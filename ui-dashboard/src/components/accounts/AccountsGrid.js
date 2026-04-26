import AccountCard from './AccountCard';

const AccountsGrid = ({ accounts, onCopy }) => {
  return (
    <div id="accounts-grid" className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
      {accounts.map((account, idx) => (
        <AccountCard
          key={account.id}
          account={account}
          onCopy={onCopy}
          delay={idx * 100}
        />
      ))}
    </div>
  );
};

export default AccountsGrid;
