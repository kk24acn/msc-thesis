import { Search } from 'lucide-react';

const FilterActions = ({ count, search, onSearchChange }) => {
    return (
        <div className="flex flex-col gap-4 mb-6">
            <div className="flex items-center justify-between">
                <h2 className="text-lg font-semibold text-darcula-text">
                    All Accounts <span className="text-darcula-muted text-sm font-normal">({count})</span>
                </h2>
            </div>

            <div className="flex flex-col lg:flex-row gap-4 justify-between lg:items-center">
                <div className="flex-1 relative max-w-2xl">
                    <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-darcula-muted" />
                    <input
                        type="text"
                        value={search}
                        onChange={(e) => onSearchChange(e.target.value)}
                        placeholder="Search by address or KeyID..."
                        className="w-full pl-12 pr-4 py-3 input-search bg-darcula-card font-mono"
                    />
                </div>
            </div>
        </div>
    );
};

export default FilterActions;