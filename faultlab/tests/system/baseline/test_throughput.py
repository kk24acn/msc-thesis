import pytest

from faultlab.client.orchestrator import OrchestratorClient

NUM_TRANSACTIONS = 1000


@pytest.mark.asyncio
@pytest.mark.blockchain_refresh
async def test_baseline_1_tx(orchestrator_client: OrchestratorClient, mpc_accounts: list[tuple[str, str]]):
    successful = await orchestrator_client.submit_transactions_batch(mpc_accounts, 1)
    assert successful == 1


@pytest.mark.asyncio
@pytest.mark.blockchain_refresh
@pytest.mark.collect_metrics
async def test_baseline_1000_txs(orchestrator_client: OrchestratorClient, mpc_accounts: list[tuple[str, str]]):
    successful = await orchestrator_client.submit_transactions_batch(mpc_accounts, NUM_TRANSACTIONS)
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"
