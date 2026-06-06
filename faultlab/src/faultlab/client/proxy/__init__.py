import asyncio
import logging
import random
from typing import Any, Literal

import httpx

logger = logging.getLogger(__name__)


class ProxyControlPlaneError(Exception):
    """Raised when a proxy control plane operation fails."""

    pass


class ProxyControlPlaneClient:
    def __init__(self, control_plane_urls: list[str]):
        self._urls = control_plane_urls

    async def _post(self, url: str, path: str, payload: dict | None = None) -> dict:
        async with httpx.AsyncClient() as client:
            response = await client.post(f"{url}{path}", json=payload or {})
            if response.is_error:
                body = response.json().get("message", response.text)
                raise httpx.HTTPStatusError(body, request=response.request, response=response)
            return response.json()

    async def _get(self, url: str, path: str) -> dict:
        async with httpx.AsyncClient() as client:
            response = await client.get(f"{url}{path}")
            if response.is_error:
                body = response.json().get("message", response.text)
                raise httpx.HTTPStatusError(body, request=response.request, response=response)
            return response.json()

    async def _gather_and_verify(self, coroutines: list, urls: list[str] | None = None) -> list:
        if urls is None:
            urls = self._urls
        results = await asyncio.gather(*coroutines, return_exceptions=True)
        for url, result in zip(urls, results):
            if isinstance(result, Exception):
                raise ProxyControlPlaneError(f"Failed to contact {url}: {result}") from result
        return results

    async def inject_all(
        self,
        fault_type: Literal["SILENT_DROP_RES", "CRASH_RES", "DELAY", "MUTATE", "REPLAY"],
        failure_rate: int = 100,
        metadata: dict[str, Any] | None = None,
        rounds: list[int] | None = None,
        signers: list[int] | int | None = None,
        inject_until_retry: int | None = None,
        until_trace_id: int | None = None,
    ) -> None:
        urls = self._urls

        if isinstance(signers, int):  # random `n` signers
            selected = random.sample(range(len(self._urls)), min(signers, len(self._urls)))
            signers = [i + 1 for i in selected]  # convert to 1-based for logging
            urls = [self._urls[i] for i in selected]
        elif isinstance(signers, list):
            out_of_range = [s for s in signers if s < 1 or s > len(self._urls)]
            if out_of_range:
                raise ProxyControlPlaneError(f"Signer indices {out_of_range} out of range (1-{len(self._urls)})")
            urls = [self._urls[s - 1] for s in signers]

        payload: dict[str, Any] = {
            "enabled": True,
            "fault_type": fault_type,
            "failure_rate": failure_rate,
            "metadata": metadata or {},
        }
        if rounds is not None:
            payload["rounds"] = rounds
        if inject_until_retry is not None:
            payload["inject_until_retry"] = inject_until_retry
        if until_trace_id is not None:
            payload["until_trace_id"] = until_trace_id

        await self._gather_and_verify([self._post(url, "/inject", payload) for url in urls], urls=urls)

        logger.info(
            f"Injected {fault_type} on signers={signers} (rate={failure_rate}%, "
            f"rounds={rounds}, until_retry={inject_until_retry}, until_trace_id={until_trace_id})"
        )

    async def reset_all(self) -> None:
        await self._gather_and_verify([self._post(url, "/reset") for url in self._urls])
        logger.info(f"Reset faults on {len(self._urls)} proxies")

    async def get_all_statuses(self) -> list[dict]:
        return await self._gather_and_verify([self._get(url, "/status") for url in self._urls])
