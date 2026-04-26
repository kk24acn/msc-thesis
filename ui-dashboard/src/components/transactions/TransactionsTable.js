import TransactionsRow from './TransactionsRow';

const TransactionsTable = ({
  transactions,
  expandedId,
  onToggle,
}) => {
  return (
    <div className="card-bordered overflow-hidden">
      <div className="table-header grid grid-cols-12 gap-4 px-6 py-3">
        <div className="col-span-4">Transaction</div>
        <div className="col-span-5">Pipeline Progress</div>
        <div className="col-span-2">Status</div>
        <div className="col-span-1">Duration</div>
      </div>

      <div id="transactions-list">
        {transactions.map((transaction) => (
          <TransactionsRow
            key={transaction.id}
            transaction={transaction}
            expanded={expandedId === transaction.id}
            onToggle={onToggle}
          />
        ))}
      </div>
    </div>
  );
};

export default TransactionsTable;
