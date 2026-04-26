import {
  X,
  Copy,
  Activity,
  ArrowDownLeft,
  ArrowUpRight,
  ArrowLeftRight,
  Bitcoin,
} from 'lucide-react';
import { typeIconMap } from '../../util/IconMap';
import { statusColorMap } from '../../util/ColorMap';

const InfoCard = ({ label, value, mono }) => (
  <div className="info-card">
    <p className="label-xs">{label}</p>
    <p className={`${mono ? 'font-mono' : ''} font-medium`}>{value}</p>
  </div>
);

const AccountDetails = ({ account, onClose, onCopy }) => {
  if (!account) return null;

  return (
    <>
      <div className="fixed inset-0 bg-darcula-overlay/50 z-40" onClick={onClose}></div>
      <div className="fixed right-0 top-0 h-full w-full sm:w-[480px] bg-darcula-card border-l border-darcula-border z-50 transform transition-transform duration-300 slide-in overflow-y-auto scrollbar-hide">
        <div className="p-6 border-b border-darcula-border flex items-center justify-between sticky top-0 bg-darcula-card z-10">
          <h2 className="text-lg font-semibold">Account Details</h2>
          <button onClick={onClose} className="btn-ghost" aria-label="Close">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 space-y-6">
          <div className="flex items-center gap-4">
            <div className="icon-box-gradient">
              <Bitcoin className="w-6 h-6 text-darcula-cyan" />
            </div>
            <div>
              <h3 className="text-xl font-semibold">{account.label}</h3>
              <div className="flex items-center gap-2 mt-1">
                <span className="font-mono text-sm text-darcula-muted">{account.address.address}</span>
                <button onClick={() => onCopy(account.address.address)} className="p-1 rounded hover:bg-darcula-contrast/10">
                  <Copy className="w-4 h-4 text-darcula-muted hover:text-darcula-cyan" />
                </button>
              </div>
            </div>
          </div>

          <div className="card-gradient bg-darcula-header/50 p-6">
            <p className="label-sm mb-2">Current Balance</p>
            <div className="flex items-baseline gap-2 mb-2">
              <span className="text-4xl font-bold font-mono">
                {account.balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 6 })}
              </span>
              <span className="text-darcula-cyan text-xl font-medium">ETH</span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <InfoCard
              label="Status"
              value={
                <span className="flex items-center gap-2 capitalize">
                  <span className={`w-2 h-2 rounded-full ${statusColorMap[account.status] || 'bg-darcula-muted'}`}></span>
                  {account.status}
                </span>
              }
            />
            <InfoCard label="Total Transactions" value={account.transactions.toLocaleString()} mono />
            <InfoCard label="Current Nonce" value={account.nonce} mono />
            <InfoCard label="Last Activity" value={account.lastActivity} />
          </div>
        </div>
      </div>
    </>
  );
};

export default AccountDetails;
