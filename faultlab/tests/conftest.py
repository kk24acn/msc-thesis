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
    return OrchestratorClient(client=http_client, base_url=settings.ORCHESTRATOR_URL)


@pytest.fixture(scope="session")
def dkg_client() -> DkgClient:
    return DkgClient(
        signer_ports=settings.SIGNER_PORTS,
        db_dsn=settings.DATABASE_URL,
        derivation_path=settings.DKG_DERIVATION_PATH,
    )


@pytest.fixture(scope="session")
def funder_client() -> FunderClient:
    return FunderClient(
        rpc_url=settings.HARDHAT_RPC_URL,
        funder_private_keys=settings.FUNDING_PRIVATE_KEYS,
        chain_id=settings.HARDHAT_CHAIN_ID,
    )


@pytest.fixture(scope="session")
def docker_client() -> DockerClient:
    return DockerClient(project_name=settings.DOCKER_PROJECT_NAME)


@pytest.fixture
def mpc_keys_repository() -> MpcKeysRepository:
    return MpcKeysRepository(dsn=settings.DATABASE_URL)


@pytest.fixture
def transactions_repository() -> TransactionsRepository:
    return TransactionsRepository(dsn=settings.DATABASE_URL)


@pytest.fixture(autouse=True)
def collect_metrics(request: pytest.FixtureRequest, transactions_repository: TransactionsRepository):
    """Collect and save transaction metrics for tests marked with @pytest.mark.collect_metrics"""
    if request.node.get_closest_marker("collect_metrics") is None:
        return
    transactions_repository.truncate()
    start_time = datetime.now(timezone.utc)

    yield

    try:
        metrics = compute_metrics(transactions_repository, start_time)
        metrics.log()
        save_metrics_csv(request.node.name, transactions_repository, start_time)
    except Exception as e:
        logger.error(f"Failed to collect metrics for {request.node.name}: {e}")


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
            setup_and_fund_mpc_accounts(mpc_keys_repository, dkg_client, funder_client),
        )

    asyncio.run(refresh())


@pytest.fixture
def mpc_accounts(mpc_keys_repository: MpcKeysRepository) -> list[tuple[str, str]]:
    keys_df = mpc_keys_repository.fetch_all()
    if len(keys_df) < 1:
        pytest.skip("No accounts found in `mpc_accounts` table")

    return list(zip(keys_df["key_id"], keys_df["ethereum_address"]))
