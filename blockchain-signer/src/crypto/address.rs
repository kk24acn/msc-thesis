use sha3::{ Digest, Keccak256 };

pub trait AddressDerivationStrategy {
    fn derive(&self, public_key: &[u8]) -> Result<String, String>;
}

pub struct EthereumAddressDeriver;

impl AddressDerivationStrategy for EthereumAddressDeriver {
    fn derive(&self, public_key: &[u8]) -> Result<String, String> {
        if public_key.len() != 65 || public_key[0] != 0x04 {
            return Err("Invalid public key format".into());
        }

        let mut hasher = Keccak256::new();
        // First byte is format identifier, drop it
        hasher.update(&public_key[1..]);
        let hash = hasher.finalize();

        // ETH address is 20 rightmost bytes from hash
        let address_bytes = &hash[12..];
        Ok(format!("0x{}", hex::encode(address_bytes)))
    }
}
