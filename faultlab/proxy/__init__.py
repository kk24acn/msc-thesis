import logging
import time

from mitmproxy.tcp import TCPFlow

from control_plane import ControlPlane
from fault_state import FaultState


logger = logging.getLogger(__name__)


class GrpcFaultInjectionProxy:
    def __init__(self) -> None:
        self.fault_state = FaultState()
        self.control_plane = ControlPlane(self.fault_state, port=5000)
        self.control_plane.start()
        self.request_count = 0
        self.monitored_services = {"mpc.signer.DsgService"}
        self.connections = {}

    def tcp_start(self, data: TCPFlow) -> None:
        """Called when a TCP connection is initiated."""
        client_addr = f"{data.client_conn.address[0]}:{data.client_conn.address[1]}"
        self.connections[id(data)] = {
            "client": client_addr,
            "start_time": time.time(),
        }
        logger.info(f"[TCP_START] Client connected: {client_addr}")

    def tcp_message(self, data: TCPFlow) -> None:
        """Called when TCP data is sent/received."""
        if id(data) in self.connections:
            current_fault = self.fault_state.get()
            if current_fault.enabled:
                logger.info(f"[FAULT_ACTIVE] Data flowing with {current_fault.fault_type}")

    def tcp_close(self, data: TCPFlow) -> None:
        """Called when a TCP connection is closed."""
        if id(data) in self.connections:
            duration = time.time() - self.connections[id(data)]["start_time"]
            logger.info(f"[TCP_CLOSE] Client {self.connections[id(data)]['client']} " f"closed after {duration:.3f}s")
            del self.connections[id(data)]


addons = [GrpcFaultInjectionProxy()]
