import logging
from typing import Any, cast
from decimal import Decimal

from web3 import Web3
from web3.types import RPCEndpoint
from eth_account.signers.local import LocalAccount


logger = logging.getLogger(__name__)


class BlockchainClientError(Exception):
    """Raised when blockchain operations fail."""

    pass


class BlockchainClient:

    def __init__(self, rpc_url: str, chain_id: int) -> None:
        self.web3 = Web3(Web3.HTTPProvider(rpc_url))
        self.chain_id = chain_id

        if not self.web3.is_connected():
            raise BlockchainClientError(f"Failed to connect to Hardhat node at {rpc_url}")

    def _call_hardhat_rpc(self, method: str, params: list) -> Any:
        try:
            response = self.web3.provider.make_request(cast(RPCEndpoint, method), params)
            if "error" in response:
                raise BlockchainClientError(f"Hardhat RPC error: {response['error']['message']}")
            return response.get("result")
        except Exception as e:
            raise BlockchainClientError(
                f"Failed to call Hardhat RPC method '{method}': {e}. Make sure you're connected to a Hardhat node."
            ) from e

    def get_account_balance(self, address: str) -> Decimal:
        try:
            address = Web3.to_checksum_address(address)
            balance_wei = self.web3.eth.get_balance(address)
            return Decimal(Web3.from_wei(balance_wei, "ether"))
        except Exception as e:
            raise BlockchainClientError(f"Failed to get balance for {address}: {e}") from e

    def get_hardhat_base_fee(self) -> int:
        try:
            result = self._call_hardhat_rpc("eth_getBlockByNumber", ["pending", False])
            if result and "baseFeePerGas" in result:
                return int(result["baseFeePerGas"], 16)
            return 0
        except BlockchainClientError:
            return 0

    def set_gas_fees(self, new_fee: int) -> int:
        old_fee = self.get_hardhat_base_fee()
        try:
            self._call_hardhat_rpc("hardhat_setNextBlockBaseFeePerGas", [hex(new_fee)])
            logger.debug(
                f"Modified gas fees on Hardhat chain "
                f"({Web3.from_wei(old_fee, 'gwei')} -> {Web3.from_wei(new_fee, 'gwei')} gwei)"
            )
        except BlockchainClientError as e:
            logger.warning(f"Could not modify gas fees on Hardhat chain: {e}.")
        return old_fee

    def transfer_eth(self, amount_eth: Decimal, from_account: LocalAccount, recipient_address: str):
        try:
            recipient_address = Web3.to_checksum_address(recipient_address)
            amount_wei = Web3.to_wei(amount_eth, "ether")
            nonce = self.web3.eth.get_transaction_count(from_account.address)

            tx = {
                "from": from_account.address,
                "to": recipient_address,
                "value": amount_wei,
                "gas": 21000,
                "gasPrice": 0,
                "nonce": nonce,
                "chainId": self.chain_id,
            }
            signed_tx = self.web3.eth.account.sign_transaction(tx, from_account.key)
            tx_hash = self.web3.eth.send_raw_transaction(signed_tx.raw_transaction)
            receipt = self.web3.eth.wait_for_transaction_receipt(tx_hash, timeout=30)

            if receipt["status"] != 1:
                raise BlockchainClientError(
                    f"Transaction from {from_account.address} failed with status {receipt['status']}"
                )

            tx_hash_str = "0x" + tx_hash.hex()
            logger.debug(f"Funded {amount_eth} ETH from {from_account.address} (tx: {tx_hash_str})")
            return tx_hash_str
        except BlockchainClientError:
            raise
        except Exception as e:
            raise BlockchainClientError(f"Failed to fund from {from_account.address}: {e}") from e
