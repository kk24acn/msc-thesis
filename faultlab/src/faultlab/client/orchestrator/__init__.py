import asyncio
import logging
import random
from decimal import Decimal

from httpx import AsyncClient, Response
from tqdm.asyncio import tqdm

logger = logging.getLogger(__name__)

DEFAULT_AMOUNT_RANGE = (0.0001, 100)


class OrchestratorClient:
    def __init__(self, client: AsyncClient, base_url: str):
        self.client: AsyncClient = client
        self.base_url: str = base_url

    async def submit_transaction(self, key_id: str, amount: Decimal, to_address: str, trace_id: int = 0) -> Response:
        payload = {"keyId": key_id, "toAddress": to_address, "amountEther": str(amount)}
        logger.debug(f"Submitting transaction request with payload={payload}")
        return await self.client.post(self.base_url, json=payload, headers={"x-trace-id": str(trace_id)})

    async def _submit_transaction_task(
        self,
        key_id: str,
        from_address: str,
        to_address: str,
        amount: Decimal,
        semaphore: asyncio.Semaphore,
        trace_id: int = 0,
    ) -> bool:
        try:
            async with semaphore:
                response = await self.submit_transaction(
                    key_id=key_id, amount=amount, to_address=to_address, trace_id=trace_id
                )

            if not response.is_success:
                logger.warning(
                    f"Transaction request from key_id={key_id} failed. "
                    f"TX: {amount} ETH | {from_address} -> {to_address}. "
                    f"Status: {response.status_code}"
                )
            return response.is_success
        except Exception as e:
            logger.warning(f"Transaction submission failed: {e!r}")
            return False

    async def submit_transactions_batch(
        self,
        accounts: list[tuple[str, str]],
        count: int,
        amount_range: tuple[float, float] = DEFAULT_AMOUNT_RANGE,
        max_concurrency: int = 50,
    ) -> int:
        semaphore = asyncio.Semaphore(max_concurrency)
        tasks = []
        for trace_id in range(count):
            (from_key_id, from_address), (_, to_address) = random.sample(accounts, 2)
            amount = Decimal(str(round(random.uniform(amount_range[0], amount_range[1]), 6)))
            tasks.append(
                self._submit_transaction_task(
                    from_key_id,
                    from_address,
                    to_address,
                    amount,
                    semaphore,
                    trace_id=trace_id,
                )
            )

        logger.info(f"Submitting {count} concurrent transactions...")
        results = await tqdm.gather(*tasks, desc="Transactions", total=count)
        successful = sum(results)
        logger.info(f"All {count} transactions completed ({successful} successful)")
        return successful
