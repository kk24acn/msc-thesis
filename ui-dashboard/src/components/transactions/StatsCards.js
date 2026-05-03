import { TrendingUp, Clock, Zap } from 'lucide-react';

const TransactionsStats = ({ transactions }) => {
    const completed = transactions.filter((t) => t.status === 'completed').length;
    const failed = transactions.filter((t) => t.status === 'failed').length;
    const finished = completed + failed;

    const successRate = finished > 0 ? ((completed / finished) * 100).toFixed(1) : 0;

    let meanExecutionTime = '--';
    if (completed > 0) {
        const completedTransactions = transactions.filter((t) => t.status === 'completed');
        const totalDurationMs = completedTransactions.reduce((acc, t) => {
            // Parse duration string like "1.2s" to milliseconds
            if (t.duration && t.duration !== '--') {
                const seconds = parseFloat(t.duration);
                return acc + (seconds * 1000);
            }
            return acc;
        }, 0);

        const meanMs = totalDurationMs / completed;
        meanExecutionTime = meanMs > 0 ? `${(meanMs / 1000).toFixed(1)}s` : '--';
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
        <div className="grid grid-cols-2 md:grid-cols-3 gap-4 mb-6">
            <div className="card-bordered rounded-2xl p-4">
                <div className="flex items-center justify-between mb-2">
                    <span className="label-sm">Transactions/sec</span>
                    <Zap className="w-4 h-4 text-darcula-warning" />
                </div>
                <p className="stat-value text-darcula-warning">{transactionsPerSecond}</p>
            </div>
            <div className="card-bordered rounded-2xl p-4">
                <div className="flex items-center justify-between mb-2">
                    <span className="label-sm">Success Rate</span>
                    <TrendingUp className="w-4 h-4 text-darcula-success" />
                </div>
                <p className="stat-value text-darcula-success">{successRate}%</p>
                <p className="text-xs text-darcula-muted mt-1">({completed}/{finished})</p>
            </div>
            <div className="card-bordered rounded-2xl p-4">
                <div className="flex items-center justify-between mb-2">
                    <span className="label-sm">Mean Exec Time</span>
                    <Clock className="w-4 h-4 text-darcula-cyan" />
                </div>
                <p className="stat-value text-darcula-cyan">{meanExecutionTime}</p>
            </div>
        </div>
    );
};

export default TransactionsStats;
