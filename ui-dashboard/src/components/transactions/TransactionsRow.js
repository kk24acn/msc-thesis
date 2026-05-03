import { ChevronRight } from 'lucide-react';
import { statusIcons } from '../../util/IconMap';
import { getSafeStatus } from '../../util/ColorMap';
import TransactionDetails from './TransactionDetails';

export const StatusBadge = ({ status }) => {
    const safeStatus = getSafeStatus(status);

    return (
        <span className={`status-badge ${safeStatus}`}>
            {statusIcons[safeStatus]}
            {status}
        </span>
    );
};

export const PipelineStages = ({ stages }) => {
    return (
        <div className="flex items-center gap-1">
            {stages.map((stage, index) => {
                const safeStatus = getSafeStatus(stage.status);

                return (
                    <div
                        key={`${stage.name}-${index}`}
                        className={`pipeline-stage ${safeStatus} ${index < stages.length - 1 ? 'mr-5' : ''}`}
                    >
                        <div
                            className={`stage-node ${safeStatus} tooltip`}
                            data-tooltip={`${stage.name}: ${stage.status}`}
                        >
                            {statusIcons[safeStatus]}
                        </div>
                    </div>
                );
            })}
        </div>
    );
};

const TransactionsRow = ({
    transaction,
    expanded,
    onToggle,
}) => {
    return (
        <div className={`transaction-row ${expanded ? 'expanded' : ''}`} data-transaction-id={transaction.id}>
            <div
                className="grid grid-cols-12 gap-4 px-6 py-4 items-center border-b border-darcula-border cursor-pointer"
                onClick={() => onToggle(transaction.id)}
            >
                <div className="col-span-4">
                    <div className="flex items-center gap-3">
                        <ChevronRight className={`w-4 h-4 text-darcula-muted expand-icon ${expanded ? 'rotated' : ''}`} />
                        <div>
                            <p className="value-mono font-medium">{transaction.id}</p>
                            <p className="text-xs text-darcula-muted">{transaction.name}</p>
                            <p className="text-xs text-darcula-purple">{transaction.description}</p>
                        </div>
                    </div>
                </div>
                <div className="col-span-5">
                    <PipelineStages stages={transaction.stages} />
                    <div className="progress-bar mt-2">
                        <div className="progress-fill" style={{ width: `${transaction.progress}%` }}></div>
                    </div>
                </div>
                <div className="col-span-2">
                    <StatusBadge status={transaction.status} />
                </div>
                <div className="col-span-1">
                    <span className="value-mono">{transaction.duration}</span>
                </div>
            </div>
            {expanded ? <TransactionDetails transaction={transaction} /> : null}
        </div>
    );
};

export default TransactionsRow;
