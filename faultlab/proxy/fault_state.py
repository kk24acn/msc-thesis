from __future__ import annotations

import logging
import threading
from dataclasses import dataclass, field
from enum import Enum
from typing import Any

from proto import dsg_pb2

DSG_TOTAL_ROUNDS = 4  # 4 ExecuteDsgPhase (1 Init + 3 Advance)
PREALLOCATED_TRACE_IDS = 1000  # Default number of trace_ids expected in the test

logger = logging.getLogger(__name__)


class FaultType(str, Enum):
    CRASH_RES = "CRASH_RES"
    SILENT_DROP_RES = "SILENT_DROP_RES"
    DELAY = "DELAY"
    MUTATE = "MUTATE"
    REPLAY = "REPLAY"


@dataclass
class FaultConfig:
    enabled: bool = False
    fault_type: FaultType | None = None
    failure_rate: int = 100
    metadata: dict[str, Any] = field(default_factory=dict)
    rounds: list[int] = field(default_factory=list)  # Round targeting: list of rounds to target (1=Init, 2-4=Advance) # fmt: skip
    inject_until_retry: int | None = None  # Inject fault until Nth retry; None = fault every signing attempt; (0=first attempt, 1=first retry, …) # fmt: skip


class FaultState:
    def __init__(self) -> None:
        self._lock = threading.RLock()
        self._current_fault = FaultConfig()
        self._round_counters: dict[str, int] = self.__prepare_round_counters()
        self._cached_response: dsg_pb2.DsgPhaseResponse | None = None

    def __prepare_round_counters(self) -> dict[str, int]:
        return {str(i): 0 for i in range(0, PREALLOCATED_TRACE_IDS + 1)}

    def update(self, fault_config: FaultConfig) -> None:
        with self._lock:
            self._current_fault = fault_config
            self._round_counters = self.__prepare_round_counters()
            self._cached_response = None

    def get(self) -> FaultConfig | None:
        with self._lock:
            if not self._current_fault.enabled:
                return None

            return FaultConfig(
                enabled=self._current_fault.enabled,
                fault_type=self._current_fault.fault_type,
                failure_rate=self._current_fault.failure_rate,
                metadata=self._current_fault.metadata,
                rounds=self._current_fault.rounds,
                inject_until_retry=self._current_fault.inject_until_retry,
            )

    def cache_response(self, response: dsg_pb2.DsgPhaseResponse) -> None:
        if self._cached_response is not None:
            return

        with self._lock:
            if self._cached_response is not None:
                return
            try:
                if response.HasField("signature_share"):
                    self._cached_response = response
            except (ValueError, AttributeError):
                logger.warning(f"Failed to cache response: {response}")

    def get_cached_response(self) -> dsg_pb2.DsgPhaseResponse | None:
        return self._cached_response

    def disable_all(self) -> None:
        with self._lock:
            self._current_fault = FaultConfig()
            self._round_counters = self.__prepare_round_counters()
            self._cached_response = None

    def resolve_fault(
        self, trace_id: str, retry_count: int = 0, is_init: bool = False
    ) -> tuple[FaultConfig | None, int]:
        def is_trace_targeted(trace_id: str | int, failure_rate: int) -> bool:
            try:
                numeric_id = int(trace_id)
            except (ValueError, TypeError):
                return False
            divisor = int(100 / failure_rate)
            return divisor > 0 and numeric_id % divisor == 0

        fault = self._current_fault

        # Init payload always starts round 1 (handles retries resetting the counter);
        # peer_payloads increments the round counter.
        if is_init:
            self._round_counters[trace_id] = 1
        else:
            self._round_counters[trace_id] = self._round_counters.get(trace_id, 0) + 1
        current_round = self._round_counters[trace_id]

        # Check if trace should be bypassed
        if not fault.enabled or not is_trace_targeted(trace_id, fault.failure_rate):
            return None, current_round

        # Check if retry should be bypassed
        if fault.inject_until_retry is not None and retry_count > fault.inject_until_retry:
            return None, current_round

        # Check if round should be bypassed
        if current_round not in fault.rounds:
            return None, current_round

        return (
            FaultConfig(
                enabled=fault.enabled,
                fault_type=fault.fault_type,
                failure_rate=fault.failure_rate,
                metadata=fault.metadata,
                rounds=fault.rounds,
                inject_until_retry=fault.inject_until_retry,
            ),
            current_round,
        )
