import logging
from decimal import Decimal

import pytest

from faultlab.db.mpc_keys import MpcKeysRepository
from faultlab.client.blockchain import FunderClient

logger = logging.getLogger(__name__)


@pytest.mark.setup
def test_fund_single_account(funder_client: FunderClient):
    amount = Decimal("1000")
    recipient = "0x277706403ba29686733eebe728e83fb89a4f81a0"
    initial_balance = funder_client.get_account_balance(recipient)
    logger.info(f"Initial balance for {recipient}: {initial_balance} ETH")

    tx_hashes = funder_client.fund_account(recipient, amount)

    assert tx_hashes is not None
    for tx_hash in tx_hashes:
        assert len(tx_hash) == 66  # 0x prefix + 64 characters

    final_balance = funder_client.get_account_balance(recipient)
    logger.info(f"Final balance for {recipient}: {final_balance} ETH")
    assert final_balance > initial_balance


@pytest.mark.setup
def test_fund_account_from_database(funder_client: FunderClient, mpc_keys_repository: MpcKeysRepository):
    df = mpc_keys_repository.fetch_all()
    if df.empty:
        pytest.skip("No accounts found in database")

    addresses = df["ethereum_address"].tolist()
    logger.info(f"Found {len(addresses)} accounts in database")

    amount = Decimal("10000.0")

    for recipient in addresses:
        tx_hash = funder_client.fund_account(recipient, amount)
        assert tx_hash is not None
        logger.info(f"Funded {recipient} with {amount} ETH")
