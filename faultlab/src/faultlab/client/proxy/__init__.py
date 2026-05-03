import asyncio
import logging
from typing import Any

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
            response.raise_for_status()
            return response.json()

    async def _get(self, url: str, path: str) -> dict:
        async with httpx.AsyncClient() as client:
            response = await client.get(f"{url}{path}")
            response.raise_for_status()
            return response.json()

    async def inject_all(
        self,
        fault_type: str,
        target_method: str,
        failure_rate: int = 100,
        target_service: str = "",
        metadata: dict[str, Any] | None = None,
    ) -> None:
        payload = {
            "enabled": True,
            "fault_type": fault_type,
            "target_method": target_method,
            "target_service": target_service,
            "failure_rate": failure_rate,
            "metadata": metadata or {},
        }
        results = await asyncio.gather(
            *[self._post(url, "/inject", payload) for url in self._urls],
            return_exceptions=True,
        )
        for url, result in zip(self._urls, results):
            if isinstance(result, Exception):
                raise ProxyControlPlaneError(f"Failed to inject fault on {url}: {result}") from result

        logger.info(f"Injected {fault_type} on {len(self._urls)} proxies (method={target_method}, rate={failure_rate}%)") #fmt: skip

    async def reset_all(self) -> None:
        results = await asyncio.gather(
            *[self._post(url, "/reset") for url in self._urls],
            return_exceptions=True,
        )
        for url, result in zip(self._urls, results):
            if isinstance(result, Exception):
                raise ProxyControlPlaneError(f"Failed to reset fault on {url}: {result}") from result

        logger.info(f"Reset faults on {len(self._urls)} proxies")

    async def get_all_statuses(self) -> list[dict]:
        results = await asyncio.gather(
            *[self._get(url, "/status") for url in self._urls],
            return_exceptions=True,
        )
        statuses = []
        for url, result in zip(self._urls, results):
            if isinstance(result, Exception):
                raise ProxyControlPlaneError(f"Failed to get status from {url}: {result}") from result
            statuses.append(result)
        return statuses
