use derivation_path::DerivationPath;
use dkls23_ll::{ dkg, dsg };
use k256::elliptic_curve::sec1::ToEncodedPoint;
use log::{ debug, error, info };
use rand::rngs::OsRng;
use std::str::FromStr;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum EngineError {
    #[error("Serialization error: {0}")] Serialization(#[from] bincode::Error),
    #[error("Invalid state transition for session")] InvalidStateTransition,
    #[error("Cryptographic operation failed: {0}")] CryptoFault(String),
    #[error("Derivation path error: {0}")] PathError(String),
    #[error("Missing or invalid message hash")] InvalidHash,
}

pub enum DkgStateWrapper {
    Phase1(dkg::State),
    Phase2(dkg::State),
    Phase3(dkg::State),
    Phase4(dkg::State),
    Phase5(dkg::State),
    Completed(dkg::Keyshare),
    Failed,
}

pub enum DsgStateWrapper {
    Phase1(dsg::State),
    Phase2(dsg::State),
    Phase3(dsg::State),
    Phase4(dsg::State),
    Completed,
    Failed,
}

pub enum DsgRoundOutput {
    Intermediate(Vec<u8>),
    Final {
        s_0: Vec<u8>,
        s_1: Vec<u8>,
        r: Vec<u8>,
    },
}

pub enum MpcSessionState {
    Dkg(DkgStateWrapper),
    Dsg(DsgStateWrapper),
}

pub struct Dkls23Engine {
    pub session_id: String,
    pub party_id: u8,
    pub state: MpcSessionState,
    pub message_hash: Option<Vec<u8>>,
    pub derivation_path: Option<String>,
}

impl Dkls23Engine {
    pub fn new_dkg(
        session_id: String,
        party_id: u8,
        threshold: u8,
        total_parties: u8,
        derivation_path: &str
    ) -> Self {
        info!("Initializing DKG Session: {} for Party: {}", session_id, party_id);
        let ranks: Vec<u8> = vec![0; total_parties as usize];
        let party = dkg::Party { ranks, t: threshold, party_id };

        Self {
            session_id,
            party_id,
            state: MpcSessionState::Dkg(
                DkgStateWrapper::Phase1(dkg::State::new(party, &mut OsRng))
            ),
            message_hash: None,
            derivation_path: Some(derivation_path.to_string()),
        }
    }

    pub fn new_dsg(
        session_id: String,
        keyshare: dkg::Keyshare,
        derivation_path: &str,
        message_hash: Vec<u8>
    ) -> Result<Self, EngineError> {
        info!("Initializing DSG Session: {} for Party: {}", session_id, keyshare.party_id);
        let party_id = keyshare.party_id;
        let path = DerivationPath::from_str(derivation_path).map_err(|e|
            EngineError::PathError(e.to_string())
        )?;

        let state = dsg::State
            ::new(&mut OsRng, keyshare, &path)
            .map_err(|e| EngineError::CryptoFault(e.to_string()))?;

        Ok(Self {
            session_id,
            party_id,
            state: MpcSessionState::Dsg(DsgStateWrapper::Phase1(state)),
            message_hash: Some(message_hash),
            derivation_path: Some(derivation_path.to_string()),
        })
    }

