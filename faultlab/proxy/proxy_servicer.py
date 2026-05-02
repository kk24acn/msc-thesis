import asyncio
import logging
from typing import cast

from grpc.aio import ServicerContext

from proto import dsg_pb2
from proto import dsg_pb2_grpc
from fault_state import FaultConfig, FaultState, FaultType

logger = logging.getLogger(__name__)


def _resolve_active_fault(fault_state: FaultState, trace_id: str, method_name: str) -> FaultConfig | None:
    def is_targeted(t_id: str | int, failure_rate: int) -> bool:
        try:
            numeric_id = int(t_id)
        except (ValueError, TypeError):
            return False
        divisor = int(100 / failure_rate)
        return divisor > 0 and numeric_id % divisor == 0

    fault = fault_state.get()
    if fault is None or fault.target_method != method_name or not is_targeted(trace_id, fault.failure_rate):
        return None
    return fault


async def _handle_pre_request_faults(fault: FaultConfig | None, method: str, trace_id: str) -> None:
    if fault is None:
        return

    match fault.fault_type:
        case FaultType.DROP_REQ:
            logger.info(f"[DROP_REQ] {method} trace={trace_id}")
            await asyncio.sleep(86400)
        case FaultType.DELAY:
            delay_ms = fault.metadata.get("delay_ms", 0)
            logger.info(f"[DELAY] {delay_ms}ms {method} trace={trace_id}")
            await asyncio.sleep(delay_ms / 1000)


async def _handle_post_request_faults(fault: FaultConfig | None, method: str, trace_id: str) -> None:
    if fault is None:
        return

    if fault.fault_type == FaultType.DROP_RES:
        logger.info(f"[DROP_RES] {method} trace={trace_id}")
        await asyncio.sleep(86400)


def _corrupt_payload[T: dsg_pb2.InitDsgRequest | dsg_pb2.AdvanceDsgRequest](  # type: ignore
    request: T,
    fault: FaultConfig | None,
    trace_id: str,
) -> T:
    if fault is None or fault.fault_type != FaultType.MUTATE:
        return request

    raw = bytearray(request.SerializeToString())
    offset = fault.metadata.get("byte_offset", 5)
    mask = fault.metadata.get("xor_mask", 0xFF)
    if len(raw) > offset:
        raw[offset] ^= mask
        logger.info(f"[MUTATE] {type(request).__name__} trace={trace_id}")
        return cast(T, type(request).FromString(bytes(raw)))
    return request


def _replay_request(fault: FaultConfig | None, stub_method, request, trace_id: str) -> None:
    if fault is None or fault.fault_type != FaultType.REPLAY:
        return

    replay_count = fault.metadata.get("replay_count", 1)
    logger.info(f"[REPLAY] replay_count={replay_count} trace={trace_id}")
    for _ in range(replay_count):
        asyncio.create_task(stub_method(request))


class DsgServiceProxy(dsg_pb2_grpc.DsgServiceServicer):
    def __init__(self, stub: dsg_pb2_grpc.DsgServiceStub, fault_state: FaultState) -> None:
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

    async def InitDsg(
        self,
        request: dsg_pb2.InitDsgRequest,  # type: ignore
        context: ServicerContext,
    ) -> dsg_pb2.InitDsgResponse:  # type: ignore
        trace_id = self._trace_id(context)
        fault = _resolve_active_fault(self._fault_state, trace_id, "InitDsg")

        await _handle_pre_request_faults(fault, "InitDsg", trace_id)
        response = await self._stub.InitDsg(_corrupt_payload(request, fault, trace_id))
        await _handle_post_request_faults(fault, "InitDsg", trace_id)

        _replay_request(fault, self._stub.InitDsg, request, trace_id)
        return response

    async def AdvanceDsg(
        self,
        request: dsg_pb2.AdvanceDsgRequest,  # type: ignore
        context: ServicerContext,
    ) -> dsg_pb2.AdvanceDsgResponse:  # type: ignore
        trace_id = self._trace_id(context)
        fault = _resolve_active_fault(self._fault_state, trace_id, "AdvanceDsg")

        await _handle_pre_request_faults(fault, "AdvanceDsg", trace_id)
        response = await self._stub.AdvanceDsg(_corrupt_payload(request, fault, trace_id))
        await _handle_post_request_faults(fault, "AdvanceDsg", trace_id)

        _replay_request(fault, self._stub.AdvanceDsg, request, trace_id)
        return response
