import { createContext, useContext, useEffect, useState, useRef, useCallback } from 'react';
import { usePolling } from './PollingContext';

const TransactionsContext = createContext();

const formatDuration = (ms) => {
    if (ms === null || ms === undefined || isNaN(ms) || ms < 0) return null;
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(1)}s`;
};

const formatTimestamp = (date) => {
    if (!date) return '--';
    const h = String(date.getHours()).padStart(2, '0');
    const m = String(date.getMinutes()).padStart(2, '0');
    const s = String(date.getSeconds()).padStart(2, '0');
    const ms = String(date.getMilliseconds()).padStart(3, '0');
    return `${h}:${m}:${s}.${ms}`;
};

const transformDbRecord = (dbTx) => {
    const statusUpper = dbTx.status?.toUpperCase() || '';
    const isConfirmed = statusUpper === 'CONFIRMED' || statusUpper === 'SUCCESS';
    const isFailed = statusUpper === 'FAILED' || statusUpper === 'ERROR';
    const isCryptoAbort = statusUpper === 'CRYPTOGRAPHIC_ABORT';
    const isInMempool = statusUpper === 'IN_MEMPOOL';
    const isStalled = statusUpper === 'STALLED';

    let uiStatus = 'running';
    if (isConfirmed) uiStatus = 'completed';
    else if (isCryptoAbort) uiStatus = 'crypto_abort';
    else if (isFailed) uiStatus = 'failed';
    else if (isStalled) uiStatus = 'stalled';
    else if (isInMempool) uiStatus = 'in_mempool';
    else uiStatus = 'running';

    const startTime = new Date(dbTx.created_at);
    const signingStartedAt = dbTx.signing_started_at ? new Date(dbTx.signing_started_at) : null;
    const signedAt = dbTx.signed_at ? new Date(dbTx.signed_at) : null;
    const updatedAt = dbTx.updated_at ? new Date(dbTx.updated_at) : null;
    const submittedAt = dbTx.submitted_at ? new Date(dbTx.submitted_at) : null;
    const confirmedAt = dbTx.confirmed_at ? new Date(dbTx.confirmed_at) : null;
    const failedAt = dbTx.failed_at ? new Date(dbTx.failed_at) : null;

    const endTime = confirmedAt || failedAt || new Date(dbTx.updated_at);
    const durationMs = endTime - startTime;
    const durationStr = durationMs > 0 ? `${(durationMs / 1000).toFixed(1)}s` : '--';

    let progress = 0;
    if (statusUpper === 'SIGNING') progress = 20;
    if (statusUpper === 'SIGNED') progress = 40;
    if (statusUpper === 'SUBMITTING') progress = 60;
    if (isInMempool || isStalled) progress = 75;
    if (isCryptoAbort || isFailed || isConfirmed) progress = 100;

    const shortAddress = dbTx.to_address
        ? `${dbTx.to_address.substring(0, 6)}...${dbTx.to_address.substring(38)}`
        : 'Unknown';

    const waitTiming = signingStartedAt ? formatDuration(signingStartedAt - startTime) : null;
    const signedTiming = signedAt
        ? (signingStartedAt ? formatDuration(signedAt - signingStartedAt) : formatDuration(signedAt - startTime))
        : null;
    const submittedTiming = submittedAt && signedAt ? formatDuration(submittedAt - signedAt) : null;
    const confirmedTiming = confirmedAt && submittedAt ? formatDuration(confirmedAt - submittedAt) : null;

    const logs = [];

    logs.push({ timestamp: formatTimestamp(startTime), level: 'info', message: 'Transaction created' });

    if (signingStartedAt) {
        logs.push({ timestamp: formatTimestamp(signingStartedAt), level: 'info', message: 'Signing started' });
    }

    if (dbTx.signing_retries > 0) {
        if (signedAt) {
            logs.push({ timestamp: formatTimestamp(signedAt), level: 'warning', message: `Signing finished after ${dbTx.signing_retries} retries` });
        } else {
            logs.push({
                timestamp: formatTimestamp(updatedAt), level: 'error', message: `Signing failed after ${dbTx.signing_retries} retries`
            });
        }
    }

    if (signedAt) {
        logs.push({ timestamp: formatTimestamp(signedAt), level: 'info', message: 'Transaction signed' });
    }


    if (dbTx.submission_retries > 0) {
        if (submittedAt) {
            logs.push({ timestamp: formatTimestamp(submittedAt), level: 'warning', message: `Submission finished after ${dbTx.submission_retries} retries` });
        } else {
            logs.push({ timestamp: formatTimestamp(updatedAt), level: 'error', message: `Submission failed after ${dbTx.submission_retries} retries` });
        }
    }

    if (submittedAt) {
        logs.push({ timestamp: formatTimestamp(submittedAt), level: 'info', message: 'Transaction submitted to mempool' });
    }


    if (isStalled) {
        logs.push({ timestamp: formatTimestamp(new Date(dbTx.updated_at)), level: 'warning', message: 'Transaction stalled — queued in mempool awaiting transactions with smaller nonce' });
    }

    if (confirmedAt) {
        logs.push({ timestamp: formatTimestamp(confirmedAt), level: 'info', message: 'Transaction confirmed' });
    }

    if (dbTx.error_message && failedAt) {
        logs.push({ timestamp: formatTimestamp(failedAt), level: 'error', message: dbTx.error_message });
    }

    return {
        id: dbTx.id.split('-')[0],
        traceId: dbTx.trace_id || null,
        name: `Transfer to ${shortAddress}`,
        description: `${dbTx.amount_ether} ETH`,
        status: uiStatus,
        toAddress: dbTx.to_address || null,
        fromAddress: dbTx.from_address || null,
        nonce: dbTx.nonce ?? null,
        submissionBlock: dbTx.submission_block ?? null,
        minedBlock: dbTx.mined_block ?? null,
        stages: [
            { name: 'New', status: 'completed', timing: '0ms' },
            {
                name: 'Queued',
                status: signingStartedAt
                    ? 'completed'
                    : (isFailed || isCryptoAbort) ? 'failed' : 'running',
                timing: waitTiming,
            },
            {
                name: 'Signed',
                status: dbTx.signed_hex_payload
                    ? 'completed'
                    : (isCryptoAbort || isFailed) ? 'failed'
                        : signingStartedAt ? 'running' : 'pending',
                timing: signedTiming,
            },
            {
                name: 'In Mempool',
                status: isConfirmed ? 'completed' : (isFailed || isCryptoAbort) ? 'failed' : isStalled ? 'stalled' : (isInMempool || dbTx.transaction_hash) ? 'in_mempool' : 'pending',
                timing: submittedTiming,
            },
            {
                name: 'Confirmed',
                status: isConfirmed ? 'completed' : ((isFailed || isCryptoAbort) ? 'failed' : 'pending'),
                timing: confirmedTiming,
            },
        ],
        duration: durationStr,
        startTime: startTime,
        progress: progress,
        logs,
    };
};

export const TransactionsProvider = ({ children }) => {
    const [transactions, setTransactions] = useState([]);
    const [error, setError] = useState(null);
    const { isPolling } = usePolling();

    const fetchRef = useRef();
    const intervalRef = useRef();

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
    }, [isPolling]);

    return (
        <TransactionsContext.Provider value={{ transactions, error, refresh: fetchTransactions }}>
            {children}
        </TransactionsContext.Provider>
    );
};

export const useTransactions = () => useContext(TransactionsContext);