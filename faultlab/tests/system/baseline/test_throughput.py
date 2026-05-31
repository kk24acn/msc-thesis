import pytest

from faultlab.client.orchestrator import OrchestratorClient

RESULTS_SUBDIR = "BASELINE"
NUM_TRANSACTIONS = 1000


@pytest.mark.collect_db_state(results_subdir=RESULTS_SUBDIR, disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=2, funding_amount_eth="100.0")
async def test_baseline_1_tx(orchestrator_client: OrchestratorClient, mpc_accounts: list[tuple[str, str]]):
    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=1,
        amount_range=(0.00001, 100),
    )
    assert successful == 1


@pytest.mark.collect_db_state(results_subdir=RESULTS_SUBDIR, disable_ui=True)
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


@pytest.mark.collect_db_state(results_subdir=RESULTS_SUBDIR, disable_ui=True)
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


@pytest.mark.collect_db_state(results_subdir=RESULTS_SUBDIR, disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
async def test_baseline_10000_txs_10_accounts(
    orchestrator_client: OrchestratorClient, mpc_accounts: list[tuple[str, str]]
):
    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS * 10,
        amount_range=(0.00001, 1),
    )
    assert successful >= NUM_TRANSACTIONS * 10 * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.collect_db_state(results_subdir=RESULTS_SUBDIR, disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=100, funding_amount_eth="1000.0")
async def test_baseline_10000_txs_100_accounts(
    orchestrator_client: OrchestratorClient, mpc_accounts: list[tuple[str, str]]
):
    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS * 10,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 10 * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"
