import asyncio
import logging
import os
from concurrent import futures

import grpc
from proto import dsg_pb2_grpc

from control_plane import ControlPlane
from fault_state import FaultState
from proxy_servicer import DsgServiceProxy

logger = logging.getLogger(__name__)

SIGNER_HOST = os.environ["SIGNER_HOST"]
SIGNER_PORT = int(os.environ["SIGNER_PORT"])
PROXY_LISTEN_PORT = int(os.environ.get("PROXY_PORT", 50051))


async def _main() -> None:
    fault_state = FaultState()
    control_plane = ControlPlane(fault_state, port=5000)
    control_plane.start()

    channel = grpc.aio.insecure_channel(f"{SIGNER_HOST}:{SIGNER_PORT}")
    dsg_stub = dsg_pb2_grpc.DsgServiceStub(channel)

    server = grpc.aio.server(futures.ThreadPoolExecutor(max_workers=10))
    dsg_pb2_grpc.add_DsgServiceServicer_to_server(DsgServiceProxy(dsg_stub, fault_state), server)
    server.add_insecure_port(f"0.0.0.0:{PROXY_LISTEN_PORT}")

    await server.start()
    logger.info(f"gRPC proxy *:{PROXY_LISTEN_PORT} to {SIGNER_HOST}:{SIGNER_PORT}")
    await server.wait_for_termination()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
    logging.getLogger("grpc").setLevel(logging.WARNING)
    logging.getLogger("werkzeug").setLevel(logging.WARNING)
    asyncio.run(_main())
