import { Wallet, Users, Zap } from 'lucide-react';

const StatsCards = ({ accounts, funderAccounts }) => {
  const activeBalance = accounts?.reduce((sum, acc) => sum + acc.balance, 0) || 0;
  const totalFunderBalance = funderAccounts?.reduce((sum, acc) => sum + Number(acc.balance), 0) || 0;

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
      <div className="card-gradient p-5">
        <div className="flex items-center justify-between">
          <div>
            <p className="label-sm mb-1">Active Balance</p>
            <p className="stat-value">
              {activeBalance.toFixed(2)} <span className="text-darcula-success">ETH</span>
            </p>
          </div>
          <div className="icon-box w-12 h-12 bg-darcula-success/10">
            <Wallet className="w-6 h-6 text-darcula-success" />
          </div>
        </div>
      </div>
      <div className="card-gradient p-5">
        <div className="flex items-center justify-between">
          <div>
            <p className="label-sm mb-1">Funder Balance</p>
            <p className="stat-value">
              {totalFunderBalance.toFixed(2)} <span className="text-darcula-warning">ETH</span>
            </p>
          </div>
          <div className="icon-box w-12 h-12 bg-darcula-warning/10">
            <Zap className="w-6 h-6 text-darcula-warning" />
          </div>
        </div>
      </div>
    </div>
  );
};

export default StatsCards;
