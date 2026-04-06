use clap::Parser;
use tonic::transport::Server;
use grpc::mpc_proto::dkg_service_server::DkgServiceServer;
use grpc::mpc_proto::dsg_service_server::DsgServiceServer;
use grpc::MpcNodeService;
use storage::KeyshareStore;
use env_logger;
use log::info;

mod crypto;
mod grpc;
mod storage;

#[derive(Parser, Debug)]
pub struct Args {
    #[arg(long, default_value = "certs/signer.crt")]
    cert: String,

    #[arg(long, default_value = "certs/signer.key")]
    key: String,

    #[arg(long, default_value = "127.0.0.1:50051")]
    addr: String,

    #[arg(long, default_value = "1")]
    party_id: u16,

    #[arg(long, default_value = "keyshares.redb")]
    db_path: String,
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    env_logger::init();

    let args = Args::parse();
    let addr = args.addr.parse()?;

    let storage = KeyshareStore::new(&args.db_path)?;
    let mpc_service = MpcNodeService::new(storage);

    info!(
        "Starting MPC Node (party-id: {}) on {} using DB {}",
        args.party_id,
        args.addr,
        args.db_path
    );

    Server::builder()
        .add_service(DkgServiceServer::new(mpc_service.clone()))
        .add_service(DsgServiceServer::new(mpc_service.clone()))
        .serve(addr).await?;

    Ok(())
}
