FROM python:3.14-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir mitmproxy flask

COPY proxy-entrypoint.sh /app/proxy-entrypoint.sh
RUN chmod +x /app/proxy-entrypoint.sh

COPY proxy/ /app/proxy/

ARG SIGNER_HOST
ARG SIGNER_PORT

EXPOSE 50051 5000

ENV PYTHONUNBUFFERED=1
ENV MITMPROXY_CONFDIR=/app/.mitmproxy
ENV SIGNER_HOST=${SIGNER_HOST}
ENV SIGNER_PORT=${SIGNER_PORT}
ENV PROXY_PORT=50051

RUN mkdir -p $MITMPROXY_CONFDIR

ENTRYPOINT ["/app/proxy-entrypoint.sh"]
