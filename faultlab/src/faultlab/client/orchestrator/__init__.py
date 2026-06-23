import asyncio
import logging
import math
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

    async def submit_transaction(self, key_id: str, amount: Decimal, to_address: str) -> Response:
        payload = {"keyId": key_id, "toAddress": to_address, "amountEther": str(amount)}
        logger.debug(f"Submitting transaction request with payload={payload}")
        return await self.client.post(self.base_url, json=payload)

    async def _submit_transaction_task(
        self,
        key_id: str,
        to_address: str,
        amount: Decimal,
        semaphore: asyncio.Semaphore,
    ) -> bool:
        try:
            async with semaphore:
                response = await self.submit_transaction(
                    key_id=key_id,
                    amount=amount,
                    to_address=to_address,
                )

            if not response.is_success:
                logger.warning(
                    f"Transaction request from key_id={key_id} failed. "
                    f"Status: {response.status_code}"
                )
            return response.is_success
        except Exception as e:
            logger.warning(f"Transaction submission from key_id={key_id} failed - {e!r}")
            return False

    async def submit_transactions_batch(
        self,
        accounts: list[tuple[str, str]],
        count: int,
        amount_range: tuple[float, float] = DEFAULT_AMOUNT_RANGE,
        max_concurrency: int = 100,
    ) -> int:
        semaphore = asyncio.Semaphore(max_concurrency)
        tasks = []

        # Build a balanced sender list: each key_id appears exactly floor(count/N)
        # or ceil(count/N) times, then shuffle to decouple submission order from key_id.
        key_ids = [key_id for key_id, _ in accounts]
        senders = (key_ids * math.ceil(count / len(key_ids)))[:count]
        random.shuffle(senders)

        for from_key_id in senders:
            to_address = random.choice([addr for k, addr in accounts if k != from_key_id])
            amount = Decimal(str(round(random.uniform(amount_range[0], amount_range[1]), 6)))
            tasks.append(
                self._submit_transaction_task(
                    from_key_id,
                    to_address,
                    amount,
                    semaphore,
                )
            )

        logger.info(f"Submitting {count} concurrent transactions...")
        results = await tqdm.gather(*tasks, desc="Transactions", total=count)
        successful = sum(results)
        logger.info(f"All {count} transactions completed ({successful} successful)")
        return successful