    pub fn advance_dkg(&mut self, payloads: &[Vec<u8>]) -> Result<(Vec<u8>, bool), EngineError> {
        let current_state = std::mem::replace(
            &mut self.state,
            MpcSessionState::Dkg(DkgStateWrapper::Failed)
        );

        match current_state {
            MpcSessionState::Dkg(DkgStateWrapper::Phase1(state)) => {
                // Generate random secret and prepare commitment (curve point)
                debug!("DKG Session {}: Advancing Phase 1 -> Phase 2", self.session_id);
                let msg1 = state.generate_msg1();
                let output = bincode::serialize(&msg1)?;
                self.state = MpcSessionState::Dkg(DkgStateWrapper::Phase2(state));
                Ok((output, false))
            }
            MpcSessionState::Dkg(DkgStateWrapper::Phase2(mut state)) => {
                // Prepare key fragments for each other node based on their commitment and owned secret
                debug!("DKG Session {}: Advancing Phase 2 -> Phase 3", self.session_id);

                let filtered_msgs: Vec<dkg::KeygenMsg1> = payloads
                    .iter()
                    .filter(|p| !p.is_empty())
                    .map(|p| bincode::deserialize::<dkg::KeygenMsg1>(p))
                    .collect::<Result<Vec<_>, _>>()?
                    .into_iter()
                    .filter(|m| m.from_id != self.party_id)
                    .collect();

                let msg2 = state
                    .handle_msg1(&mut OsRng, filtered_msgs)
                    .map_err(|e| EngineError::CryptoFault(e.to_string()))?;

                let output = bincode::serialize(&msg2)?;
                self.state = MpcSessionState::Dkg(DkgStateWrapper::Phase3(state));
                Ok((output, false))
            }
            MpcSessionState::Dkg(DkgStateWrapper::Phase3(mut state)) => {
                // Verify key fragments prepared for this node using Phase 1 commitments, generate second commitment (hash)
                debug!("DKG Session {}: Advancing Phase 3 -> Phase 4", self.session_id);

                let filtered_msgs: Vec<dkg::KeygenMsg2> = payloads
                    .iter()
                    .filter(|p| !p.is_empty())
                    .map(|p| bincode::deserialize::<Vec<dkg::KeygenMsg2>>(p))
                    .collect::<Result<Vec<_>, _>>()?
                    .into_iter()
                    .flatten()
                    .filter(|m| m.to_id == self.party_id)
                    .collect();

                let msg3 = state
                    .handle_msg2(&mut OsRng, filtered_msgs)
                    .map_err(|e| EngineError::CryptoFault(e.to_string()))?;

                let commitment = state.calculate_commitment_2();
                let output = bincode::serialize(&(msg3, commitment))?;

                self.state = MpcSessionState::Dkg(DkgStateWrapper::Phase4(state));
                Ok((output, false))
            }
            MpcSessionState::Dkg(DkgStateWrapper::Phase4(mut state)) => {
                // Verify commitments from Phase 3 and calculate a Zero-Knowledge (Schnorr) Proof
                debug!("DKG Session {}: Advancing Phase 4 -> Phase 5", self.session_id);

                let mut inbound = Vec::new();
                let mut commitments = Vec::new();

                for p in payloads.iter().filter(|p| !p.is_empty()) {
                    let (msgs, comm): (Vec<dkg::KeygenMsg3>, [u8; 32]) = bincode::deserialize(p)?;
                    inbound.extend(msgs);
                    commitments.push(comm);
                }

                let filtered_msgs: Vec<_> = inbound
                    .into_iter()
                    .filter(|m| m.to_id == self.party_id)
                    .collect();

                let msg4 = state
                    .handle_msg3(&mut OsRng, filtered_msgs, &commitments)
                    .map_err(|e| EngineError::CryptoFault(e.to_string()))?;

                let output = bincode::serialize(&msg4)?;
                self.state = MpcSessionState::Dkg(DkgStateWrapper::Phase5(state));
                Ok((output, false))
            }
            MpcSessionState::Dkg(DkgStateWrapper::Phase5(mut state)) => {
                // Verify proofs from other peers and calculate the final keyshare
                debug!("DKG Session {}: Advancing Phase 5 -> Completed", self.session_id);

                let filtered_msgs: Vec<dkg::KeygenMsg4> = payloads
                    .iter()
                    .filter(|p| !p.is_empty())
                    .map(|p| bincode::deserialize::<dkg::KeygenMsg4>(p))
                    .collect::<Result<Vec<_>, _>>()?
                    .into_iter()
                    .filter(|m| m.from_id != self.party_id)
                    .collect();

                let keyshare = state.handle_msg4(filtered_msgs).map_err(|e| {
                    error!("DKG Session {}: Finalization failed - {}", self.session_id, e);
                    EngineError::CryptoFault(e.to_string())
                })?;

                info!("DKG Session {}: Successfully completed", self.session_id);
                self.state = MpcSessionState::Dkg(DkgStateWrapper::Completed(keyshare));
                Ok((vec![], true))
            }
            _ => {
                error!("DKG Session {}: Invalid state transition", self.session_id);
                Err(EngineError::InvalidStateTransition)
            }
        }
    }

