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

    def _wait_for_log(
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

            time.sleep(poll_interval)
        return False

    def restart_hardhat(self, timeout_seconds: int = 30) -> None:
        container_name = self._format_container_name("hardhat")
        expected_message = "Started HTTP and WebSocket JSON-RPC server at"

        logger.info("Restarting hardhat container")
        try:
            container = self.client.containers.get(container_name)
            restart_time = time.time()
            container.restart()
            logger.debug("Hardhat container restart command sent")

            if self._wait_for_log(container_name, expected_message, timeout_seconds, restart_time):
                logger.info("Hardhat is ready")
            else:
                logger.warning(
                    f"Hardhat container restart timed out after {timeout_seconds}s. "
                    "Node startup may still be pending. Check container logs for details."
                )
        except Exception as e:
            raise DockerClientError(f"Failed to restart hardhat: {e}") from e

    def restart_orchestrator(self, timeout_seconds: int = 60) -> None:
        container_name = self._format_container_name("orchestrator")
        expected_message = "OrchestratorApplication - Started OrchestratorApplication in"

        logger.info("Restarting orchestrator container")
        try:
            container = self.client.containers.get(container_name)
            restart_time = time.time()
            container.restart()
            logger.debug("Orchestrator container restart command sent")

            if self._wait_for_log(container_name, expected_message, timeout_seconds, restart_time):
                logger.info("Orchestrator is ready")
            else:
                logger.warning(
                    f"Orchestrator container restart timed out after {timeout_seconds}s. "
                    "Application startup may still be pending. Check container logs for details."
                )
        except Exception as e:
            raise DockerClientError(f"Failed to restart orchestrator: {e}") from e

    def cleanup_signer_databases(self, timeout_seconds: int = 15) -> None:
        signer_ids = [1, 2, 3]
        container_names = [self._format_container_name(f"signer-{i}") for i in signer_ids]
        volume_names = [self._format_volume_name(f"signer{i}_data") for i in signer_ids]
        expected_message = "Starting MPC Node (party-id:"

        logger.info("Performing signers `redb` databases cleanup")
        try:
            logger.debug("Removing signer containers...")
            for container_name in container_names:
                try:
                    container = self.client.containers.get(container_name)
                    container.remove(force=True)  # force=True stops the container if running
                    logger.debug(f"Removed {container_name}")
                except docker.errors.NotFound as e:
                    logger.info(f"Signer container `{container_name}` does not exist. Proceeding...")
                except Exception as e:
                    raise DockerClientError(f"Failed to remove signer container `{container_name}`: {e}") from e

            logger.debug("Removing signer data volumes...")
            for volume_name in volume_names:
                try:
                    volume = self.client.volumes.get(volume_name)
                    volume.remove()
                    logger.debug(f"Removed volume {volume_name}")
                except docker.errors.NotFound as e:
                    logger.info(f"Signer volume `{volume_name}` does not exist. Proceeding...")
                except Exception as e:
                    raise DockerClientError(f"Failed to remove volume `{volume_name}`: {e}") from e

            logger.debug("Recreating signer containers via docker compose...")
            startup_time = time.time()
            try:
                # Use docker compose to recreate the removed containers
                signer_names = [f"signer-{i}" for i in signer_ids]
                cmd = ["docker", "compose", "up", "-d", "--no-deps"] + signer_names
                subprocess.run(cmd, check=True, capture_output=True)
                logger.debug("Signer containers recreated")
            except subprocess.CalledProcessError as e:
                raise DockerClientError(
                    f"Failed to recreate signer containers with docker compose: {e.stderr.decode()}"
                ) from e

            for container_name in container_names:
                self._wait_for_log(container_name, expected_message, timeout_seconds, startup_time)

            logger.info("Signer containers are ready with fresh redb databases")
        except Exception as e:
            raise DockerClientError(f"Failed to cleanup signer databases: {e}") from e
