import asyncio
import grpc
import logging
from typing import cast
from faultlab.proto import dkg_pb2, dkg_pb2_grpc
from faultlab.db import MpcKeysRepository

logger = logging.getLogger(__name__)


class DkgClientError(Exception):
    """Raised when DKG operations fail."""

    pass


class DkgClient:
    def __init__(self, signer_grpc_urls: list[str], db_dsn: str, derivation_path: str):
        def create_stubs() -> dict[int, dkg_pb2_grpc.DkgServiceStub]:
            stubs = {}
            for party_id, address in enumerate(signer_grpc_urls):
                try:
                    channel = grpc.insecure_channel(address)
                    stubs[party_id] = dkg_pb2_grpc.DkgServiceStub(channel)
                    logger.debug(f"Connected to signer {party_id} at {address}")
                except Exception as e:
                    raise DkgClientError(f"Failed to connect to signer {party_id} at {address}: {e}") from e
            return stubs

        self.mpc_keys_repository = MpcKeysRepository(db_dsn)
        self.signer_stubs = create_stubs()
        self.derivation_path = derivation_path

    def _process_dkg(self, session_id: str, threshold: int, total_parties: int) -> str:
        def advance_dkg(session_id: str, total_parties: int, input_payloads: list, round_num: int) -> list:
            outputs = []
            for party_id in range(total_parties):
                try:
                    req = dkg_pb2.AdvanceDkgRequest(session_id=session_id, party_id=party_id, payloads=input_payloads)
                    res = self.signer_stubs[party_id].AdvanceDkg(req)
                    logger.debug(f"DKG {session_id}: Round {round_num}, party {party_id}, is_done={res.is_done}") # fmt: skip
                    outputs.append(res.output)
                except Exception as e:
                    raise DkgClientError(f"DKG {session_id}: Round {round_num}, party {party_id} failed: {e}") from e

            logger.debug(f"DKG {session_id}: Round {round_num} completed")
            return outputs

        def finalize_dkg(session_id: str, total_parties: int, final_outputs: list) -> str:
            logger.debug(f"DKG {session_id}: Checking completion with {len(final_outputs)} outputs")

            is_done_states = []
            eth_address = None

            for party_id in range(total_parties):
                try:
                    req = dkg_pb2.AdvanceDkgRequest(session_id=session_id, party_id=party_id, payloads=final_outputs)
                    res = self.signer_stubs[party_id].AdvanceDkg(req)
                    is_done_states.append(res.is_done)

                    output_str = res.output.decode("utf-8") if res.output else None
                    logger.debug(f"DKG {session_id}: Party {party_id} - is_done={res.is_done}, output={output_str}")

                    if res.is_done and eth_address is None and res.output:
                        eth_address = str(output_str)
                except Exception as e:
                    raise DkgClientError(f"DKG {session_id}: Completion check failed for party {party_id}: {e}") from e

            logger.debug(f"DKG {session_id}: Completion states = {is_done_states} extracted_address = {eth_address}")
            if not all(is_done_states):
                raise DkgClientError(f"DKG {session_id}: Not all parties reported completion. States: {is_done_states}")
            if not eth_address:
                raise DkgClientError(f"DKG {session_id}: No address extracted from any party")
            return eth_address

        logger.debug(f"Starting DKG session {session_id} with threshold={threshold}, parties={total_parties}")

        for party_id in range(total_parties):
            init_req = dkg_pb2.InitDkgRequest(
                session_id=session_id,
                party_id=party_id,
                threshold=threshold,
                total_parties=total_parties,
                derivation_path=self.derivation_path,
            )
            self.signer_stubs[party_id].InitDkg(init_req)
        logger.debug(f"DKG {session_id}: Initialized all parties")

        outputs = []
        for round_num in range(1, 5):
            outputs = advance_dkg(session_id, total_parties, outputs, round_num)
            if not outputs:
                raise DkgClientError(f"DKG {session_id}: Round {round_num} failed")

        return finalize_dkg(session_id, total_parties, outputs)

    def setup_key(self, session_id: str, threshold: int, total_parties: int) -> str:
        if len(self.signer_stubs) < total_parties:
            raise DkgClientError(
                f"Not enough signer nodes connected. Expected {total_parties}, got {len(self.signer_stubs)}"
            )

        try:
            return self._process_dkg(session_id, threshold, total_parties)
        except Exception as e:
            raise DkgClientError(f"DKG session {session_id} failed: {e}") from e

    async def setup_keys(self, num_sessions: int, threshold: int, total_parties: int) -> int:
        logger.info(f"Initiating {num_sessions} DKG sessions with threshold={threshold}, total_parties={total_parties}") # fmt: skip

        tasks = [
            asyncio.to_thread(self.setup_key, f"dkg-session-{i}", threshold, total_parties) for i in range(num_sessions)
        ]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        successful_sessions = 0
        for i, result in enumerate(results):
            session_id = f"dkg-session-{i}"
            if isinstance(result, Exception):
                logger.error(f"DKG session {session_id} failed: {result}")
                continue

            try:
                eth_address = cast(str, result)
                self.mpc_keys_repository.save(session_id, eth_address, threshold, total_parties, self.derivation_path)
                successful_sessions += 1
                logger.debug(f"Stored key metadata for {session_id}")
            except Exception as e:
                logger.warning(f"Failed to store key metadata for {session_id}: {e}")

        logger.info(f"DKG setup complete: {successful_sessions}/{num_sessions} sessions successful")
        return successful_sessions
