import pytest
import logging

from faultlab.client.signer.dkg_client import DkgClient

logger = logging.getLogger(__name__)


@pytest.mark.setup
def test_dkg_setup(dkg_client: DkgClient):
    session_id = "test-single-session-1"
    threshold = 2
    total_parties = 3

    eth_address = dkg_client.setup_key(session_id=session_id, threshold=threshold, total_parties=total_parties)

    assert eth_address is not None, "DKG session failed to generate address"
    assert eth_address.startswith("0x"), "Invalid Ethereum address format"
    assert len(eth_address) == 42, "Ethereum address should be 42 characters (0x + 40 hex)"

    logger.info(f"DKG session successful. Generated address: {eth_address}")


@pytest.mark.setup
def test_dkg_setup_multi(dkg_client: DkgClient):
    num_sessions = 10
    threshold = 2
    total_parties = 3

    successful = dkg_client.setup_keys(num_sessions=num_sessions, threshold=threshold, total_parties=total_parties)
    assert successful == num_sessions, f"DKG setup incomplete: {successful}/{num_sessions} sessions successful"
