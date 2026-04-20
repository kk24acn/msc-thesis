#!/bin/sh
set -e

export PYTHONPATH=/app/proxy:$PYTHONPATH

exec mitmdump \
    --mode "reverse:tcp://${SIGNER_HOST}:${SIGNER_PORT}" \
    --listen-host 0.0.0.0 \
    --listen-port "${PROXY_PORT}" \
    -s proxy/__init__.py
