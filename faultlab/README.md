# FaultLab - Python Fault Injection Framework

Python test suite and gRPC proxy layer for injecting crash and Byzantine faults.

## Proxy Execution
It is recommended to run the proxies via the root `docker-compose.yml` to ensure they are properly networked with the Orchestrator and the Signer nodes.

## Test Suite Execution
```sh
# Install dependencies
uv pip install -r pyproject.toml -e . --group dev
```

Test cases can be triggered from the VSCode UI interface.

---

## Compiling Protobuf
Regenerate gRPC code after modifying `.proto` files:

### Proxy
```bash
python -m grpc_tools.protoc \
    -I./proxy \
    --python_out=./proxy/ \
    --grpc_python_out=./proxy/ \
    --mypy_out=./proxy/ \
    --mypy_grpc_out=./proxy/ \
    ./proxy/proto/dsg.proto
```

### Test Suite
```bash
python -m grpc_tools.protoc \
    -I./src \
    --python_out=./src/ \
    --grpc_python_out=./src/ \
    --mypy_out=./src/ \
    --mypy_grpc_out=./src/ \
    ./src/faultlab/proto/dkg.proto
```