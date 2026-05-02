use crate::crypto::address::{ AddressDerivationStrategy, EthereumAddressDeriver };
use log::debug;
use crate::crypto::dkls23::{ DkgStateWrapper, Dkls23Engine, MpcSessionState };
use crate::storage::KeyshareStore;
use dashmap::DashMap;
use derivation_path::DerivationPath;
use dkls23_ll::dkg::Keyshare;
use dkls23_ll::dsg::derive_with_offset;
use k256::ProjectivePoint;
use k256::elliptic_curve::sec1::ToEncodedPoint;
use std::str::FromStr;
use std::sync::Arc;
use tonic::{ Request, Response, Status };

pub mod mpc_proto {
    tonic::include_proto!("mpc.signer");
}

use mpc_proto::dkg_service_server::DkgService;
use mpc_proto::{ AdvanceDkgRequest, AdvanceDkgResponse, InitDkgRequest, InitDkgResponse };
use mpc_proto::dsg_service_server::DsgService;
use mpc_proto::{ AdvanceDsgRequest, AdvanceDsgResponse, InitDsgRequest, InitDsgResponse };

#[derive(Clone)]
pub struct MpcNodeService {
    dkg_sessions: Arc<DashMap<String, Dkls23Engine>>,
    dsg_sessions: Arc<DashMap<String, Dkls23Engine>>,
    storage: KeyshareStore,
}

impl MpcNodeService {
    pub fn new(storage: KeyshareStore) -> Self {
        Self {
            dkg_sessions: Arc::new(DashMap::new()),
            dsg_sessions: Arc::new(DashMap::new()),
            storage,
        }
    }
}

fn session_key(session_id: &str, party_id: u32) -> String {
    format!("{}-{}", session_id, party_id)
}

fn derive_ethereum_address(keyshare: &Keyshare, path_str: &str) -> Result<String, Status> {
    let path = DerivationPath::from_str(path_str).map_err(|e|
        Status::internal(format!("Invalid derivation path: {}", e))
    )?;

    let (_, derived_pub) = derive_with_offset(
        &ProjectivePoint::from(keyshare.public_key),
        &keyshare.root_chain_code,
        &path
    ).map_err(|e| Status::internal(format!("Derivation error: {:?}", e)))?;

    let pub_key_bytes = derived_pub.to_encoded_point(false).as_bytes().to_vec();
    EthereumAddressDeriver.derive(&pub_key_bytes).map_err(|e|
        Status::internal(format!("Address derivation error: {}", e))
    )
}

#[tonic::async_trait]
impl DkgService for MpcNodeService {
    async fn init_dkg(
        &self,
        request: Request<InitDkgRequest>
    ) -> Result<Response<InitDkgResponse>, Status> {
        let req = request.into_inner();
        debug!(
            "InitDkg request: session_id={}, party_id={}, threshold={}, total_parties={}, derivation_path={}",
            req.session_id,
            req.party_id,
            req.threshold,
            req.total_parties,
            req.derivation_path
        );
        let key = session_key(&req.session_id, req.party_id);

        match self.storage.exists(&key) {
            Ok(true) => {
                return Err(
                    Status::already_exists(
                        format!("Keyshare for session {} already exists", req.session_id)
                    )
                );
            }
            Ok(false) => (),
            Err(e) => {
                return Err(Status::internal(e.to_string()));
            }
        }

        let engine = Dkls23Engine::new_dkg(
            req.session_id.clone(),
            req.party_id as u8,
            req.threshold as u8,
            req.total_parties as u8,
            &req.derivation_path
        );

        self.dkg_sessions.insert(key, engine);
        Ok(Response::new(InitDkgResponse { success: true }))
    }

