from typing import Any, Literal

import pytest
from faultlab.client.orchestrator import OrchestratorClient
from faultlab.client.proxy import ProxyControlPlaneClient
from tests.conftest import EVAL_RESULTS_BASE_DIR, EVAL_BATCH_SIZE, SUBMISSION_CONCURRENCY

EVAL_RESULTS_BASE_DIR = f"{EVAL_RESULTS_BASE_DIR}/3_EDGE_CASES"

##################################################################################################################
# ----------------------------------------  DROP-01 -------------------------------------------------------------#


@pytest.mark.collect_db_state(results_subdir=f"{EVAL_RESULTS_BASE_DIR}/DROP_01", disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", quarantine_mode="CIRCUIT_BREAKER")
@pytest.mark.inject_fault(fault_type="SILENT_DROP_RES", failure_rate=100, signers=[2], metadata={"rounds": [4]})
async def test_drop_01_resource_leak(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=EVAL_BATCH_SIZE,
        amount_range=(0.00001, 100),
        max_concurrency=SUBMISSION_CONCURRENCY,
    )


#################################################################################################################
# ----------------------------------------  BYZ-01 -------------------------------------------------------------#


@pytest.mark.collect_db_state(results_subdir=f"{EVAL_RESULTS_BASE_DIR}/BYZ_01", disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", quarantine_mode="CIRCUIT_BREAKER")
@pytest.mark.inject_fault(fault_type="MUTATE", failure_rate=100, signers=[2], metadata={"offset": 10})
async def test_byz_01_cryptographic_abort(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=EVAL_BATCH_SIZE,
        amount_range=(0.00001, 100),
        max_concurrency=SUBMISSION_CONCURRENCY,
    )


#################################################################################################################
# ----------------------------------------  BYZ-02 -------------------------------------------------------------#


@pytest.mark.collect_db_state(results_subdir=f"{EVAL_RESULTS_BASE_DIR}/BYZ_02", disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", quarantine_mode="CIRCUIT_BREAKER")
@pytest.mark.inject_fault(fault_type="REPLAY", failure_rate=100, signers=[3])
async def test_byz_02_signature_replay(
    orchestrator_client: OrchestratorClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=EVAL_BATCH_SIZE,
        amount_range=(0.00001, 100),
        max_concurrency=SUBMISSION_CONCURRENCY,
    )


###################################################################################################################
# ----------------------------------------  SWEEP-01 -------------------------------------------------------------#


@pytest.mark.collect_db_state(results_subdir=f"{EVAL_RESULTS_BASE_DIR}/SWEEP_00", disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", quarantine_mode="CIRCUIT_BREAKER", nonce_gap_sweeper_enabled="false") # fmt: skip
async def test_SWEEP_00_sequence_sweeper_capacity_nonce_gap_sweeper_disabled(
    orchestrator_client: OrchestratorClient,
    proxy_client: ProxyControlPlaneClient,
    mpc_accounts: list[tuple[str, str]],
) -> None:
    await proxy_client.inject_all(fault_type="CRASH_RES", failure_rate=20, signers=[2, 3])

    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=EVAL_BATCH_SIZE,
        amount_range=(0.00001, 100),
        max_concurrency=SUBMISSION_CONCURRENCY,
    )


@pytest.mark.parametrize(
    "fault_type, params",
    [
        ("CRASH_RES", {}),
        ("MUTATE", {"metadata": {"offset": 10}}),
        ("SILENT_DROP_RES", {"rounds": [4]}),
    ],
)
@pytest.mark.collect_db_state(results_subdir=f"{EVAL_RESULTS_BASE_DIR}/SWEEP_01", disable_ui=True)
@pytest.mark.blockchain_refresh(num_accounts=10, funding_amount_eth="10000.0", quarantine_mode="CIRCUIT_BREAKER")
async def test_SWEEP_01_sequence_sweeper_capacity(
    orchestrator_client: OrchestratorClient,
    proxy_client: ProxyControlPlaneClient,
    mpc_accounts: list[tuple[str, str]],
    fault_type: Literal["SILENT_DROP_RES", "CRASH_RES", "DELAY", "MUTATE", "REPLAY"],
    params: dict[str, Any],
) -> None:
    await proxy_client.inject_all(fault_type=fault_type, failure_rate=20, signers=[2, 3], **params)

    await orchestrator_client.submit_transactions_batch(
        accounts=mpc_accounts,
        count=EVAL_BATCH_SIZE,
        amount_range=(0.00001, 100),
        max_concurrency=SUBMISSION_CONCURRENCY,
    )
