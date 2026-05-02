from __future__ import annotations

import threading
from dataclasses import dataclass, field
from enum import Enum
from typing import Any


class FaultType(str, Enum):
    DROP_REQ = "DROP_REQ"
    DELAY = "DELAY"
    DROP_RES = "DROP_RES"
    MUTATE = "MUTATE"
    REPLAY = "REPLAY"


@dataclass
class FaultConfig:
    enabled: bool = False
    fault_type: FaultType | None = None
    target_service: str = ""
    target_method: str = ""
    failure_rate: int = 100
    metadata: dict[str, Any] = field(default_factory=dict)


class FaultState:
    def __init__(self) -> None:
        self._lock = threading.RLock()
        self._current_fault = FaultConfig()

    def update(self, fault_config: FaultConfig) -> None:
        with self._lock:
            self._current_fault = fault_config

    def get(self) -> FaultConfig:
        with self._lock:
            return FaultConfig(
                enabled=self._current_fault.enabled,
                fault_type=self._current_fault.fault_type,
                target_service=self._current_fault.target_service,
                target_method=self._current_fault.target_method,
                failure_rate=self._current_fault.failure_rate,
                metadata=dict(self._current_fault.metadata),
            )

    def disable_all(self) -> None:
        with self._lock:
            self._current_fault = FaultConfig()
