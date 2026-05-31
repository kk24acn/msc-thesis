import pytest

from faultlab.client.orchestrator import OrchestratorClient

NUM_TRANSACTIONS = 10

# == CRASH_RES =====================================================================


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="CRASH_RES", failure_rate=101)
async def test_crash_res_invalid_failure_rate(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """Error before test execution. Fault misconfiguration (failure rate must be between 1 and 100)"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="CRASH_RES", failure_rate=100)
async def test_crash_res_100pct_failure_rate(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions fail. Response never reaches the orchestrator (gRPC UNAVAILABLE abort)"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="CRASH_RES", failure_rate=50)
async def test_crash_res_50pct_failure_rate(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """50% transactions fail. 50% transactions pass"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="CRASH_RES", failure_rate=100, signers=[2])
async def test_crash_res_100pct_failure_rate_1_target(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions succeed. The quorum is always [1,3]"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="CRASH_RES", failure_rate=100, signers=[1, 2])
async def test_crash_res_100pct_failure_rate_2_targets(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions fail. Impossible to build healthy quorum with 2-of-3 threshold when 2 nodes are disrupted"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="CRASH_RES", failure_rate=100, rounds=[1])
async def test_crash_res_100pct_failure_rate_round_1_only(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions fail. The first communication round is disrupted (ExecuteDsgPhase/Init)"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="CRASH_RES", failure_rate=100, rounds=[2, 3, 4])
async def test_crash_res_100pct_failure_rate_advance_rounds_only(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions fail. The advance communication rounds are disrupted (ExecuteDsgPhase/Advance)"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="CRASH_RES", failure_rate=100, inject_until_retry=2)
async def test_crash_res_100pct_failure_rate_until_retry_2(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions succeed. Fault active on retry_count 0–2; retry #3 finishes successfully"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="CRASH_RES", failure_rate=100, inject_until_retry=3)
async def test_crash_res_100pct_failure_rate_until_retry_3(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions fail. Fault active on retry_count 0–3; retry #4 is never performed because the orchestrator is configured for a maximum of 3 retries"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


# == SILENT_DROP_RES ==================================================================


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="SILENT_DROP_RES", failure_rate=100)
async def test_silent_drop_res_100pct_failure_rate(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions fail. The response is silently dropped after processing by the signers; orchestrator times out waiting"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="SILENT_DROP_RES", failure_rate=100, rounds=[4])
async def test_silent_drop_res_100pct_failure_rate_round_4_only(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions fail. The fourth communication round (ExecuteDsgPhase/Advance) processed by the signers but silently dropped before reaching orchestrator"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


# == DELAY =====================================================================


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="DELAY", failure_rate=100, metadata={"delay_ms": 5000})
async def test_delay_5s_100pct_failure_rate(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions succeed. 5-second latency per round injected on all proxies, resulting in 5 x 4 = 20s delay for each transaction"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="DELAY", failure_rate=100, metadata={"delay_ms": 5000}, signers=[2])
async def test_delay_5s_100pct_failure_rate_1_target(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions succeed. 5-second latency per round injected on one proxy, resulting in 5 x 4 = 20s delay for 66% (2/3) of transactions"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="DELAY", failure_rate=100, metadata={"delay_ms": 10000}, signers=[1, 2])
async def test_delay_10s_100pct_failure_rate_2_targets(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions fail. 10-second latency per round injected on two proxies, the orchestrator terminates the connection because it's configured for a 10s timeout"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(
    fault_type="DELAY",
    failure_rate=100,
    metadata={"delay_ms": 10000},
    signers=[1, 2],
    inject_until_retry=2,
)
async def test_delay_10s_100pct_failure_rate_2_targets_until_retry_2(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """
    All transactions succeed. 10-second latency per round injected on two proxies, the orchestrator terminates the connection because it's configured for a 10s timeout
    Fault active on retry_count 0–2; retry #3 finishes successfully
    """

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


# == MUTATE =====================================================================


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="MUTATE", failure_rate=100)
async def test_mutate_100pct_failure_rate(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions fail. Signers receive mutated payload and DKLs23 math breaks, causing cryptographic abort on round #3"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="MUTATE", failure_rate=100, signers=[2])
async def test_mutate_100pct_failure_rate_1_target(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions succeed. One signer acts maliciously, successful quorum is always [1,3]"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="MUTATE", failure_rate=100, rounds=[1])
async def test_mutate_100pct_failure_rate_round_1_only(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions fail. Message hash is mutated on the first round, so signers successfully process invalid message but orchestrator rejects signature during verification"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


# == REPLAY =====================================================================


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="REPLAY", failure_rate=100)
async def test_replay_100pct_failure_rate(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """9/10 transactions fail. A cached signature share from the final signing round is returned for all transactions except the first. The first processed transaction succeeds and primes the cache"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


# == COMPOSITION =====================================================================


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="CRASH_RES", failure_rate=25, signers=[2])
@pytest.mark.inject_fault(fault_type="REPLAY", failure_rate=50, signers=[3])
async def test_crash_res_25pct_node_2_and_replay_50pct_node_3(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """Chaos. Some transactions fail, some pass, some stack in mempool (without nonce gap recovery)"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS * 10,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 10 * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="CRASH_RES", failure_rate=10, signers=[2, 3])
@pytest.mark.collect_db_state(disable_ui=True)
async def test_crash_res_10pct_nodes_2_and_3(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """Every 10th transaction transaction fails and must be replaced to zero-value transaction with the NonceGapSweeper"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS * 10,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 10 * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"

