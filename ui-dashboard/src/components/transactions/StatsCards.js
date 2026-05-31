import { TrendingUp, Clock, Zap, Activity, RefreshCw, Timer, Shield } from 'lucide-react';

const formatAvgRetries = (avg) => {
    if (avg === 0) return '0';
    return parseFloat(avg.toPrecision(2)).toString();
};

const TransactionsStats = ({ transactions }) => {
    const completed = transactions.filter((t) => t.status === 'completed').length;

    const successRate = transactions.length > 0
        ? ((completed / transactions.length) * 100).toFixed(2)
        : '0.00';

    let meanExecutionTime = '--';
    if (completed > 0) {
        const completedTransactions = transactions.filter((t) => t.status === 'completed');
        const totalDurationMs = completedTransactions.reduce((acc, t) => {
            if (t.duration && t.duration !== '--') {
                return acc + (parseFloat(t.duration) * 1000);
            }
            return acc;
        }, 0);
        const meanMs = totalDurationMs / completed;
        meanExecutionTime = meanMs > 0 ? `${(meanMs / 1000).toFixed(1)}s` : '--';
    }

    let meanPureExecTime = '--';
    const completedWithPureExec = transactions.filter(
        (t) => t.status === 'completed' && t.pureExecMs != null
    );
    if (completedWithPureExec.length > 0) {
        const meanPureMs =
            completedWithPureExec.reduce((acc, t) => acc + t.pureExecMs, 0) /
            completedWithPureExec.length;
        meanPureExecTime = meanPureMs > 0 ? `${(meanPureMs / 1000).toFixed(1)}s` : '--';
    }

    const totalRetries = transactions.reduce(
        (acc, t) => acc + (t.signingRetries || 0) + (t.submissionRetries || 0),
        0
    );
    const avgRetries = transactions.length > 0 ? totalRetries / transactions.length : 0;

    const sweepedCount = transactions.filter((t) => t.isSweeped).length;
    const totalSweeperAttempts = transactions.reduce((acc, t) => acc + (t.sweeperAttempts || 0), 0);

    let batchThroughput = '--';
    if (transactions.length > 1) {
        const earliestCreated = transactions
            .map((t) => t.startTime?.getTime())
            .filter((t) => t && !isNaN(t))
            .reduce((min, t) => Math.min(min, t), Infinity);

        const latestUpdated = transactions
            .map((t) => t.updatedAt?.getTime())
            .filter((t) => t && !isNaN(t))
            .reduce((max, t) => Math.max(max, t), -Infinity);

        if (isFinite(earliestCreated) && isFinite(latestUpdated) && latestUpdated > earliestCreated) {
            const batchSpanSeconds = (latestUpdated - earliestCreated) / 1000;
            batchThroughput = (transactions.length / batchSpanSeconds).toFixed(2);
        }
    }

    let transactionsPerSecond = '--';
    if (transactions.length > 1) {
        const times = transactions
            .map((t) => t.startTime.getTime())
            .filter((t) => !isNaN(t))
            .sort((a, b) => a - b);

        if (times.length > 1) {
            const timeSpanMs = times[times.length - 1] - times[0];
            const timeSpanSeconds = timeSpanMs / 1000;
            transactionsPerSecond = timeSpanSeconds > 0 ? (transactions.length / timeSpanSeconds).toFixed(2) : '--';
        }
    }

    return (
        <div className="flex flex-col gap-4 mb-6">
            <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                <div className="card-bordered rounded-2xl p-4">
                    <div className="flex items-center justify-between mb-2">
                        <span className="label-sm">Batch Throughput</span>
                        <Activity className="w-4 h-4 text-darcula-purple" />
                    </div>
                    <p className="stat-value text-darcula-purple">{batchThroughput}</p>
                    <p className="text-xs text-darcula-muted mt-1">tx/s (first created &rarr; last updated)</p>
                </div>
                <div className="card-bordered rounded-2xl p-4">
                    <div className="flex items-center justify-between mb-2">
                        <span className="label-sm">Submission Rate</span>
                        <Zap className="w-4 h-4 text-darcula-warning" />
                    </div>
                    <p className="stat-value text-darcula-warning">{transactionsPerSecond}</p>
                    <p className="text-xs text-darcula-muted mt-1">tx/s (first &rarr; last created)</p>
                </div>
                <div className="card-bordered rounded-2xl p-4">
                    <div className="flex items-center justify-between mb-2">
                        <span className="label-sm">Success Rate</span>
                        <TrendingUp className="w-4 h-4 text-darcula-success" />
                    </div>
                    <p className="stat-value text-darcula-success">{successRate}%</p>
                    <p className="text-xs text-darcula-muted mt-1">({completed}/{transactions.length})</p>
                </div>
            </div>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <div className="card-bordered rounded-2xl p-4">
                    <div className="flex items-center justify-between mb-2">
                        <span className="label-sm">Mean Exec Time</span>
                        <Clock className="w-4 h-4 text-darcula-cyan" />
                    </div>
                    <p className="stat-value text-darcula-cyan">{meanExecutionTime}</p>
                    <p className="text-xs text-darcula-muted mt-1">total duration (incl. queued)</p>
                </div>
                <div className="card-bordered rounded-2xl p-4">
                    <div className="flex items-center justify-between mb-2">
                        <span className="label-sm">Mean Pure Exec Time</span>
                        <Timer className="w-4 h-4 text-darcula-cyan" />
                    </div>
                    <p className="stat-value text-darcula-cyan">{meanPureExecTime}</p>
                    <p className="text-xs text-darcula-muted mt-1">excl. queued time</p>
                </div>
                <div className="card-bordered rounded-2xl p-4">
                    <div className="flex items-center justify-between mb-2">
                        <span className="label-sm">Avg Retries / Tx</span>
                        <RefreshCw className={`w-4 h-4 ${avgRetries > 0 ? 'text-darcula-warning' : 'text-darcula-success'}`} />
                    </div>
                    <p className={`stat-value ${avgRetries > 0 ? 'text-darcula-warning' : 'text-darcula-success'}`}>
                        {formatAvgRetries(avgRetries)}
                    </p>
                    <p className="text-xs text-darcula-muted mt-1">
                        {totalRetries} total retr{totalRetries === 1 ? 'y' : 'ies'}
                    </p>
                </div>
                <div className="card-bordered rounded-2xl p-4">
                    <div className="flex items-center justify-between mb-2">
                        <span className="label-sm">Sweep Attempts</span>
                        <Shield className={`w-4 h-4 ${sweepedCount > 0 ? 'text-darcula-warning' : 'text-darcula-success'}`} />
                    </div>
                    <p className={`stat-value ${sweepedCount > 0 ? 'text-darcula-warning' : 'text-darcula-success'}`}>
                        {totalSweeperAttempts}
                    </p>
                    <p className="text-xs text-darcula-muted mt-1">
                        {sweepedCount} sweeped tx{sweepedCount !== 1 ? 's' : ''}
                    </p>
                </div>
            </div>
        </div>
    );
};

export default TransactionsStats;
