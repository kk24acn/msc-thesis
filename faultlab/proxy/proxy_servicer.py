import asyncio
import logging
from typing import Awaitable, Callable

import grpc
from grpc.aio import ServicerContext

from proto import dsg_pb2
from proto import dsg_pb2_grpc
from fault_state import DSG_TOTAL_ROUNDS, FaultConfig, FaultState, FaultType

logger = logging.getLogger(__name__)


async def _invoke(
    coroutine: Awaitable[dsg_pb2.DsgPhaseResponse],
    context: ServicerContext,
    log_ctx: str,
    on_error: Callable[[], None] | None = None,
) -> dsg_pb2.DsgPhaseResponse:
    try:
        return await coroutine
    except grpc.aio.AioRpcError as e:
        logger.info(f"[FAULT_ERR] ExecuteDsgPhase | downstream={e.code().name}: {e.details()}, {log_ctx}")
        if on_error:
            on_error()
        await context.abort(e.code(), e.details() or "")


async def _handle_pre_request_faults(
    fault: FaultConfig | None,
    context: ServicerContext,
    log_ctx: str,
) -> None:
    if fault is None:
        return

    match fault.fault_type:
        case FaultType.DELAY:
            delay_ms = fault.metadata.get("delay_ms", 0)
            logger.info(f"[DELAY] ExecuteDsgPhase | delay={delay_ms}ms, {log_ctx}")
            await asyncio.sleep(delay_ms / 1000)


async def _handle_request_faults(
    request: dsg_pb2.DsgPhaseRequest,
    fault: FaultConfig | None,
    stub_function: Callable,
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
        case _:
            return await stub_function(request)


async def _handle_post_request_faults(
    fault: FaultConfig | None,
    context: ServicerContext,
    log_ctx: str,
) -> None:
    if fault is None:
        return

    match fault.fault_type:
        case FaultType.CRASH_RES:
            logger.info(f"[CRASH_RES] ExecuteDsgPhase | {log_ctx}")
            await context.abort(grpc.StatusCode.UNAVAILABLE, "fault injection: response crashed")
        case FaultType.SILENT_DROP_RES:
            logger.info(f"[SILENT_DROP_RES] ExecuteDsgPhase | {log_ctx}")
            await asyncio.Event().wait()


class DsgServiceProxy(dsg_pb2_grpc.DsgServiceServicer):
    def __init__(self, stub: dsg_pb2_grpc.DsgServiceAsyncStub, fault_state: FaultState) -> None:
        self._stub = stub
        self._fault_state = fault_state
        self._replay_priming_claimed: bool = False
        self._replay_cache_event: asyncio.Event = asyncio.Event()

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

    async def _handle_replay_fault(
        self,
        request: dsg_pb2.DsgPhaseRequest,
        context: ServicerContext,
        round_num: int,
        log_ctx: str,
    ) -> dsg_pb2.DsgPhaseResponse:
        if round_num != DSG_TOTAL_ROUNDS:
            logger.info(f"[OK] ExecuteDsgPhase | {log_ctx}")
            return await _invoke(self._stub.ExecuteDsgPhase(request), context, log_ctx)

        live_cache = self._fault_state.get_cached_response()
        if live_cache is not None:
            logger.info(f"[REPLAY] ExecuteDsgPhase | {log_ctx}")
            return live_cache

        if not self._replay_priming_claimed:
            self._replay_priming_claimed = True
            logger.info(f"[REPLAY] Cache empty, bypassing fault and priming cache | {log_ctx}")
            response = await _invoke(
                self._stub.ExecuteDsgPhase(request),
                context,
                log_ctx,
                on_error=self._replay_cache_event.set,
            )
            self._fault_state.cache_response(response)
            self._replay_cache_event.set()
            return response
        else:
            logger.info(f"[REPLAY] Waiting for cache primer | {log_ctx}")
            await self._replay_cache_event.wait()
            cached = self._fault_state.get_cached_response()
            if cached is not None:
                logger.info(f"[REPLAY] ExecuteDsgPhase (post-wait) | {log_ctx}")
                return cached
            await context.abort(grpc.StatusCode.UNAVAILABLE, "fault injection: replay cache unavailable after primer failure") # fmt: skip

    async def ExecuteDsgPhase(
        self,
        request: dsg_pb2.DsgPhaseRequest,
        context: ServicerContext,
    ) -> dsg_pb2.DsgPhaseResponse:
        trace_id = self._trace_id(context)
        retry_count = self._retry_count(context)
        fault, round_num = self._fault_state.resolve_fault(
            trace_id,
            retry_count,
            is_init=request.WhichOneof("payload") == "init",
        )
        log_ctx = f"trace_id={trace_id}, round={round_num}, retry={retry_count}"
        if not fault:
            logger.info(f"[OK] ExecuteDsgPhase | {log_ctx}")

        if fault and fault.fault_type == FaultType.REPLAY:
            return await self._handle_replay_fault(request, context, round_num, log_ctx)

        await _handle_pre_request_faults(fault, context, log_ctx)
        response = await _invoke(
            _handle_request_faults(request, fault, self._stub.ExecuteDsgPhase, log_ctx),
            context,
            log_ctx,
        )
        await _handle_post_request_faults(fault, context, log_ctx)
        return response
