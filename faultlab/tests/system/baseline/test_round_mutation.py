import pytest
from faultlab.client.orchestrator import OrchestratorClient
from faultlab.client.proxy import ProxyControlPlaneClient


@pytest.mark.parametrize("round_idx", [1, 2, 3, 4])
@pytest.mark.blockchain_refresh(num_accounts=2, funding_amount_eth="10.0", quarantine_mode="CIRCUIT_BREAKER")
async def test_mutate_per_round_temp(
    orchestrator_client: OrchestratorClient,
    proxy_client: ProxyControlPlaneClient,
    mpc_accounts: list[tuple[str, str]],
    round_idx: int,
) -> None:
    """Observe system behavior when a payload is mutated during a specific round."""
    await proxy_client.inject_all(fault_type="MUTATE", failure_rate=100, signers=[2], rounds=[round_idx])

    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=1,
        amount_range=(0.00001, 1),
        max_concurrency=100,
    )
