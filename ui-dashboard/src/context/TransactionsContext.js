import { createContext, useContext, useEffect, useState, useRef, useCallback } from 'react';

const TransactionsContext = createContext();

const transformDbRecord = (dbTx) => {
    const statusUpper = dbTx.status?.toUpperCase() || '';
    const isConfirmed = statusUpper === 'CONFIRMED' || statusUpper === 'SUCCESS';
    const isFailed = statusUpper === 'FAILED' || statusUpper === 'ERROR';

    let uiStatus = 'new';
    if (isConfirmed) uiStatus = 'completed';
    else if (isFailed) uiStatus = 'failed';
    else uiStatus = 'running';

    const startTime = new Date(dbTx.created_at);
    const endTime = new Date(dbTx.updated_at);
    const durationMs = endTime - startTime;
    const durationStr = durationMs > 0 ? `${(durationMs / 1000).toFixed(1)}s` : '--';

    let progress = 0;
    if (statusUpper === 'SIGNING') progress = 20;
    if (statusUpper === 'SIGNED') progress = 40;
    if (statusUpper === 'SUBMITTING') progress = 60;
    if (statusUpper === 'SUBMITTED') progress = 80;
    if (isFailed || isConfirmed) progress = 100;

    const shortAddress = dbTx.to_address
        ? `${dbTx.to_address.substring(0, 6)}...${dbTx.to_address.substring(38)}`
        : 'Unknown';

    return {
        id: dbTx.id.split('-')[0],
        name: `Transfer to ${shortAddress}`,
        description: `${dbTx.amount_ether} ETH`,
        status: uiStatus,
        stages: [
            { name: 'New', status: 'completed' },
            {
                name: 'Signed',
                status: dbTx.signed_hex_payload ? 'completed' : (isFailed ? 'failed' : 'running'),
            },
            {
                name: 'Submitted',
                status: dbTx.transaction_hash ? 'completed' : (isFailed ? 'failed' : 'pending'),
            },
            {
                name: 'Confirmed',
                status: isConfirmed ? 'completed' : (isFailed ? 'failed' : 'pending'),
            },
        ],
        duration: durationStr,
        startTime: startTime,
        progress: progress,
        logs: dbTx.error_message
            ? [{ timestamp: endTime.toLocaleTimeString(), level: 'error', message: dbTx.error_message }]
            : [],
    };
};

export const TransactionsProvider = ({ children }) => {
    const [transactions, setTransactions] = useState([]);
    const [error, setError] = useState(null);

    const fetchRef = useRef();

    const fetchTransactions = useCallback(async () => {
        try {
            const baseUrl = process.env.REACT_APP_POSTGREST_URL || 'http://localhost:3001';
            const response = await fetch(`${baseUrl}/transactions?order=created_at.desc`);

            if (!response.ok) throw new Error('Network response was not ok');

            const data = await response.json();
            setTransactions(data.map(transformDbRecord));
            setError(null);
        } catch (err) {
            console.error('Failed to fetch transactions:', err);
            setError(err.message);
        }
    }, []);

    useEffect(() => {
        fetchRef.current = fetchTransactions;
    }, [fetchTransactions]);

    useEffect(() => {
        fetchRef.current();

        const interval = setInterval(() => {
            if (fetchRef.current) {
                fetchRef.current();
            }
        }, 2000);

        return () => clearInterval(interval);
    }, []);

    return (
        <TransactionsContext.Provider value={{ transactions, error, refresh: fetchTransactions }}>
            {children}
        </TransactionsContext.Provider>
    );
};

export const useTransactions = () => useContext(TransactionsContext);