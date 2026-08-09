# Blockchain Signer

Isolated Rust signers executing DKLs23 signature generation using the `silent-shard-dkls23-ll` crate built on top of the `Tonic` and `Tokio` frameworks.

## Execution
It is recommended to run the signers via the root `docker-compose.yml` to ensure they are properly networked with the Orchestrator and the Faultlab proxy.

## Inspect `redb` database
```sh
cargo run --bin inspect_redb -- node3.redb
```