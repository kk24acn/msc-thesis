FROM rust:1.94-slim-bullseye AS builder

RUN apt-get update \
    && apt-get install -y --no-install-recommends protobuf-compiler ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /usr/src/signer

COPY --from=shared_proto signer.proto ./proto/signer.proto
COPY Cargo.toml Cargo.lock ./
COPY src ./src
COPY build.rs ./

RUN cargo build --release

FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends tini ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /usr/src/signer/target/release/blockchain-signer ./blockchain-signer

EXPOSE 50051

ENTRYPOINT ["./blockchain-signer", "--cert", "./certs/signer.crt", "--key", "./certs/signer.key", "--ca", "./certs/ca.crt"]