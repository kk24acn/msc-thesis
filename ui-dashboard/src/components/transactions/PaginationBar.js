import { ChevronLeft, ChevronRight } from 'lucide-react';

const PaginationBar = ({
    currentPage = 1,
    totalPages = 1,
    itemsShowing = 0,
    totalItems = 0,
    onPageChange = () => { },
}) => {
    const startItem = totalItems > 0 ? (currentPage - 1) * itemsShowing + 1 : 0;
    const endItem = Math.min(currentPage * itemsShowing, totalItems);

    const getPageNumbers = () => {
        const pages = [];
        const maxVisible = 5;

        if (totalPages <= maxVisible) {
            for (let i = 1; i <= totalPages; i++) {
                pages.push(i);
            }
        } else {
            pages.push(1);
            if (currentPage > 3) pages.push('...');

            const start = Math.max(2, currentPage - 1);
            const end = Math.min(totalPages - 1, currentPage + 1);

            for (let i = start; i <= end; i++) {
                if (!pages.includes(i)) pages.push(i);
            }

            if (currentPage < totalPages - 2) pages.push('...');
            if (!pages.includes(totalPages)) pages.push(totalPages);
        }

        return pages;
    };

    const pageNumbers = getPageNumbers();

    return (
        <div className="flex items-center justify-between mt-6">
            <p className="label-sm">
                Showing <span className="text-darcula-text">{startItem}-{endItem}</span> of{' '}
                <span className="text-darcula-text">{totalItems}</span> transactions
            </p>
            <div className="flex items-center gap-2">
                <button
                    className="btn btn-secondary"
                    disabled={currentPage === 1}
                    onClick={() => onPageChange(currentPage - 1)}
                >
                    <ChevronLeft className="w-4 h-4" />
                </button>
                {pageNumbers.map((page, idx) => (
                    page === '...' ? (
                        <span key={`ellipsis-${idx}`} className="text-darcula-muted px-2">
                            ...
                        </span>
                    ) : (
                        <button
                            key={page}
                            className={page === currentPage ? 'btn btn-primary' : 'btn btn-secondary'}
                            onClick={() => onPageChange(page)}
                        >
                            {page}
                        </button>
                    )
                ))}
                <button
                    className="btn btn-secondary"
                    disabled={currentPage === totalPages}
                    onClick={() => onPageChange(currentPage + 1)}
                >
                    <ChevronRight className="w-4 h-4" />
                </button>
            </div>
        </div>
    );
};

export default PaginationBar;
