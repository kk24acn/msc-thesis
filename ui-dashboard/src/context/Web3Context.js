import { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react';
import { ethers } from 'ethers';
import { usePolling } from './PollingContext';

const Web3Context = createContext();

export const Web3Provider = ({ children }) => {
    const [provider, setProvider] = useState(null);
    const [signers, setSigners] = useState([]);
    const [mpcKeyMetadata, setMpcKeyMetadata] = useState({});
    const [funderAccounts, setFunderAccounts] = useState([]);
    const [error, setError] = useState(null);

    useEffect(() => {
        try {
            const baseUrl = process.env.REACT_APP_HARDHAT_RPC_URL || 'http://localhost:8545';
            const hardhatProvider = new ethers.JsonRpcProvider(baseUrl);
            setProvider(hardhatProvider);
        } catch (err) {
            console.error("Failed to initialize Web3 Provider:", err);
            setError(err.message);
        }
    }, []);

    const fetchBlockchainData = useCallback(async () => {
        if (!provider) return;

        try {
            const postgrestUrl = process.env.REACT_APP_POSTGREST_URL || 'http://localhost:3001';
            const mpcKeysResponse = await fetch(`${postgrestUrl}/mpc_keys`);

            if (!mpcKeysResponse.ok) {
                throw new Error(`Failed to fetch MPC keys: ${mpcKeysResponse.statusText}`);
            }

            const mpcKeys = await mpcKeysResponse.json();
            const addressList = mpcKeys.map(key => key.ethereum_address);

            const metadata = {};
            mpcKeys.forEach(key => {
                metadata[key.ethereum_address] = {
                    keyId: key.key_id,
                    derivationPath: key.derivation_path,
                    threshold: key.threshold,
                    totalParties: key.total_parties,
                };
            });
            setMpcKeyMetadata(metadata);

            const signersBalances = await Promise.all(
                addressList.map(async (address) => {
                    const [balance, nonce] = await Promise.all([
                        provider.getBalance(address),
                        provider.getTransactionCount(address),
                    ]);
                    return {
                        address,
                        balance: ethers.formatEther(balance),
                        nonce,
                        ...metadata[address]
                    };
                })
            );
            setSigners(signersBalances);

            const allAccounts = await provider.listAccounts();
            const mpcAddresses = new Set(addressList.map(a => a.toLowerCase()));
            const funderList = allAccounts.filter(signer => {
                const addr = typeof signer === 'string' ? signer : signer.address;
                return !mpcAddresses.has(addr.toLowerCase());
            });

            const fundersBalances = await Promise.all(
                funderList.map(async (signer) => {
                    const addr = typeof signer === 'string' ? signer : signer.address;
                    const balance = await provider.getBalance(addr);
                    return { address: addr, balance: ethers.formatEther(balance) };
                })
            );
            setFunderAccounts(fundersBalances);

            setError(null);
        } catch (err) {
            console.error('Failed to fetch blockchain data:', err);
            setError(err.message);
        }
    }, [provider]);

    const fetchRef = useRef();
    const intervalRef = useRef();
    const { isPolling } = usePolling();

    useEffect(() => {
        fetchRef.current = fetchBlockchainData;
    }, [fetchBlockchainData]);

    useEffect(() => {
        if (!provider) return;

        fetchRef.current();

        if (isPolling) {
            intervalRef.current = setInterval(() => {
                if (fetchRef.current) {
                    fetchRef.current();
                }
            }, 2000);
        }

        return () => {
            if (intervalRef.current) {
                clearInterval(intervalRef.current);
            }
        };
    }, [provider, isPolling]);

    return (
        <Web3Context.Provider value={{ provider, signers, mpcKeyMetadata, funderAccounts, error, refresh: fetchBlockchainData }}>
            {children}
        </Web3Context.Provider>
    );
};

export const useWeb3 = () => useContext(Web3Context);