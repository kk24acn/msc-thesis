import { NavLink } from 'react-router-dom';
import { Wallet, ArrowRightLeft, Zap } from 'lucide-react';
import { usePolling } from '../../context/PollingContext';

const baseLink = 'sidebar-link';
const activeLink = 'sidebar-link-active';

const Sidebar = () => {
    const { isPolling, togglePolling } = usePolling();

    return (
        <aside
            id="sidebar"
            className="fixed left-0 top-0 h-full w-64 bg-darcula-card border-r border-darcula-border z-50"
        >
            <div className="p-6">
                <div className="flex items-center gap-3 justify-start">
                    <span className="font-bold text-lg tracking-tight">
                        Blockchain<span className="text-darcula-cyan">Orchestrator</span>
                    </span>
                </div>
            </div>

            <nav className="mt-8 px-4">
                <NavLink to="/accounts" end className={({ isActive }) => (isActive ? activeLink : baseLink)}>
                    <Wallet className="w-5 h-5" />
                    <span className="font-medium">Accounts</span>
                </NavLink>
                <NavLink to="/transactions" className={({ isActive }) => (isActive ? activeLink : baseLink)}>
                    <ArrowRightLeft className="w-5 h-5" />
                    <span className="font-medium">Transactions</span>
                </NavLink>

                <button
                    onClick={togglePolling}
                    className={`sidebar-link w-full text-left ${isPolling ? 'text-darcula-cyan' : 'text-darcula-text-secondary'}`}
                >
                    <Zap className="w-5 h-5" />
                    <span className="font-medium">Polling</span>
                    <span className="ml-auto text-xs">{isPolling ? 'ON' : 'OFF'}</span>
                </button>
            </nav>
        </aside>
    );
};

export default Sidebar;
