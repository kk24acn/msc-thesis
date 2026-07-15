import pytest
from faultlab.client.orchestrator import OrchestratorClient
from faultlab.client.proxy import ProxyControlPlaneClient
from tests.conftest import EVAL_RESULTS_BASE_DIR, EVAL_BATCH_SIZE, SUBMISSION_CONCURRENCY

EVAL_RESULTS_BASE_DIR = f"{EVAL_RESULTS_BASE_DIR}/1_BASELINE"

##################################################################################################################
# ----------------------------------------  BASE-01 -------------------------------------------------------------#


@pytest.mark.collect_db_state(results_subdir=f"{EVAL_RESULTS_BASE_DIR}/BASE_01", disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", quarantine_mode="DISABLED")
async def test_base_01_baseline_load_quarantine_disabled(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=EVAL_BATCH_SIZE,
        amount_range=(0.00001, 100),
        max_concurrency=SUBMISSION_CONCURRENCY,
    )
    print(f"BASE-01 | Quarantine Mode: DISABLED")


@pytest.mark.collect_db_state(results_subdir=f"{EVAL_RESULTS_BASE_DIR}/BASE_01", disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", quarantine_mode="CIRCUIT_BREAKER")
async def test_base_01_baseline_load_quarantine_circuit_breaker(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=EVAL_BATCH_SIZE,
        amount_range=(0.00001, 100),
        max_concurrency=SUBMISSION_CONCURRENCY,
    )
    print(f"BASE-01 | Quarantine Mode: CIRCUIT_BREAKER")


#####################################################################################################################
# ----------------------------------------  LATENCY-01 -------------------------------------------------------------#


@pytest.mark.parametrize("delay_ms", [100, 500, 1500])
@pytest.mark.collect_db_state(results_subdir=f"{EVAL_RESULTS_BASE_DIR}/LATENCY_01", disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", quarantine_mode="DISABLED")
async def test_latency_01_sub_threshold_degradation_quarantine_disabled(
    orchestrator_client: OrchestratorClient,
    proxy_client: ProxyControlPlaneClient,
    mpc_accounts: list[tuple[str, str]],
    delay_ms: int,
) -> None:
    await proxy_client.inject_all(fault_type="DELAY", failure_rate=100, metadata={"delay_ms": delay_ms})

    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=EVAL_BATCH_SIZE,
        amount_range=(0.00001, 100),
        max_concurrency=SUBMISSION_CONCURRENCY,
    )
    print(f"LATENCY-01 | Quarantine Mode: DISABLED | Delay: {delay_ms}ms")


@pytest.mark.parametrize("delay_ms", [100, 500, 1500])
@pytest.mark.collect_db_state(results_subdir=f"{EVAL_RESULTS_BASE_DIR}/LATENCY_01", disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", quarantine_mode="CIRCUIT_BREAKER")
async def test_latency_01_sub_threshold_degradation_quarantine_circuit_breaker(
    orchestrator_client: OrchestratorClient,
    proxy_client: ProxyControlPlaneClient,
    mpc_accounts: list[tuple[str, str]],
    delay_ms: int,
) -> None:
    await proxy_client.inject_all(fault_type="DELAY", failure_rate=100, metadata={"delay_ms": delay_ms})

    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=EVAL_BATCH_SIZE,
        amount_range=(0.00001, 100),
        max_concurrency=SUBMISSION_CONCURRENCY,
    )
    print(f"LATENCY-01 | Quarantine Mode: CIRCUIT_BREAKER | Delay: {delay_ms}ms")


###################################################################################################################
# ----------------------------------------  CRASH-01 -------------------------------------------------------------#


@pytest.mark.collect_db_state(results_subdir=f"{EVAL_RESULTS_BASE_DIR}/CRASH_01", disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", quarantine_mode="DISABLED")
@pytest.mark.inject_fault(fault_type="CRASH_RES", failure_rate=100, signers=[2])
async def test_crash_01_foundational_failover_quarantine_disabled(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=EVAL_BATCH_SIZE,
        amount_range=(0.00001, 100),
        max_concurrency=SUBMISSION_CONCURRENCY,
    )
    print(f"CRASH-01 | Quarantine Mode: DISABLED")


@pytest.mark.collect_db_state(results_subdir=f"{EVAL_RESULTS_BASE_DIR}/CRASH_01", disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", quarantine_mode="CIRCUIT_BREAKER")
@pytest.mark.inject_fault(fault_type="CRASH_RES", failure_rate=100, signers=[2])
async def test_crash_01_foundational_failover_quarantine_circuit_breaker(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=EVAL_BATCH_SIZE,
        amount_range=(0.00001, 100),
        max_concurrency=SUBMISSION_CONCURRENCY,
    )
    print(f"CRASH-01 | Quarantine Mode: CIRCUIT_BREAKER")
