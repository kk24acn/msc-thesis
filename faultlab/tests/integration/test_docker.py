import pytest

from faultlab.client.docker import DockerClient


@pytest.mark.setup
def test_hardhat_restart(docker_client: DockerClient):
    docker_client.restart_hardhat()


@pytest.mark.setup
def test_signers_db_cleanup(docker_client: DockerClient):
    docker_client.cleanup_signer_databases()
