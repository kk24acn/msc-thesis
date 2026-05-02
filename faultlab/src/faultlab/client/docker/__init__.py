import asyncio
import logging
import subprocess
import time

import docker
import docker.errors

logger = logging.getLogger(__name__)


class DockerClientError(Exception):
    """Raised when Docker operations fail."""

    pass


class DockerClient:
    def __init__(self, project_name: str = ""):
        try:
            self.client = docker.from_env()
            self.project_name = project_name
        except Exception as e:
            raise DockerClientError(f"Failed to connect to Docker daemon: {e}") from e

    def _format_container_name(self, name: str) -> str:
        return f"{self.project_name}-{name}-1"

    def _format_volume_name(self, name: str) -> str:
        return f"{self.project_name}_{name}"

    async def _wait_for_log(
        self,
        container_name: str,
        expected_message: str,
        timeout_seconds: int,
        since: float | None,
        poll_interval: float = 1.0,
    ) -> bool:
        start_time = time.time()
        while time.time() - start_time < timeout_seconds:
            try:
                container = self.client.containers.get(container_name)
                logs = container.logs(since=since).decode("utf-8")
                if expected_message in logs:
                    return True
            except Exception as e:
                logger.debug(f"Error checking logs for {container_name}: {e}")

            await asyncio.sleep(poll_interval)
        return False

    async def _restart_container(self, name: str) -> float:
        restart_time = time.time()
        container = await asyncio.to_thread(self.client.containers.get, name)
        await asyncio.to_thread(container.restart)
        logger.debug(f"Restart command sent to {name}")
        return restart_time

    async def restart_hardhat(self, timeout_seconds: int = 30) -> None:
        container_name = self._format_container_name("hardhat")
        expected_message = "Started HTTP and WebSocket JSON-RPC server at"

        logger.info("Restarting hardhat container")
        try:
            restart_time = await self._restart_container(container_name)

            if await self._wait_for_log(container_name, expected_message, timeout_seconds, restart_time):
                logger.info("Hardhat is ready")
            else:
                logger.warning(f"Hardhat did not confirm readiness in time")
        except Exception as e:
            raise DockerClientError(f"Failed to restart hardhat: {e}") from e

    async def restart_orchestrator(self, timeout_seconds: int = 60) -> None:
        container_name = self._format_container_name("orchestrator")
        expected_message = "OrchestratorApplication - Started OrchestratorApplication in"

        logger.info("Restarting orchestrator container")
        try:
            restart_time = await self._restart_container(container_name)

            if await self._wait_for_log(container_name, expected_message, timeout_seconds, restart_time):
                logger.info("Orchestrator is ready")
            else:
                logger.warning(f"Orchestrator did not confirm readiness in time")
        except Exception as e:
            raise DockerClientError(f"Failed to restart orchestrator: {e}") from e

    async def restart_proxies(self, timeout_seconds: int = 15) -> None:
        proxy_ids = [1, 2, 3]
        container_names = [self._format_container_name(f"faultlab-proxy-{i}") for i in proxy_ids]
        expected_message = "gRPC proxy *:"

        logger.info("Restarting faultlab proxy containers to reset signer channels")
        try:
            restart_time = time.time()
            await asyncio.gather(*[self._restart_container(name) for name in container_names])
            results = await asyncio.gather(
                *[
                    self._wait_for_log(
                        name,
                        expected_message,
                        timeout_seconds,
                        restart_time,
                    )
                    for name in container_names
                ]
            )

            if not all(results):
                logger.warning("One or more proxies did not confirm readiness in time")
            else:
                logger.info("All proxy containers restarted and ready")
        except Exception as e:
            raise DockerClientError(f"Failed to restart proxy containers: {e}") from e

    async def cleanup_signer_databases(self, timeout_seconds: int = 15) -> None:
        async def remove_container(name: str) -> None:
            try:
                container = await asyncio.to_thread(self.client.containers.get, name)
                await asyncio.to_thread(container.remove, force=True)
                logger.debug(f"Removed {name}")
            except docker.errors.NotFound:
                logger.info(f"Signer container `{name}` does not exist. Proceeding...")
            except Exception as e:
                raise DockerClientError(f"Failed to remove signer container `{name}`: {e}") from e

        async def remove_volume(name: str) -> None:
            try:
                volume = await asyncio.to_thread(self.client.volumes.get, name)
                await asyncio.to_thread(volume.remove)
                logger.debug(f"Removed volume {name}")
            except docker.errors.NotFound:
                logger.info(f"Signer volume `{name}` does not exist. Proceeding...")
            except Exception as e:
                raise DockerClientError(f"Failed to remove volume `{name}`: {e}") from e

        signer_ids = [1, 2, 3]
        container_names = [self._format_container_name(f"signer-{i}") for i in signer_ids]
        volume_names = [self._format_volume_name(f"signer{i}_data") for i in signer_ids]
        expected_message = "Starting MPC Node (party-id:"

        logger.info("Performing signers `redb` databases cleanup")
        try:
            logger.debug("Removing signer containers...")
            await asyncio.gather(*[remove_container(name) for name in container_names])

            logger.debug("Removing signer data volumes...")
            await asyncio.gather(*[remove_volume(name) for name in volume_names])

            logger.debug("Recreating signer containers via docker compose...")
            startup_time = time.time()
            try:
                cmd = ["docker", "compose", "up", "-d", "--no-deps"] + [f"signer-{i}" for i in signer_ids]
                await asyncio.to_thread(subprocess.run, cmd, check=True, capture_output=True)
                logger.debug("Signer containers recreated")
            except subprocess.CalledProcessError as e:
                raise DockerClientError(f"Failed to recreate signer containers with docker compose: {e.stderr.decode()}") from e # fmt: skip

            results = await asyncio.gather(
                *[
                    self._wait_for_log(
                        container_name,
                        expected_message,
                        timeout_seconds,
                        startup_time,
                    )
                    for container_name in container_names
                ]
            )

            if not all(results):
                logger.warning("One or more signers did not confirm readiness in time")
            else:
                logger.info("All signer containers are ready with fresh redb databases")
        except Exception as e:
            raise DockerClientError(f"Failed to cleanup signer databases: {e}") from e

    async def stop_dashboard_containers(self) -> None:
        """Stop dashboard containers to reduce resource usage during metrics collection."""
        dashboard_services = ["dashboard-postgrest", "dashboard-ui"]
        container_names = [self._format_container_name(service) for service in dashboard_services]

        logger.info("Stopping dashboard containers for fair metrics collection")
        try:
            for container_name in container_names:
                try:
                    container = await asyncio.to_thread(self.client.containers.get, container_name)
                    await asyncio.to_thread(container.stop)
                    logger.debug(f"Stopped {container_name}")
                except docker.errors.NotFound:
                    logger.debug(f"Container {container_name} not found, skipping stop")
                except Exception as e:
                    logger.warning(f"Failed to stop {container_name}: {e}")
        except Exception as e:
            raise DockerClientError(f"Failed to stop dashboard containers: {e}") from e

    async def start_dashboard_containers(self) -> None:
        """Start dashboard containers after metrics collection is complete."""
        dashboard_services = ["dashboard-postgrest", "dashboard-ui"]
        container_names = [self._format_container_name(service) for service in dashboard_services]

        logger.info("Starting dashboard containers")
        try:
            for container_name in container_names:
                try:
                    container = await asyncio.to_thread(self.client.containers.get, container_name)
                    await asyncio.to_thread(container.start)
                    logger.debug(f"Started {container_name}")
                except docker.errors.NotFound:
                    logger.debug(f"Container {container_name} not found, skipping start")
                except Exception as e:
                    logger.warning(f"Failed to start {container_name}: {e}")
        except Exception as e:
            raise DockerClientError(f"Failed to start dashboard containers: {e}") from e
