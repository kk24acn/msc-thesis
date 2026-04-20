import logging
from typing import List
from decimal import Decimal
from contextlib import contextmanager

from eth_account import Account
from eth_account.signers.local import LocalAccount

from faultlab.client.blockchain.blockchain_client import BlockchainClient, BlockchainClientError


logger = logging.getLogger(__name__)


class FunderClientError(Exception):
    """Raised when funding operations fail."""

    pass


class FunderClient:
    def __init__(self, rpc_url: str, funder_private_keys: list[str], chain_id: int) -> None:
        def initialize_funders() -> List[LocalAccount]:
            if not funder_private_keys:
                raise FunderClientError("At least one funder private key must be provided")

            funders = []
            try:
                for i, key in enumerate(funder_private_keys):
                    account = Account.from_key(key)
                    funders.append(account)
                    logger.debug(f"Initialized funder account [{i}]: {account.address}")
            except Exception as e:
                raise FunderClientError(f"Invalid funder private key: {e}") from e
            return funders

        try:
            self.blockchain_client = BlockchainClient(rpc_url, chain_id)
        except BlockchainClientError as e:
            raise FunderClientError(f"Failed to initialize blockchain client: {e}") from e

        self.funder_accounts = initialize_funders()

    @contextmanager
    def zero_gas_fees(self):
        """Context manager to temporarily disable gas fees and automatically restore them."""
        gas_fee_rem = self.blockchain_client.set_gas_fees(0)
        try:
            yield
        finally:
            self.blockchain_client.set_gas_fees(gas_fee_rem)

    def _get_available_funders(self) -> List[tuple[LocalAccount, Decimal]]:
        funders_with_balance = []

        for funder in self.funder_accounts:
            try:
                balance = self.blockchain_client.get_account_balance(funder.address)
                if balance > Decimal("0"):
                    funders_with_balance.append((funder, balance))
            except FunderClientError:
                pass

        # Sort by balance descending to use richest funders first
        funders_with_balance.sort(key=lambda x: x[1], reverse=True)
        return funders_with_balance

    def _fund_account(self, recipient_address: str, amount_eth: Decimal) -> List[str]:
        tx_hashes = []
        remaining_amount = amount_eth
        available_funders = self._get_available_funders()

        if not available_funders:
            raise FunderClientError(
                f"No funders have available balance to fund {recipient_address} with {amount_eth} ETH"
            )

        total_available = sum(balance for _, balance in available_funders)
        if total_available < amount_eth:
            raise FunderClientError(
                f"Total available funds ({total_available} ETH) "
                f"insufficient to fund {amount_eth} ETH to {recipient_address}"
            )

        logger.debug(
            f"Funding {recipient_address} with {amount_eth} ETH "
            f"using {len(available_funders)} funders (total available: {total_available} ETH)"
        )

        for funder, available_balance in available_funders:
            if remaining_amount <= Decimal("0"):
                break

            # Fund with either the remaining amount or the funder's available balance (whichever is less)
            fund_amount = min(remaining_amount, available_balance)
            tx_hash = self.blockchain_client.transfer_eth(fund_amount, funder, recipient_address)
            tx_hashes.append(tx_hash)
            remaining_amount -= fund_amount

        if remaining_amount > Decimal("0"):
            raise FunderClientError(f"Could not fund full amount. Remaining: {remaining_amount} ETH")
        return tx_hashes

    def _fund_accounts_batch(self, addresses: list[str], amount_eth: Decimal) -> dict[str, List[str]]:
        results: dict[str, List[str]] = {}
        for address in addresses:
            try:
                tx_hash = self._fund_account(address, amount_eth)
                results[address] = tx_hash
            except FunderClientError as e:
                logger.error(f"Failed to fund {address}: {e}")
                raise
        return results

    def fund_account(self, address: str, amount_eth: Decimal) -> List[str]:
        logger.info(f"Funding {address} account with {amount_eth} ETH")
        with self.zero_gas_fees():
            return self._fund_account(address, amount_eth)

    def fund_accounts_batch(self, addresses: list[str], amount_eth: Decimal) -> dict[str, List[str]]:
        logger.info(f"Funding {len(addresses)} MPC accounts with {amount_eth} ETH")
        with self.zero_gas_fees():
            return self._fund_accounts_batch(addresses, amount_eth)

    def get_account_balance(self, address: str) -> Decimal:
        return self.blockchain_client.get_account_balance(address)
