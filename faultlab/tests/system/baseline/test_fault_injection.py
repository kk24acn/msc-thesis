import pytest

from faultlab.client.orchestrator import OrchestratorClient

NUM_TRANSACTIONS = 10

# == DROP_REQ ==================================================================


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="DROP_REQ", failure_rate=101)
async def test_drop_req_invalid_failure_rate(
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
@pytest.mark.inject_fault(fault_type="DROP_REQ", failure_rate=100)
async def test_drop_req_100pct_failure_rate(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions fail. Request never reaches the signers"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="DROP_REQ", failure_rate=50)
async def test_drop_req_50pct_failure_rate(
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
@pytest.mark.inject_fault(fault_type="DROP_REQ", failure_rate=100, signers=[2])
async def test_drop_req_100pct_failure_rate_1_target(
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
@pytest.mark.inject_fault(fault_type="DROP_REQ", failure_rate=100, signers=[1, 2])
@pytest.mark.collect_db_state
async def test_drop_req_100pct_failure_rate_2_targets(
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
@pytest.mark.inject_fault(fault_type="DROP_REQ", failure_rate=100, rounds=[1])
@pytest.mark.collect_db_state
async def test_drop_req_100pct_failure_rate_round_1_only(
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
@pytest.mark.inject_fault(fault_type="DROP_REQ", failure_rate=100, rounds=[2, 3, 4])
@pytest.mark.collect_db_state
async def test_drop_req_100pct_failure_rate_advance_rounds_only(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions fail. The second communication round is disrupted (ExecuteDsgPhase/Advance)"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="DROP_REQ", failure_rate=100, inject_until_retry=2)
@pytest.mark.collect_db_state
async def test_drop_req_100pct_failure_rate_until_retry_2(
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
@pytest.mark.inject_fault(fault_type="DROP_REQ", failure_rate=100, inject_until_retry=3)
@pytest.mark.collect_db_state
async def test_drop_req_100pct_failure_rate_until_retry_3(
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


# == DROP_RES ==================================================================


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="DROP_RES", failure_rate=100)
@pytest.mark.collect_db_state
async def test_drop_res_100pct_failure_rate(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions fail. The first communication round (ExecuteDsgPhase/Init) processed by the signers but never reaches orchestrator back"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="DROP_RES", failure_rate=100, rounds=[4])
async def test_drop_res_100pct_failure_rate_round_4_only(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    """All transactions fail. The fourth communication round (ExecuteDsgPhase/Advance) processed by the signers but never reaches orchestrator back"""

    successful = await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=NUM_TRANSACTIONS,
        amount_range=(0.00001, 100),
    )
    assert successful >= NUM_TRANSACTIONS * 1.0, f"Too many submissions failed: {successful}/{NUM_TRANSACTIONS}"


# == DELAY =====================================================================


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="DELAY", failure_rate=100, metadata={"delay_ms": 5000})
@pytest.mark.collect_db_state
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
@pytest.mark.collect_db_state
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
@pytest.mark.collect_db_state
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


# == MUTATE ====================================================================


@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="MUTATE", failure_rate=100)
@pytest.mark.collect_db_state
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
@pytest.mark.collect_db_state
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
@pytest.mark.collect_db_state
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


# == REPLAY ===================================================================


# TODO - Possible race condition, sometimes more than 1 tx passes successfully
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0")
@pytest.mark.inject_fault(fault_type="REPLAY", failure_rate=100)
@pytest.mark.collect_db_state
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
