import pytest
import httpx
import asyncpg
import asyncio
import logging
import time
from decimal import Decimal
from pathlib import Path

from faultlab.client.blockchain.funder_client import FunderClient
from faultlab.client.docker import DockerClient
from faultlab.config import settings
from faultlab.client.orchestrator import OrchestratorClient
from faultlab.db.mpc_keys import MpcKeysRepository
from faultlab.db.transactions import TransactionsRepository
from faultlab.client.signer import DkgClient
from faultlab.client.proxy import ProxyControlPlaneClient
from faultlab.analysis import save_transactions_csv, TERMINAL_STATUSES

logger = logging.getLogger(__name__)

logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)


async def setup_and_fund_mpc_accounts(
    mpc_keys_repository: MpcKeysRepository,
    dkg_client: DkgClient,
    funder_client: FunderClient,
    num_accounts: int,
    funding_amount_eth: str,
):
    try:
        await dkg_client.setup_keys(
            num_sessions=num_accounts,
            threshold=settings.DKG_THRESHOLD,
            total_parties=settings.DKG_TOTAL_PARTIES,
        )
        keys_df = mpc_keys_repository.fetch_all()
        await asyncio.to_thread(
            funder_client.fund_accounts_batch,
            keys_df["ethereum_address"].tolist(),
            Decimal(funding_amount_eth),
        )
        return keys_df
    except Exception as e:
        logger.error(f"DKG setup failed: {e}")
        raise


@pytest.fixture
async def db_pool():
    pool = await asyncpg.create_pool(dsn=settings.DATABASE_URL)
    yield pool
    await pool.close()


@pytest.fixture
def http_client():
    client = httpx.AsyncClient(timeout=settings.HTTP_TIMEOUT)
    yield client
    # Use asyncio.run to create a new event loop for cleanup
    # This avoids "RuntimeError: Event loop is closed" when pytest-asyncio
    # has already closed the main event loop before async fixture teardown
    try:
        asyncio.run(client.aclose())
    except RuntimeError:
        # Event loop already closed, skip cleanup
        pass


@pytest.fixture
def orchestrator_client(http_client: httpx.AsyncClient) -> OrchestratorClient:
    if not settings.ORCHESTRATOR_URL:
        pytest.skip("ORCHESTRATOR_URL not configured")

    return OrchestratorClient(client=http_client, base_url=settings.ORCHESTRATOR_URL)


@pytest.fixture(scope="session")
def dkg_client() -> DkgClient:
    if not settings.SIGNER_GRPC_URLS:
        pytest.skip("SIGNER_GRPC_URLS not configured")
    if not settings.DATABASE_URL:
        pytest.skip("DATABASE_URL not configured")
    if not settings.DKG_DERIVATION_PATH:
        pytest.skip("DKG_DERIVATION_PATH not configured")

    return DkgClient(
        signer_grpc_urls=settings.SIGNER_GRPC_URLS,
        db_dsn=settings.DATABASE_URL,
        derivation_path=settings.DKG_DERIVATION_PATH,
    )


@pytest.fixture(scope="session")
def funder_client() -> FunderClient:
    if not settings.HARDHAT_RPC_URL:
        pytest.skip("HARDHAT_RPC_URL not configured")
    if not settings.FUNDING_PRIVATE_KEYS:
        pytest.skip("FUNDING_PRIVATE_KEYS not configured")
    if not settings.HARDHAT_CHAIN_ID:
        pytest.skip("HARDHAT_CHAIN_ID not configured")

    return FunderClient(
        rpc_url=settings.HARDHAT_RPC_URL,
        funder_private_keys=settings.FUNDING_PRIVATE_KEYS,
        chain_id=settings.HARDHAT_CHAIN_ID,
    )


@pytest.fixture(scope="session")
def docker_client() -> DockerClient:
    if not settings.DOCKER_PROJECT_NAME:
        pytest.skip("DOCKER_PROJECT_NAME not configured")

    return DockerClient(project_name=settings.DOCKER_PROJECT_NAME)


@pytest.fixture
def mpc_keys_repository() -> MpcKeysRepository:
    if not settings.DATABASE_URL:
        pytest.skip("DATABASE_URL not configured")

    return MpcKeysRepository(db_url=settings.DATABASE_URL)


