# Master Project - Blockchain Transaction Orchestration System

This project provides an 11-component infrastructure for chaos engineering evaluation of a centralized blockchain orchestrator. The orchestrator connects to three isolated Rust signer nodes via Python gRPC proxies to execute a 2-of-3 DKLs23 threshold signature protocol. It tracks transaction lifecycle in PostgreSQL database and submits them to a local Hardhat 3 blockchain node.

An automated Python test suite configures the system and executes test batches. It evaluates system resilience and performance under stress by injecting crash faults (delay, crash, drop packet) and Byzantine faults (mutate, replay) through the proxies. A React dashboard powered by PostgREST visualizes system metrics and transaction states.

## Project Components

*   **`blockchain-orchestrator/`**: Spring Boot 4 (Java 25) central service handling DKLs23 signing session coordination and transaction submission.
*   **`blockchain-signer/`**: Isolated Rust signers executing DKLs23 signature generation using the `silent-shard-dkls23-ll` crate built on top of the `Tonic` and `Tokio` frameworks.
*   **`faultlab/`**: Python test suite and gRPC proxy layer for injecting crash and Byzantine faults.
*   **`hardhat-node/`**: Local Ethereum blockchain simulator for transaction verification.
*   **`ui-dashboard/`**: React frontend for monitoring transactions and orchestrator state.

## Running the Infrastructure with Docker Compose

Start the complete infra:
```sh
docker compose up -d --build
```

Stop the infra:
```sh
docker compose down
```

Clean restart (wipe database and volumes):

```sh
docker compose down
docker volume prune -f --all
docker compose up -d --build
```

## Third-Party Software and Licensing
This project leverages the following open-source technologies:
- Spring Boot 4 (Apache License 2.0)
- Hardhat 3 (MIT License)
- React 19 (MIT License)
- PostgreSQL 18 (PostgreSQL License)

The isolated Rust signing nodes utilize the `silent-shard-dkls23-ll` library from Silence Laboratories. This component is governed by the Silence Laboratories Non-Commercial Use License Agreement preserved in the LICENSE and NOTICE files of this repository.