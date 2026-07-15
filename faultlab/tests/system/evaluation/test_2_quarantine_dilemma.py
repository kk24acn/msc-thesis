import pytest
from faultlab.client.orchestrator import OrchestratorClient
from faultlab.client.proxy import ProxyControlPlaneClient
from tests.conftest import EVAL_RESULTS_BASE_DIR, EVAL_BATCH_SIZE, SUBMISSION_CONCURRENCY

EVAL_RESULTS_BASE_DIR = f"{EVAL_RESULTS_BASE_DIR}/2_QUARANTINE_DILEMMA"

###################################################################################################################
# ----------------------------------------  TRANS-01 -------------------------------------------------------------#


@pytest.mark.parametrize("fail_rate", [3, 16, 44])
@pytest.mark.collect_db_state(results_subdir=f"{EVAL_RESULTS_BASE_DIR}/TRANS_01", disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", quarantine_mode="DISABLED")
async def test_trans_01_micro_jitter_quarantine_disabled(
    orchestrator_client: OrchestratorClient,
    proxy_client: ProxyControlPlaneClient,
    mpc_accounts: list[tuple[str, str]],
    fail_rate: int,
) -> None:
    await proxy_client.inject_all(fault_type="SILENT_DROP_RES", failure_rate=fail_rate, signers=[2])

    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=EVAL_BATCH_SIZE,
        amount_range=(0.00001, 100),
        max_concurrency=SUBMISSION_CONCURRENCY,
    )
    print(f"TRANS-01 | Quarantine Mode: DISABLED | Failure Rate: {fail_rate}%")


@pytest.mark.parametrize("fail_rate", [3, 16, 44])
@pytest.mark.collect_db_state(results_subdir=f"{EVAL_RESULTS_BASE_DIR}/TRANS_01", disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", quarantine_mode="SOFT")
async def test_trans_01_micro_jitter_quarantine_soft(
    orchestrator_client: OrchestratorClient,
    proxy_client: ProxyControlPlaneClient,
    mpc_accounts: list[tuple[str, str]],
    fail_rate: int,
) -> None:
    await proxy_client.inject_all(fault_type="SILENT_DROP_RES", failure_rate=fail_rate, signers=[2])

    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=EVAL_BATCH_SIZE,
        amount_range=(0.00001, 100),
        max_concurrency=SUBMISSION_CONCURRENCY,
    )
    print(f"TRANS-01 | Quarantine Mode: SOFT | Failure Rate: {fail_rate}%")


@pytest.mark.parametrize("fail_rate", [3, 16, 44])
@pytest.mark.collect_db_state(results_subdir=f"{EVAL_RESULTS_BASE_DIR}/TRANS_01", disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", quarantine_mode="CIRCUIT_BREAKER")
async def test_trans_01_micro_jitter_quarantine_circuit_breaker(
    orchestrator_client: OrchestratorClient,
    proxy_client: ProxyControlPlaneClient,
    mpc_accounts: list[tuple[str, str]],
    fail_rate: int,
) -> None:
    await proxy_client.inject_all(fault_type="SILENT_DROP_RES", failure_rate=fail_rate, signers=[2])

    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=EVAL_BATCH_SIZE,
        amount_range=(0.00001, 100),
        max_concurrency=SUBMISSION_CONCURRENCY,
    )
    print(f"TRANS-01 | Quarantine Mode: CIRCUIT_BREAKER | Failure Rate: {fail_rate}%")


###################################################################################################################
# ----------------------------------------  TRANS-02 -------------------------------------------------------------#


@pytest.mark.collect_db_state(results_subdir=f"{EVAL_RESULTS_BASE_DIR}/TRANS_02", disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", quarantine_mode="DISABLED")
@pytest.mark.inject_fault(fault_type="SILENT_DROP_RES", failure_rate=100, signers=[2], until_trace_id=500)
async def test_trans_02_macro_outage_quarantine_disabled(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=EVAL_BATCH_SIZE,
        amount_range=(0.00001, 100),
        max_concurrency=SUBMISSION_CONCURRENCY,
    )
    print(f"TRANS-02 | Quarantine Mode: DISABLED")


@pytest.mark.collect_db_state(results_subdir=f"{EVAL_RESULTS_BASE_DIR}/TRANS_02", disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", quarantine_mode="SOFT")
@pytest.mark.inject_fault(fault_type="SILENT_DROP_RES", failure_rate=100, signers=[2], until_trace_id=500)
async def test_trans_02_macro_outage_quarantine_soft(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=EVAL_BATCH_SIZE,
        amount_range=(0.00001, 100),
        max_concurrency=SUBMISSION_CONCURRENCY,
    )
    print(f"TRANS-02 | Quarantine Mode: SOFT")


@pytest.mark.collect_db_state(results_subdir=f"{EVAL_RESULTS_BASE_DIR}/TRANS_02", disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", quarantine_mode="CIRCUIT_BREAKER")
@pytest.mark.inject_fault(fault_type="SILENT_DROP_RES", failure_rate=100, signers=[2], until_trace_id=500)
async def test_trans_02_macro_outage_quarantine_circuit_breaker(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=EVAL_BATCH_SIZE,
        amount_range=(0.00001, 100),
        max_concurrency=SUBMISSION_CONCURRENCY,
    )
    print(f"TRANS-02 | Quarantine Mode: CIRCUIT_BREAKER")
