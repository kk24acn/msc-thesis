import { useMemo, useState } from 'react';
import Sidebar from '../components/common/Sidebar';
import Header from '../components/common/Header';
import TransactionsStats from '../components/transactions/StatsCards';
import TransactionsFilters from '../components/transactions/FilterActions';
import TransactionsTable from '../components/transactions/TransactionsTable';
import PaginationBar from '../components/transactions/PaginationBar';
import { useTransactions } from '../context/TransactionsContext';

const ITEMS_PER_PAGE = 10;

const Transactions = () => {
    const { transactions } = useTransactions();

    const [expandedId, setExpandedId] = useState(null);
    const [activeFilter, setActiveFilter] = useState('all');
    const [search, setSearch] = useState('');
    const [currentPage, setCurrentPage] = useState(1);

    const filteredTransactions = useMemo(() => {
        const query = search.toLowerCase();
        let base = activeFilter === 'all'
            ? transactions
            : transactions.filter((t) => t.status === activeFilter);

        if (!query) {
            return base.sort((a, b) => {
                const aTrace = isNaN(parseInt(a.traceId)) ? Infinity : parseInt(a.traceId);
                const bTrace = isNaN(parseInt(b.traceId)) ? Infinity : parseInt(b.traceId);
                return aTrace - bTrace;
            });
        }

        return base
            .filter((t) => t.traceId?.toLowerCase() === query)
            .sort((a, b) => {
                const aTrace = isNaN(parseInt(a.traceId)) ? Infinity : parseInt(a.traceId);
                const bTrace = isNaN(parseInt(b.traceId)) ? Infinity : parseInt(b.traceId);
                return aTrace - bTrace;
            });
    }, [transactions, activeFilter, search]);

    const paginatedTransactions = useMemo(() => {
        const startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
        const endIndex = startIndex + ITEMS_PER_PAGE;
        return filteredTransactions.slice(startIndex, endIndex);
    }, [filteredTransactions, currentPage]);

    const totalPages = Math.ceil(filteredTransactions.length / ITEMS_PER_PAGE);

    const toggleExpand = (id) => setExpandedId((prev) => (prev === id ? null : id));

    const handlePageChange = (page) => {
        setCurrentPage(page);
        setExpandedId(null);
    };

    const handleFilterChange = (filter) => {
        setActiveFilter(filter);
        setCurrentPage(1);
        setExpandedId(null);
    };

    const handleSearchChange = (query) => {
        setSearch(query);
        setCurrentPage(1);
        setExpandedId(null);
    };

    return (
        <div className="page-shell">
            <Sidebar />
            <main className="main-content">
                <Header title="Transactions" />

                <div className="p-6">
                    <TransactionsStats transactions={transactions} />
                    <TransactionsFilters
                        activeFilter={activeFilter}
                        onFilterChange={handleFilterChange}
                        search={search}
                        onSearchChange={handleSearchChange}
                        transactionCount={filteredTransactions.length}
                    />
                    <TransactionsTable
                        transactions={paginatedTransactions}
                        expandedId={expandedId}
                        onToggle={toggleExpand}
                    />
                    <PaginationBar
                        currentPage={currentPage}
                        totalPages={totalPages}
                        itemsShowing={paginatedTransactions.length}
                        totalItems={filteredTransactions.length}
                        onPageChange={handlePageChange}
                    />
                </div>
            </main>
        </div>
    );
};

export default Transactions;