FROM rust:1.94-slim-bookworm AS builder

RUN apt-get update \
    && apt-get install -y --no-install-recommends protobuf-compiler ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /usr/src/signer

RUN mkdir -p ./proto
COPY --from=shared_proto . ./proto/

COPY Cargo.toml Cargo.lock ./
COPY src ./src
COPY build.rs ./

RUN cargo build --release

FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends tini ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Create data directory for database persistence
RUN mkdir -p /app/data /app/certs

COPY --from=builder /usr/src/signer/target/release/blockchain-signer ./blockchain-signer

EXPOSE 50051

ENTRYPOINT ["/usr/bin/tini", "--", "./blockchain-signer"]