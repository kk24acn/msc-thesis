import { Copy, Bitcoin } from 'lucide-react';

const formatAddress = (addr) => `${addr.slice(0, 6)}...${addr.slice(-4)}`;

const AccountCard = ({ account, onCopy, delay = 0 }) => {
    return (
        <div
            className="card-gradient bg-darcula-card hover:bg-darcula-header/50 p-5 transition-all cursor-pointer group slide-in"
            style={{ animationDelay: `${delay}ms` }}
        >
            <div className="flex items-start justify-between mb-4">
                <div className="flex items-center gap-3 flex-1">
                    <div className="icon-box-gradient">
                        <Bitcoin className="w-6 h-6 text-darcula-cyan" />
                    </div>
                    <div>
                        <h3 className="font-semibold text-sm">{account.label}</h3>
                        <div className="flex items-center gap-2 mt-1">
                            <span className="font-mono text-xs text-darcula-muted">{formatAddress(account.address)}</span>
                            <button
                                onClick={(e) => {
                                    e.stopPropagation();
                                    onCopy(account.address);
                                }}
                                className="opacity-0 group-hover:opacity-100 transition-opacity"
                                aria-label="Copy address"
                            >
                                <Copy className="w-3 h-3 text-darcula-muted hover:text-darcula-cyan" />
                            </button>
                        </div>
                    </div>
                </div>
                <div className="flex flex-col items-end">
                    <span className="text-xs text-darcula-muted">Nonce</span>
                    <span className="font-mono text-sm text-darcula-text font-medium">{account.nonce}</span>
                </div>
            </div>

            <div className="mb-4">
                <div className="flex items-baseline gap-2">
                    <span className="stat-value">
                        {account.balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 4 })}
                    </span>
                    <span className="text-darcula-cyan font-medium">ETH</span>
                </div>
            </div>
        </div>
    );
};

export default AccountCard;
