import { statusIcons } from '../../util/IconMap';
import { getSafeStatus, stageBadgeClasses } from '../../util/ColorMap';

const LogsPanel = ({ logs }) => {
    return (
        <div className="lg:col-span-2">
            <div className="flex items-center justify-between mb-4">
                <h4 className="section-heading">Execution Logs</h4>
            </div>
            <div className="log-terminal max-h-80 overflow-y-auto">
                {logs.length === 0 ? (
                    <div className="p-4 text-darcula-muted text-sm">No logs available</div>
                ) : (
                    logs.map((log, idx) => (
                        <div key={`${log.timestamp}-${idx}`} className="log-line">
                            <span className="log-timestamp">{log.timestamp}</span>
                            <span className={`log-level ${log.level}`}>[{log.level.toUpperCase()}]</span>
                            <span className="log-message">{log.message}</span>
                        </div>
                    ))
                )}
            </div>
        </div>
    );
};


const TransactionDetails = ({ transaction }) => {
    return (
        <div className="details-panel border-b border-darcula-border">
            <div className="p-6 bg-darcula-bg">
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    <div className="lg:col-span-1">
                        <h4 className="section-heading mb-4">Stage Details</h4>
                        <div className="space-y-2">
                            {transaction.stages.map((stage, index) => {
                                const safeStatus = getSafeStatus(stage.status);

                                return (
                                    <div
                                        key={`${transaction.id}-${stage.name}`}
                                        className={`flex items-center justify-between p-3 rounded-xl ${safeStatus === 'running' ? 'bg-darcula-cyan/10 border border-darcula-cyan/30' : 'bg-darcula-card'}`}
                                    >
                                        <div className="flex items-center gap-3">
                                            <div className={`w-6 h-6 rounded-full flex items-center justify-center text-xs ${stageBadgeClasses[safeStatus]}`}>
                                                {index + 1}
                                            </div>
                                            <span className={`text-sm ${safeStatus === 'running' ? 'text-darcula-cyan font-medium' : 'text-darcula-text'}`}>
                                                {stage.name}
                                            </span>
                                        </div>
                                        <div className="flex items-center gap-2">
                                            {stage.timing && (
                                                <span className="text-xs text-darcula-muted font-mono">{stage.timing}</span>
                                            )}
                                            {statusIcons[safeStatus]}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>

                        <div className="mt-4 p-4 card-base">
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <p className="label-xs">Duration</p>
                                    <p className="value-mono">{transaction.duration}</p>
                                </div>
                                <div>
                                    <p className="label-xs">Trace ID</p>
                                    <p className="value-mono">{transaction.traceId || '--'}</p>
                                </div>
                            </div>
                        </div>
                    </div>

                    <LogsPanel logs={transaction.logs} />
                </div>
            </div>
        </div>
    );
};

export default TransactionDetails;