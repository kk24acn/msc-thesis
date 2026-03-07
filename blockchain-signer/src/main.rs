use std::error::Error;
use std::str::FromStr;

use clap::Parser;
use ethers::core::types::TransactionRequest as EthersTransactionRequest;
use ethers::core::types::{ U256, U64, Bytes, H160 };
use ethers::signers::{ LocalWallet, Signer };
use ethers::types::{ NameOrAddress };
use ethers::utils::keccak256;
use log::info;
use rustls::crypto::CryptoProvider;
use rustls::crypto::ring::default_provider;
use tonic::{ Request, Response, Status };
use tonic::transport::{ Server, Identity, Certificate, server::ServerTlsConfig };
use signer::signer_server::{ Signer as GrpcSigner, SignerServer };
use signer::{ TransactionRequest, TransactionResponse };
use std::fs;

pub mod signer {
    tonic::include_proto!("signer");
}

#[derive(Parser, Debug)]
pub struct Args {
    #[arg(long, default_value = "certs/signer.crt")]
    cert: String,

    #[arg(long, default_value = "certs/signer.key")]
    key: String,

    #[arg(long, default_value = "certs/ca.crt")]
    ca: String,

    #[arg(long, default_value = "127.0.0.1:50051")]
    addr: String,
}

#[derive(Debug, Default)]
pub struct TransactionSigner {}

fn hash(request: TransactionRequest) -> EthersTransactionRequest {
    let gas_price = U256::from_str_radix(&request.gas_price, 10).expect("Invalid gas_price value");
    let value_val = U256::from_str_radix(&request.value, 10).expect("Invalid value");
    let nonce_val = U256::from_str_radix(&request.nonce, 10).expect("Invalid nonce");

    let tx = EthersTransactionRequest {
        from: Some(H160::from_str(request.from.as_str()).expect("Invalid `from` address")),
        to: Some(
            NameOrAddress::from(H160::from_str(request.to.as_str()).expect("Invalid `to` address"))
        ),
        gas: Some(U256::from(21000)), // TODO Use default now, add modification possibility
        gas_price: Some(gas_price),
        value: Some(value_val),
        data: Some(Bytes::from(request.data)),
        nonce: Some(nonce_val),
        chain_id: Some(U64::from(request.chain_id)),
    };

    tx
}

#[tonic::async_trait]
impl GrpcSigner for TransactionSigner {
    async fn sign_transaction(
        &self,
        request: Request<TransactionRequest>
    ) -> Result<Response<TransactionResponse>, Status> {
        let transaction = request.into_inner();
        let chain_id = transaction.chain_id.clone();
        let tx_req = hash(transaction);

        let private_key = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
        let wallet: LocalWallet = private_key
            .parse::<LocalWallet>()
            .map_err(|e| Status::internal(format!("Failed to parse private key: {}", e)))?
            .with_chain_id(chain_id);

        // Sign the transaction to produce the raw bytes
        let signature = wallet
            .sign_transaction(&tx_req.clone().into()).await
            .map_err(|e| tonic::Status::internal(format!("Failed to sign transaction: {}", e)))?;

        let raw_tx = tx_req.clone().rlp_signed(&signature);
        let tx_hash = keccak256(&raw_tx);

        info!("Raw Signed Transaction: 0x{}", hex::encode(&raw_tx));
        info!("Transaction Hash (Keccak256): 0x{}", hex::encode(tx_hash));

        Ok(
            Response::new(TransactionResponse {
                raw_tx_hex: format!("0x{}", hex::encode(&raw_tx)),
                tx_hash_hex: format!("0x{}", hex::encode(tx_hash)),
            })
        )
    }
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    env_logger::init();

    CryptoProvider::install_default(default_provider()).ok();

    let args = Args::parse();

    let cert = fs::read(&args.cert)?;
    let key = fs::read(&args.key)?;
    let server_identity = Identity::from_pem(cert, key);

    let ca_cert = fs::read(&args.ca)?;
    let client_ca = Certificate::from_pem(ca_cert);
    let tls_config = ServerTlsConfig::new().identity(server_identity).client_ca_root(client_ca);

    let addr = args.addr.parse()?;

    info!("Server listening on {}", addr);

    Server::builder()
        .tls_config(tls_config)?
        .add_service(SignerServer::new(TransactionSigner::default()))
        .serve(addr).await?;

    Ok(())
}
