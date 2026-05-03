import pytest

from faultlab.client.orchestrator import OrchestratorClient

NUM_TRANSACTIONS = 10


@pytest.mark.blockchain_refresh
@pytest.mark.inject_fault(fault_type="DROP_REQ", target_method="AdvanceDsg", failure_rate=100)
async def test_drop_req_100pct_failure_rate(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    successful = await orchestrator_client.submit_transactions_batch(mpc_accounts, NUM_TRANSACTIONS)

    assert successful == 0, (
        f"Expected 0 successful transactions with DROP_REQ at 100% failure rate, "
        f"got {successful}/{NUM_TRANSACTIONS}"
    )


@pytest.mark.blockchain_refresh
@pytest.mark.inject_fault(fault_type="DROP_REQ", target_method="AdvanceDsg", failure_rate=50)
async def test_drop_req_50pct_failure_rate(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    successful = await orchestrator_client.submit_transactions_batch(mpc_accounts, NUM_TRANSACTIONS)

    assert successful == NUM_TRANSACTIONS / 2, (
        f"Expected {NUM_TRANSACTIONS/2} successful transactions with DROP_REQ at 50% failure rate, "
        f"got {successful}/{NUM_TRANSACTIONS}"
    )
