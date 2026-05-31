import time

import pytest

from faultlab.client.orchestrator import OrchestratorClient

RESULTS_SUBDIR = "HTTP_CONCURRENCY_LIMIT"
NUM_TRANSACTIONS = 1000


@pytest.mark.collect_db_state(results_subdir=RESULTS_SUBDIR, disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.parametrize("concurrency", [10, 30, 50, 70, 100, 200, 1000])
async def test_http_concurrency(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
    concurrency: int,
):
    start = time.time()
    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
        max_concurrency=concurrency,
    )
    elapsed = time.time() - start
    tps = successful / elapsed
    print(f"Concurrency={concurrency}  |  TPS: {tps:.2f}  |  Time: {elapsed:.2f}s")


class TestGrpcConcurrencyLimit:
    """Measures throughput under different GRPC_CONCURRENCY_LIMIT values on the orchestrator."""

    RESULTS_SUBDIR = "GRPC_CONCURRENCY_LIMIT/10"

    @pytest.mark.collect_db_state(results_subdir=RESULTS_SUBDIR, disable_ui=True)
    @pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", grpc_concurrency_limit=10)
    async def test_grpc_concurrency_limit_10(
        self,
        orchestrator_client: OrchestratorClient,
        mpc_accounts: list[tuple[str, str]],
    ) -> None:
        successful = await orchestrator_client.submit_transactions_batch(
            accounts=mpc_accounts,
            count=NUM_TRANSACTIONS,
            amount_range=(0.00001, 100),
        )
        assert successful >= NUM_TRANSACTIONS, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"

    @pytest.mark.collect_db_state(results_subdir=RESULTS_SUBDIR, disable_ui=True)
    @pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", grpc_concurrency_limit=30)
    async def test_grpc_concurrency_limit_30(
        self,
        orchestrator_client: OrchestratorClient,
        mpc_accounts: list[tuple[str, str]],
    ) -> None:
        successful = await orchestrator_client.submit_transactions_batch(
            accounts=mpc_accounts,
            count=NUM_TRANSACTIONS,
            amount_range=(0.00001, 100),
        )
        assert successful >= NUM_TRANSACTIONS, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"

    @pytest.mark.collect_db_state(results_subdir=RESULTS_SUBDIR, disable_ui=True)
    @pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", grpc_concurrency_limit=50)
    async def test_grpc_concurrency_limit_50(
        self,
        orchestrator_client: OrchestratorClient,
        mpc_accounts: list[tuple[str, str]],
    ) -> None:
        successful = await orchestrator_client.submit_transactions_batch(
            accounts=mpc_accounts,
            count=NUM_TRANSACTIONS,
            amount_range=(0.00001, 100),
        )
        assert successful >= NUM_TRANSACTIONS, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"

    @pytest.mark.collect_db_state(results_subdir=RESULTS_SUBDIR, disable_ui=True)
    @pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", grpc_concurrency_limit=100)
    async def test_grpc_concurrency_limit_100(
        self,
        orchestrator_client: OrchestratorClient,
        mpc_accounts: list[tuple[str, str]],
    ) -> None:
        successful = await orchestrator_client.submit_transactions_batch(
            accounts=mpc_accounts,
            count=NUM_TRANSACTIONS,
            amount_range=(0.00001, 100),
        )
        assert successful >= NUM_TRANSACTIONS, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"

    @pytest.mark.collect_db_state(results_subdir=RESULTS_SUBDIR, disable_ui=True)
    @pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", grpc_concurrency_limit=200)
    async def test_grpc_concurrency_limit_200(
        self,
        orchestrator_client: OrchestratorClient,
        mpc_accounts: list[tuple[str, str]],
    ) -> None:
        successful = await orchestrator_client.submit_transactions_batch(
            accounts=mpc_accounts,
            count=NUM_TRANSACTIONS,
            amount_range=(0.00001, 100),
        )
        assert successful >= NUM_TRANSACTIONS, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"

    @pytest.mark.collect_db_state(results_subdir=RESULTS_SUBDIR, disable_ui=True)
    @pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", grpc_concurrency_limit=1000)
    async def test_grpc_concurrency_limit_1000(
        self,
        orchestrator_client: OrchestratorClient,
        mpc_accounts: list[tuple[str, str]],
    ) -> None:
        successful = await orchestrator_client.submit_transactions_batch(
            accounts=mpc_accounts,
            count=NUM_TRANSACTIONS,
            amount_range=(0.00001, 100),
        )
        assert successful >= NUM_TRANSACTIONS, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"