    async fn advance_dkg(
        &self,
        request: Request<AdvanceDkgRequest>
    ) -> Result<Response<AdvanceDkgResponse>, Status> {
        let req = request.into_inner();
        debug!(
            "AdvanceDkg request: session_id={}, party_id={}, payloads=[{}]",
            req.session_id,
            req.party_id,
            req.payloads
                .iter()
                .map(|p| format!("{} bytes", p.len()))
                .collect::<Vec<_>>()
                .join(", ")
        );
        let key = session_key(&req.session_id, req.party_id);

        let process_result = {
            let mut engine_ref = self.dkg_sessions
                .get_mut(&key)
                .ok_or_else(|| Status::not_found("Session not found"))?;

            match engine_ref.advance_dkg(&req.payloads) {
                Ok((out, done)) => {
                    let mut ks_data = None;
                    let mut path_data = None;

                    if done {
                        if
                            let MpcSessionState::Dkg(DkgStateWrapper::Completed(ks)) =
                                &engine_ref.state
                        {
                            ks_data = Some(ks.clone());
                            path_data = engine_ref.derivation_path.clone();
                        }
                    }
                    Ok((out, done, ks_data, path_data))
                }
                Err(e) => Err(Status::internal(e.to_string())),
            }
        };

        match process_result {
            Ok((output, is_done, keyshare_data, derivation_path)) => {
                if is_done {
                    self.dkg_sessions.remove(&key);

                    let (keyshare, path_str) = match (keyshare_data, derivation_path) {
                        (Some(ks), Some(path)) => (ks, path),
                        _ => {
                            return Err(
                                Status::internal("Engine finished but missing keyshare or path")
                            );
                        }
                    };

                    let serialized_ks = bincode
                        ::serialize(&keyshare)
                        .map_err(|e| Status::internal(e.to_string()))?;

                    self.storage
                        .save_keyshare(&key, &serialized_ks)
                        .map_err(|e| Status::internal(e.to_string()))?;

                    return Ok(
                        Response::new(AdvanceDkgResponse {
                            output: derive_ethereum_address(&keyshare, &path_str)?.into_bytes(),
                            is_done,
                        })
                    );
                }

                Ok(Response::new(AdvanceDkgResponse { output, is_done }))
            }
            Err(err) => {
                self.dkg_sessions.remove(&key);
                Err(err)
            }
        }
    }
}

#[tonic::async_trait]
impl DsgService for MpcNodeService {
    async fn init_dsg(
        &self,
        request: Request<InitDsgRequest>
    ) -> Result<Response<InitDsgResponse>, Status> {
        let req = request.into_inner();
        debug!(
            "InitDsg request: key_id={}, dsg_session_id={}, party_id={}, derivation_path={}, message_hash={}",
            req.key_id,
            req.dsg_session_id,
            req.party_id,
            req.derivation_path,
            req.message_hash
                .iter()
                .map(|b| format!("{:02x}", b))
                .collect::<String>()
        );
        let dkg_key = session_key(&req.key_id, req.party_id);
        let dsg_key = session_key(&req.dsg_session_id, req.party_id);

        let keyshare_bytes = self.storage
            .get_keyshare(&dkg_key)
            .map_err(|e| Status::internal(e.to_string()))?
            .ok_or_else(|| {
                Status::not_found(format!("Keyshare not found for key_id: {}", req.key_id))
            })?;

        let keyshare: Keyshare = bincode
            ::deserialize(&keyshare_bytes)
            .map_err(|e| Status::internal(format!("Failed to deserialize keyshare: {}", e)))?;

        let engine = Dkls23Engine::new_dsg(
            req.dsg_session_id.clone(),
            keyshare,
            &req.derivation_path,
            req.message_hash
        ).map_err(|e| Status::internal(e.to_string()))?;

        self.dsg_sessions.insert(dsg_key, engine);
        Ok(Response::new(InitDsgResponse { success: true }))
    }

    async fn advance_dsg(
        &self,
        request: Request<AdvanceDsgRequest>
    ) -> Result<Response<AdvanceDsgResponse>, Status> {
        let req = request.into_inner();
        debug!(
            "AdvanceDsg request: dsg_session_id={}, party_id={}, payloads=[{}]",
            req.dsg_session_id,
            req.party_id,
            req.payloads
                .iter()
                .map(|p| format!("{} bytes", p.len()))
                .collect::<Vec<_>>()
                .join(", ")
        );
        let key = session_key(&req.dsg_session_id, req.party_id);

        let result = {
            let mut session = self.dsg_sessions
                .get_mut(&key)
                .ok_or_else(|| Status::not_found("DSG Session not found"))?;
            session.advance_dsg(&req.payloads)
        };

        match result {
            Ok((output, is_done)) => {
                if is_done {
                    self.dsg_sessions.remove(&key);
                }
                Ok(Response::new(AdvanceDsgResponse { output, is_done }))
            }
            Err(e) => {
                self.dsg_sessions.remove(&key);
                Err(Status::internal(e.to_string()))
            }
        }
    }
}
