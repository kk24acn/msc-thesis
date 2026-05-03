import pytest
import httpx
import asyncpg
import asyncio
import logging
from datetime import datetime, timezone

from decimal import Decimal

from faultlab.client.blockchain.funder_client import FunderClient
from faultlab.client.docker import DockerClient
from faultlab.config import settings
from faultlab.client.orchestrator import OrchestratorClient
from faultlab.db.mpc_keys import MpcKeysRepository
from faultlab.db.transactions import TransactionsRepository
from faultlab.client.signer import DkgClient
from faultlab.client.proxy import ProxyControlPlaneClient
from faultlab.analysis.metrics import compute_metrics, save_metrics_csv

logger = logging.getLogger(__name__)

logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)


async def setup_and_fund_mpc_accounts(
    mpc_keys_repository: MpcKeysRepository,
    dkg_client: DkgClient,
    funder_client: FunderClient,
):
    try:
        await dkg_client.setup_keys(
            num_sessions=settings.DKG_SESSIONS,
            threshold=settings.DKG_THRESHOLD,
            total_parties=settings.DKG_TOTAL_PARTIES,
        )
        keys_df = mpc_keys_repository.fetch_all()
        await asyncio.to_thread(
            funder_client.fund_accounts_batch,
            keys_df["ethereum_address"].tolist(),
            Decimal(settings.FUNDING_AMOUNT_ETH),
        )
        return keys_df
    except Exception as e:
        logger.error(f"DKG setup failed: {e}")
        raise


@pytest.fixture(scope="session")
def event_loop():
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    yield loop
    loop.close()


@pytest.fixture(scope="session")
async def db_pool():
    pool = await asyncpg.create_pool(dsn=settings.DATABASE_URL)
    yield pool
    await pool.close()


@pytest.fixture(scope="session")
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


@pytest.fixture(scope="session")
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

    return MpcKeysRepository(dsn=settings.DATABASE_URL)


@pytest.fixture
def transactions_repository() -> TransactionsRepository:
    if not settings.DATABASE_URL:
        pytest.skip("DATABASE_URL not configured")

    return TransactionsRepository(dsn=settings.DATABASE_URL)


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
def collect_metrics(
    request: pytest.FixtureRequest, transactions_repository: TransactionsRepository, docker_client: DockerClient
):
    """Collect and save transaction metrics for tests marked with @pytest.mark.collect_metrics.

    Stops non-essential dashboard containers during test execution for fair metrics collection,
    then restarts them after completion.
    """
    if request.node.get_closest_marker("collect_metrics") is None:
        yield
        return

    async def setup_metrics():
        await docker_client.stop_dashboard_containers()
        transactions_repository.truncate()

    async def teardown_metrics():
        try:
            metrics = compute_metrics(transactions_repository, start_time)
            metrics.log()
            save_metrics_csv(request.node.name, transactions_repository, start_time)
        except Exception as e:
            logger.error(f"Failed to collect metrics for {request.node.name}: {e}")
        finally:
            await docker_client.start_dashboard_containers()

    asyncio.run(setup_metrics())
    start_time = datetime.now(timezone.utc)

    yield

    asyncio.run(teardown_metrics())


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

    This fixture performs:
    1. Restarts hardhat (resets chain state)
    2. Clears keyshare DBs on signer nodes
    3. Truncates transactions table
    4. Truncates mpc_accounts table

    5. Restarts orchestrator (resets nonce counters)
    6. Computes and funds new MPC accounts set
    """
    if request.node.get_closest_marker("blockchain_refresh") is None:
        return

    logger.info("Blockchain refresh triggered for test")

    async def refresh():
        await asyncio.gather(
            docker_client.restart_hardhat(),
            docker_client.cleanup_signer_databases(),
            asyncio.to_thread(transactions_repository.truncate),
            asyncio.to_thread(mpc_keys_repository.truncate),
        )
        await asyncio.gather(
            docker_client.restart_orchestrator(),
            docker_client.restart_proxies(),
            setup_and_fund_mpc_accounts(mpc_keys_repository, dkg_client, funder_client),
        )

    asyncio.run(refresh())


@pytest.fixture(autouse=True)
async def fault_injection(request: pytest.FixtureRequest, proxy_client: ProxyControlPlaneClient):
    """Automatically inject faults before test if marked with @pytest.mark.inject_fault.

    Marker accepts keyword arguments:
    - fault_type: str - Type of fault to inject
    - target_method: str - Target method for fault
    - failure_rate: int - Failure rate percentage (1-100)
    - metadata: dict - Fault-specific metadata
    """

    marker: pytest.Mark = request.node.get_closest_marker("inject_fault")
    if marker is None:
        yield
        return

    fault_type = marker.kwargs.get("fault_type", None)
    target_method = marker.kwargs.get("target_method", None)
    failure_rate = marker.kwargs.get("failure_rate", 0)
    metadata = marker.kwargs.get("metadata", {})

    try:
        if fault_type and target_method and failure_rate:
            await proxy_client.inject_all(
                fault_type=fault_type,
                target_method=target_method,
                failure_rate=failure_rate,
                metadata=metadata,
            )
            logger.info(
                f"Injected {fault_type} on proxies (method={target_method},"
                f"rate={failure_rate}%) for test {request.node.name}"
            )
        else:
            logger.info(f"Fault injection skipped (type={fault_type}, method={target_method}, rate={failure_rate}%)")
    except Exception as e:
        logger.error(f"Failed to inject fault for {request.node.name}: {e}")
        raise

    yield

    try:
        await proxy_client.reset_all()
        logger.info(f"Reset faults on proxies after test {request.node.name}")
    except Exception as e:
        logger.error(f"Failed to reset faults after {request.node.name}: {e}")