    pub fn advance_dsg(&mut self, payloads: &[Vec<u8>]) -> Result<DsgRoundOutput, EngineError> {
        let current_state = std::mem::replace(
            &mut self.state,
            MpcSessionState::Dsg(DsgStateWrapper::Failed)
        );

        match current_state {
            MpcSessionState::Dsg(DsgStateWrapper::Phase1(mut state)) => {
                // Generate random secret (nonce) and prepare commitment (hashed public nonce mixed with blinding factor)
                debug!("DSG Session {}: Advancing Phase 1 | Init", self.session_id);

                let msg1 = state.generate_msg1();
                let output = bincode::serialize(&msg1)?;
                self.state = MpcSessionState::Dsg(DsgStateWrapper::Phase2(state));
                Ok(DsgRoundOutput::Intermediate(output))
            }
            MpcSessionState::Dsg(DsgStateWrapper::Phase2(mut state)) => {
                // Prepare MtA setup params for each peer using Phase 1 commitments
                debug!("DSG Session {}: Advancing Phase 2 | Prep MtA", self.session_id);

                let filtered_msgs: Vec<dsg::SignMsg1> = payloads
                    .iter()
                    .filter(|p| !p.is_empty())
                    .map(|p| bincode::deserialize::<dsg::SignMsg1>(p))
                    .collect::<Result<Vec<_>, _>>()?
                    .into_iter()
                    .filter(|m| m.from_id != state.keyshare.party_id)
                    .collect();

                let msg2 = state
                    .handle_msg1(&mut OsRng, filtered_msgs)
                    .map_err(|e| EngineError::CryptoFault(e.to_string()))?;

                let output = bincode::serialize(&msg2)?;
                self.state = MpcSessionState::Dsg(DsgStateWrapper::Phase3(state));
                Ok(DsgRoundOutput::Intermediate(output))
            }
            MpcSessionState::Dsg(DsgStateWrapper::Phase3(mut state)) => {
                // Complete the MtA conversion using setup params for this node, reveal public nonce and blinding factor
                debug!("DSG Session {}: Advancing Phase 3 | Complete MtA", self.session_id);

                let filtered_msgs: Vec<dsg::SignMsg2> = payloads
                    .iter()
                    .filter(|p| !p.is_empty())
                    .map(|p| bincode::deserialize::<Vec<dsg::SignMsg2>>(p))
                    .collect::<Result<Vec<_>, _>>()?
                    .into_iter()
                    .flatten()
                    .filter(|m| m.to_id == state.keyshare.party_id)
                    .collect();

                let msg3 = state
                    .handle_msg2(&mut OsRng, filtered_msgs)
                    .map_err(|e| EngineError::CryptoFault(e.to_string()))?;

                let output = bincode::serialize(&msg3)?;
                self.state = MpcSessionState::Dsg(DsgStateWrapper::Phase4(state));
                Ok(DsgRoundOutput::Intermediate(output))
            }
            MpcSessionState::Dsg(DsgStateWrapper::Phase4(mut state)) => {
                // Verify nonces with Phase 1 commitments, prepare partial signature
                debug!("DSG Session {}: Advancing Phase 4 | Finalize", self.session_id);

                let filtered_msgs: Vec<dsg::SignMsg3> = payloads
                    .iter()
                    .filter(|p| !p.is_empty())
                    .map(|p| bincode::deserialize::<Vec<dsg::SignMsg3>>(p))
                    .collect::<Result<Vec<_>, _>>()?
                    .into_iter()
                    .flatten()
                    .filter(|m| m.to_id == state.keyshare.party_id)
                    .collect();

                let presig = state
                    .handle_msg3(filtered_msgs)
                    .map_err(|e| EngineError::CryptoFault(e.to_string()))?;

                let message_hash_arr: [u8; 32] = self.message_hash
                    .as_ref()
                    .ok_or(EngineError::InvalidHash)?
                    .as_slice()
                    .try_into()
                    .map_err(|_| EngineError::InvalidHash)?;

                let (partial_sig, _) = dsg::create_partial_signature(presig, message_hash_arr);

                let s_0 = partial_sig.s_0.to_bytes().to_vec();
                let s_1 = partial_sig.s_1.to_bytes().to_vec();
                let r = partial_sig.r.to_encoded_point(false).as_bytes().to_vec();

                info!("DSG Session {}: Successfully completed", self.session_id);
                self.state = MpcSessionState::Dsg(DsgStateWrapper::Completed);

                Ok(DsgRoundOutput::Final { s_0, s_1, r })
            }
            _ => {
                error!("DSG Session {}: Invalid state transition", self.session_id);
                Err(EngineError::InvalidStateTransition)
            }
        }
    }
}
