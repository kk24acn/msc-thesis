import { useMemo, useState } from 'react';
import Sidebar from '../components/common/Sidebar';
import Header from '../components/common/Header';
import StatsCards from '../components/accounts/StatsCards';
import FilterActions from '../components/accounts/FilterActions';
import AccountsGrid from '../components/accounts/AccountsGrid';
import Toast from '../components/common/Toast';
import { useWeb3 } from '../context/Web3Context';

const transformSignerToAccount = (signer, index) => ({
    id: index + 1,
    address: signer.address,
    keyId: signer.keyId || `Account #${index + 1}`,
    balance: Number(signer.balance || 0),
    nonce: signer.nonce ?? 0,
});

const Accounts = () => {
    const { signers, funderAccounts, error } = useWeb3();

    const [search, setSearch] = useState('');
    const [toast, setToast] = useState(null);

    const accounts = useMemo(() => {
        return signers.map(transformSignerToAccount);
    }, [signers]);

    const filteredAccounts = useMemo(() => {
        const query = search.toLowerCase();

        return accounts.filter(
            (acc) =>
                acc.address?.toString().toLowerCase().includes(query) ||
                acc.keyId?.toString().toLowerCase().includes(query),
        );
    }, [accounts, search]);

    const copyAddress = async (address) => {
        try {
            await navigator.clipboard.writeText(address);
            setToast({ title: 'Copied to clipboard', message: address });
        } catch (err) {
            setToast({ title: 'Copy failed', message: 'Clipboard not available' });
        }
        setTimeout(() => setToast(null), 3000);
    };

    return (
        <div className="page-shell">
            <Sidebar />
            <main className="main-content">
                <Header title="Accounts" />

                <div className="p-6">
                    {error ? (
                        <div className="alert-error">
                            Failed to connect to Hardhat RPC at http://localhost:8545. Start the node and refresh.
                        </div>
                    ) : null}

                    <StatsCards accounts={accounts} funderAccounts={funderAccounts} />
                    <FilterActions
                        count={filteredAccounts.length}
                        search={search}
                        onSearchChange={setSearch}
                    />
                    <AccountsGrid
                        accounts={filteredAccounts}
                        onCopy={copyAddress}
                    />
                </div>
            </main>
            <Toast toast={toast} />
        </div>
    );
};

export default Accounts;