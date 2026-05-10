import { Search } from 'lucide-react';

const filters = ['all', 'running', 'in_mempool', 'stalled', 'completed', 'failed', 'crypto_abort'];

const filterLabels = {
    all: 'All',
    running: 'Running',
    in_mempool: 'In Mempool',
    stalled: 'Stalled',
    completed: 'Completed',
    failed: 'Failed',
    crypto_abort: 'Crypto Abort',
};

const TransactionsFilters = ({ activeFilter, onFilterChange, search, onSearchChange, transactionCount = 0 }) => {
    return (
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 mb-6">
            <div className="flex items-center gap-2 flex-wrap">
                {filters.map((filter) => (
                    <button
                        key={filter}
                        className={`filter-tab ${activeFilter === filter ? 'active' : ''}`}
                        onClick={() => onFilterChange(filter)}
                    >
                        {filterLabels[filter]}
                    </button>
                ))}
                <span className="text-sm text-darcula-muted ml-2">
                    ({transactionCount} transaction{transactionCount !== 1 ? 's' : ''})
                </span>
            </div>
            <div className="flex items-center gap-3">
                <div className="relative">
                    <Search className="w-4 h-4 text-darcula-muted absolute left-3 top-1/2 -translate-y-1/2" />
                    <input
                        type="text"
                        value={search}
                        onChange={(e) => onSearchChange(e.target.value)}
                        placeholder="Search by TraceID..."
                        className="input-search bg-darcula-header pl-10 pr-4 py-2 placeholder-darcula-muted w-64"
                    />
                </div>
            </div>
        </div>
    );
};

export default TransactionsFilters;
