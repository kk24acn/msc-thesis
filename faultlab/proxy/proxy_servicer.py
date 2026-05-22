import asyncio
import logging
from typing import Callable

import grpc
from grpc.aio import ServicerContext

from proto import dsg_pb2
from proto import dsg_pb2_grpc
from fault_state import DSG_TOTAL_ROUNDS, FaultConfig, FaultState, FaultType

logger = logging.getLogger(__name__)


async def _handle_pre_request_faults(
    fault: FaultConfig | None,
    context: ServicerContext,
    log_ctx: str,
) -> None:
    if fault is None:
        return

    match fault.fault_type:
        case FaultType.DROP_REQ:
            logger.info(f"[DROP_REQ] ExecuteDsgPhase | {log_ctx}")
            await context.abort(grpc.StatusCode.UNAVAILABLE, "fault injection: request dropped")
        case FaultType.DELAY:
            delay_ms = fault.metadata.get("delay_ms", 0)
            logger.info(f"[DELAY] ExecuteDsgPhase | delay={delay_ms}ms, {log_ctx}")
            await asyncio.sleep(delay_ms / 1000)


async def _handle_request_faults(
    request: dsg_pb2.DsgPhaseRequest,
    cache: dsg_pb2.DsgPhaseResponse | None,
    fault: FaultConfig | None,
    stub_function: Callable,
    cache_function: Callable | None,
    round_num: int,
    log_ctx: str,
) -> dsg_pb2.DsgPhaseResponse:
    if fault is None:
        return await stub_function(request)

    match fault.fault_type:
        case FaultType.MUTATE:
            # byte_offset is measured from the END of the target bytes so the flip
            # lands in fixed-size cryptographic fields rather than length prefixes.
            offset = fault.metadata.get("byte_offset", 10)
            mask = fault.metadata.get("xor_mask", 0xFF)

            def flip(data: bytes) -> bytes:
                raw = bytearray(data)
                idx = len(raw) - 1 - offset
                if idx >= 0:
                    raw[idx] ^= mask
                return bytes(raw)

            match request.WhichOneof("payload"):
                case "init":
                    # Corrupt the message hash — signers sign a different message with
                    # no cryptographic abort; the combined signature is invalid on-chain.
                    logger.info(f"[MUTATE] ExecuteDsgPhase | target=message_hash, {log_ctx}")
                    return await stub_function(
                        dsg_pb2.DsgPhaseRequest(
                            dsg_session_id=request.dsg_session_id,
                            party_id=request.party_id,
                            init=dsg_pb2.InitPayload(
                                key_id=request.init.key_id,
                                message_hash=flip(request.init.message_hash),
                                derivation_path=request.init.derivation_path,
                            ),
                        )
                    )
                case "peer_payloads":
                    # Corrupt round messages — triggers cryptographic abort on the signer.
                    mutated = [flip(p) for p in request.peer_payloads.payloads]
                    logger.info(f"[MUTATE] ExecuteDsgPhase | target=peer_payloads, payloads={len(mutated)}, {log_ctx}")
                    return await stub_function(
                        dsg_pb2.DsgPhaseRequest(
                            dsg_session_id=request.dsg_session_id,
                            party_id=request.party_id,
                            peer_payloads=dsg_pb2.PeerPayloads(payloads=mutated),
                        )
                    )
                case _:
                    return await stub_function(request)
        case FaultType.REPLAY:
            if round_num != DSG_TOTAL_ROUNDS:
                logger.info(f"[OK] ExecuteDsgPhase | {log_ctx}")
                return await stub_function(request)

            if cache is not None:
                logger.info(f"[REPLAY] ExecuteDsgPhase | {log_ctx}")
                return cache

            logger.info(f"[REPLAY] Cache empty, bypassing fault and priming cache | {log_ctx}") # fmt: skip
            response = await stub_function(request)
            if cache_function:
                cache_function(response)
            return response
        case _:
            return await stub_function(request)


async def _handle_post_request_faults(
    fault: FaultConfig | None,
    context: ServicerContext,
    stub_function: Callable,
    request: dsg_pb2.DsgPhaseRequest,
    log_ctx: str,
) -> None:
    if fault is None:
        return

    match fault.fault_type:
        case FaultType.DROP_RES:
            logger.info(f"[DROP_RES] ExecuteDsgPhase | {log_ctx}")
            await context.abort(grpc.StatusCode.UNAVAILABLE, "fault injection: response dropped")


class DsgServiceProxy(dsg_pb2_grpc.DsgServiceServicer):
    def __init__(self, stub: dsg_pb2_grpc.DsgServiceAsyncStub, fault_state: FaultState) -> None:
        self._stub = stub
        self._fault_state = fault_state

    def _trace_id(self, context: ServicerContext) -> str:
        metadata = context.invocation_metadata()
        if not metadata:
            return ""

        for key, value in metadata:
            if key == "x-trace-id":
                if isinstance(value, bytes):
                    return value.decode("utf-8")
                return str(value)
        return ""

    def _retry_count(self, context: ServicerContext) -> int:
        metadata = context.invocation_metadata()
        if metadata:
            for key, value in metadata:
                if key == "x-retry-count":
                    try:
                        return int(value)
                    except (ValueError, TypeError):
                        return 0
        return -1

    async def ExecuteDsgPhase(
        self,
        request: dsg_pb2.DsgPhaseRequest,
        context: ServicerContext,
    ) -> dsg_pb2.DsgPhaseResponse:
        trace_id = self._trace_id(context)
        retry_count = self._retry_count(context)
        fault, round_num = self._fault_state.resolve_fault(
            trace_id, retry_count, is_init=request.WhichOneof("payload") == "init"
        )
        log_ctx = f"trace_id={trace_id}, round={round_num}, retry={retry_count}"
        if not fault:
            logger.info(f"[OK] ExecuteDsgPhase | {log_ctx}")

        await _handle_pre_request_faults(fault, context, log_ctx)
        try:
            response = await _handle_request_faults(
                request,
                self._fault_state.get_cached_response(),
                fault,
                self._stub.ExecuteDsgPhase,
                self._fault_state.cache_response,
                round_num,
                log_ctx,
            )
        except grpc.aio.AioRpcError as e:
            logger.info(f"[FAULT_ERR] ExecuteDsgPhase | downstream={e.code().name}: {e.details()}, {log_ctx}")
            await context.abort(e.code(), e.details() or "")
        await _handle_post_request_faults(fault, context, self._stub.ExecuteDsgPhase, request, log_ctx) # fmt: skip
        return response