@pytest.fixture
def transactions_repository() -> TransactionsRepository:
    if not settings.DATABASE_URL:
        pytest.skip("DATABASE_URL not configured")

    return TransactionsRepository(db_url=settings.DATABASE_URL)


@pytest.fixture(scope="session")
def proxy_client() -> ProxyControlPlaneClient:
    if not settings.PROXY_CONTROL_PLANE_URLS:
        pytest.skip("PROXY_CONTROL_PLANE_URLS not configured")

    return ProxyControlPlaneClient(control_plane_urls=settings.PROXY_CONTROL_PLANE_URLS)


@pytest.fixture
def mpc_accounts(mpc_keys_repository: MpcKeysRepository) -> list[tuple[str, str]]:
    keys_df = mpc_keys_repository.fetch_all()
    if len(keys_df) < 1:
        pytest.skip("No accounts found in `mpc_accounts` table")

    return list(zip(keys_df["key_id"], keys_df["ethereum_address"]))


@pytest.fixture(autouse=True)
def collect_db_state(
    request: pytest.FixtureRequest,
    docker_client: DockerClient,
    transactions_repository: TransactionsRepository,
):
    """Save the transactions table to CSV after test completion.

    Polls every 10s until all rows reach a terminal status
    (CONFIRMED / STALLED / FAILED / CRYPTOGRAPHIC_ABORT), then writes a CSV to tests/results/.
    Times out after 120s, logs a warning, and saves the partial snapshot
    without failing the test.

    Marker kwargs:
    - results_subdir: str  — subdirectory under tests/results/ (default: "")
    - disable_ui: bool     — stop dashboard containers before the test and restart them
                             after DB stabilises + CSV is saved (default: False)
    """
    marker = request.node.get_closest_marker("collect_db_state")
    disable_ui = marker.kwargs.get("disable_ui", False) if marker else False

    if disable_ui:
        asyncio.run(docker_client.stop_dashboard_containers())

    yield

    if marker is None:
        return

    if not settings.DATABASE_URL:
        logger.warning("DATABASE_URL not configured, skipping CSV export")
        if disable_ui:
            asyncio.run(docker_client.start_dashboard_containers())
        return

    results_subdir = marker.kwargs.get("results_subdir", "")
    results_dir = Path(__file__).parent.joinpath("results")
    if results_subdir:
        results_dir = results_dir.joinpath(results_subdir)
    results_dir.mkdir(parents=True, exist_ok=True)

    try:
        POLL_INTERVAL_S = 10
        TIMEOUT_S = 500
        elapsed = 0
        df = transactions_repository.fetch_all()

        while elapsed < TIMEOUT_S:
            pending = int((~df["status"].isin(TERMINAL_STATUSES)).sum()) if not df.empty else 0
            if pending == 0:
                break
            logger.info(f"{pending} transactions still pending ({elapsed}s elapsed) — waiting {POLL_INTERVAL_S}s...")
            time.sleep(POLL_INTERVAL_S)
            elapsed += POLL_INTERVAL_S
            df = transactions_repository.fetch_all()
        else:
            pending = int((~df["status"].isin(TERMINAL_STATUSES)).sum()) if not df.empty else 0
            logger.warning(f"Timed out after {TIMEOUT_S}s with {pending} transactions still not in terminal status")

        test_name = request.node.name.replace("[", "_").replace("]", "").replace("/", "_")
        csv_path = save_transactions_csv(df, results_dir, test_name)
        logger.info(f"Saved {len(df):,} row(s) -> {csv_path}")
    finally:
        if disable_ui:
            asyncio.run(docker_client.start_dashboard_containers())


