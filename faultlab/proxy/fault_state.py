from __future__ import annotations

import threading
from dataclasses import dataclass, field
from typing import Any


@dataclass
class FaultConfig:
    enabled: bool = False
    fault_type: str = ""
    target_service: str = ""
    target_method: str = ""
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
                metadata=dict(self._current_fault.metadata),
            )

    def disable_all(self) -> None:
        with self._lock:
            self._current_fault = FaultConfig()
