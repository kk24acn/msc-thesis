import logging
from typing import Union
from threading import Thread

from flask import Flask, jsonify, request
from flask.wrappers import Response

from fault_state import FaultConfig, FaultState, FaultType


logger = logging.getLogger(__name__)


def _parse_fault_config(payload: dict) -> FaultConfig:
    failure_rate = payload.get("failure_rate", 100)
    if not isinstance(failure_rate, int) or not (1 <= failure_rate <= 100):
        raise ValueError("failure_rate must be an integer between 1 and 100")

    raw_fault_type = payload.get("fault_type")
    try:
        fault_type = FaultType(raw_fault_type) if raw_fault_type else None
    except ValueError:
        raise ValueError(f"unknown fault_type '{raw_fault_type}', must be one of {[e.value for e in FaultType]}")

    return FaultConfig(
        enabled=payload.get("enabled", False),
        fault_type=fault_type,
        target_service=payload.get("target_service", ""),
        target_method=payload.get("target_method", ""),
        failure_rate=failure_rate,
        metadata=payload.get("metadata", {}),
    )


class ControlPlane:
    def __init__(self, fault_state: FaultState, port: int = 5000) -> None:
        self.fault_state: FaultState = fault_state
        self.port = port
        self.app = Flask(__name__)
        self._setup_routes()
        self._thread: Thread | None = None

    def _setup_routes(self) -> None:
        @self.app.route("/inject", methods=["POST"])
        def inject_fault() -> Union[Response, tuple[Response, int]]:
            try:
                payload = request.get_json() or {}
                fault_config = _parse_fault_config(payload)
                self.fault_state.update(fault_config)
                logger.info(
                    f"Fault configuration updated: {fault_config.fault_type} "
                    f"for {fault_config.target_service}/{fault_config.target_method}"
                )
                return jsonify({"status": "ok", "fault": fault_config.__dict__})
            except Exception as e:
                logger.error(f"Error processing injection request: {e}")
                return jsonify({"status": "error", "message": str(e)}), 400

        @self.app.route("/status", methods=["GET"])
        def get_status() -> Response:
            current_fault = self.fault_state.get()
            active = current_fault.__dict__ if current_fault else {}
            logger.info(f"Current fault: {current_fault}")
            return jsonify({"status": "ok", "active_fault": active})

        @self.app.route("/reset", methods=["POST"])
        def reset_faults() -> Response:
            self.fault_state.disable_all()
            logger.info("All faults disabled")
            return jsonify({"status": "ok", "message": "All faults reset"})

    def start(self) -> None:
        def run_flask() -> None:
            self.app.run(host="0.0.0.0", port=self.port, debug=False)

        self._thread = Thread(target=run_flask, daemon=True)
        self._thread.start()
        logger.info(f"Control plane started on port {self.port}")

    def stop(self) -> None:
        if self._thread and self._thread.is_alive():
            logger.info("Stopping control plane")