@pytest.fixture(autouse=True)
def blockchain_refresh(
    request: pytest.FixtureRequest,
    docker_client: DockerClient,
    funder_client: FunderClient,
    dkg_client: DkgClient,
    mpc_keys_repository: MpcKeysRepository,
    transactions_repository: TransactionsRepository,
) -> None:
    """Refresh blockchain state when test is marked with @pytest.mark.blockchain_refresh

    Marker accepts optional keyword arguments:
    - num_accounts: int - Number of MPC accounts to generate (default: 10)
    - funding_amount_eth: str - Amount in ETH to fund each account (default: 10_000)

    Performs:
    1. Restarts hardhat (resets chain state)
    2. Clears keyshare DBs on signer nodes
    3. Truncates transactions table
    4. Truncates mpc_accounts table
    5. Restarts orchestrator (resets nonce counters)
    6. Generates and funds new MPC accounts set
    """
    if request.node.get_closest_marker("blockchain_refresh") is None:
        return

    marker = request.node.get_closest_marker("blockchain_refresh")
    num_accounts = marker.kwargs.get("num_accounts", 10)
    funding_amount_eth = marker.kwargs.get("funding_amount_eth", "10000")
    grpc_concurrency_limit = marker.kwargs.get("grpc_concurrency_limit", None)

    logger.info(f"Blockchain refresh triggered for test (num_accounts={num_accounts}, funding={funding_amount_eth} ETH)") # fmt: skip

    orchestrator_env = ({"GRPC_CONCURRENCY_LIMIT": str(grpc_concurrency_limit)} if grpc_concurrency_limit is not None else None) # fmt: skip

    async def refresh():
        await asyncio.gather(
            docker_client.restart_hardhat(),
            docker_client.cleanup_signer_databases(),
            asyncio.to_thread(transactions_repository.truncate),
            asyncio.to_thread(mpc_keys_repository.truncate),
        )
        await asyncio.gather(
            docker_client.restart_orchestrator(env_overrides=orchestrator_env),
            docker_client.restart_proxies(),
            setup_and_fund_mpc_accounts(
                mpc_keys_repository,
                dkg_client,
                funder_client,
                num_accounts=num_accounts,
                funding_amount_eth=funding_amount_eth,
            ),
        )

    asyncio.run(refresh())


@pytest.fixture(autouse=True)
async def inject_fault(request: pytest.FixtureRequest, proxy_client: ProxyControlPlaneClient):
    """Automatically inject faults before test if marked with @pytest.mark.inject_fault.

    Marker accepts keyword arguments:
    - fault_type: Literal["SILENT_DROP_RES", "CRASH_RES", "DELAY", "MUTATE", "REPLAY"] - Type of fault to inject
    - failure_rate: int - Failure rate percentage (1-100)
    - metadata: dict - Fault-specific metadata (see per-fault-type keys below)
    - rounds: list[int] | None - 1-indexed rounds to target (1=Init, 2-4=Advance); None = all rounds
    - signers: list[int] | int | None - 1-indexed signers to disrupt; int = N randomly selected signers; None = all signers
    - inject_until_retry: int | None - stop injecting after this retry count (0=first attempt only, 1=first attempt + first retry, …); None = fault every attempt
    - until_trace_id: int | None - stop injecting for requests with trace id strictly greater than this value; None = fault every trace id

    Metadata keys by fault type:
    - SILENT_DROP_RES:  (no metadata keys)              — silently cancels the RPC context after the signer processes; orchestrator times out
    - CRASH_RES:        (no metadata keys)              — aborts the gRPC connection with UNAVAILABLE after the signer processes
    - DELAY:            delay_ms (int, default 0)       — milliseconds to sleep before forwarding
    - MUTATE:           byte_offset (int, default 10)   — offset from the END of the payload bytes; targets fixed-size crypto fields
                        xor_mask   (int, default 0xFF)  — XOR mask applied to that byte
    - REPLAY:           (no metadata keys)              — on round 4, returns the cached response from the first session instead of forwarding to the signer
    """

    markers = list(request.node.iter_markers("inject_fault"))
    if not markers:
        return

    for marker in markers:
        fault_type = marker.kwargs.get("fault_type", None)
        failure_rate = marker.kwargs.get("failure_rate", 0)
        metadata = marker.kwargs.get("metadata", {})
        rounds = marker.kwargs.get("rounds", None)
        signers = marker.kwargs.get("signers", None)
        inject_until_retry = marker.kwargs.get("inject_until_retry", None)
        until_trace_id = marker.kwargs.get("until_trace_id", None)

        try:
            if fault_type and failure_rate:
                await proxy_client.inject_all(
                    fault_type=fault_type,
                    failure_rate=failure_rate,
                    metadata=metadata,
                    rounds=rounds,
                    signers=signers,
                    inject_until_retry=inject_until_retry,
                    until_trace_id=until_trace_id,
                )
            else:
                logger.info(f"Fault injection skipped (type={fault_type}, rate={failure_rate}%)")
        except Exception as e:
            logger.error(f"Failed to inject fault for {request.node.name}: {e}")
            raise
