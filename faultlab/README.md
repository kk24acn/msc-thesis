# FaultLab - Python Fault Injection Framework

## Creating micromamba environment
```sh
micromamba create -n faultlab_env python=3.14 uv -c conda-forge
```

## Install Dependencies
```sh
LOG_REM=$RUST_LOG
RUST_LOG=warn
micromamba run -n faultlab_env uv pip install -r pyproject.toml
RUST_LOG=$LOG_REM
```

## Compiling Protobuf
Regenerate gRPC code after modifying `.proto` files:

### Test Suite
```bash
micromamba run -n faultlab_env python -m grpc_tools.protoc \
    -I./src \
    --python_out=./src/ \
    --grpc_python_out=./src/ \
    ./src/faultlab/proto/dkg.proto
```
### Proxy
```bash
micromamba run -n faultlab_env python -m grpc_tools.protoc \
    -I./proxy \
    --python_out=./proxy/ \
    --grpc_python_out=./proxy/ \
    ./proxy/proto/dsg.proto
```