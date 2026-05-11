import time

import pytest

from faultlab.client.orchestrator import OrchestratorClient

NUM_TRANSACTIONS = 1000


@pytest.mark.baseline
@pytest.mark.blockchain_refresh(num_accounts=2, funding_amount_eth="100.0")
async def test_baseline_1_tx(orchestrator_client: OrchestratorClient, mpc_accounts: list[tuple[str, str]]):
    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=1,
        amount_range=(0.00001, 100),
    )
    assert successful == 1


@pytest.mark.baseline
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.parametrize("concurrency", [10, 30, 50, 70, 100, 200])
async def test_baseline_determine_concurrency(
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


@pytest.mark.baseline
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
async def test_baseline_1000_txs_10_accounts(
    orchestrator_client: OrchestratorClient, mpc_accounts: list[tuple[str, str]]
):
    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.baseline
@pytest.mark.blockchain_refresh(num_accounts=100, funding_amount_eth="1000.0")
async def test_baseline_1000_txs_100_accounts(
    orchestrator_client: OrchestratorClient, mpc_accounts: list[tuple[str, str]]
):
    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.baseline
@pytest.mark.blockchain_refresh(num_accounts=1000, funding_amount_eth="100.0")
async def test_baseline_1000_txs_1000_accounts(
    orchestrator_client: OrchestratorClient, mpc_accounts: list[tuple[str, str]]
):
    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 10),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"
