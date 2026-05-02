FROM python:3.14-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir grpcio grpcio-tools flask

COPY proxy/ /app/proxy/

ARG SIGNER_HOST
ARG SIGNER_PORT

EXPOSE 50051 5000

# Disable log buffering
ENV PYTHONUNBUFFERED=1 
ENV SIGNER_HOST=${SIGNER_HOST}
ENV SIGNER_PORT=${SIGNER_PORT}
ENV PROXY_PORT=50051
ENV PYTHONPATH=/app/proxy

CMD ["python", "proxy/main.py"]
