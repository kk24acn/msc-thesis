import { NavLink } from 'react-router-dom';
import { Wallet, ArrowRightLeft } from 'lucide-react';

const baseLink = 'sidebar-link';
const activeLink = 'sidebar-link-active';

const Sidebar = () => {
    return (
        <aside
            id="sidebar"
            className="fixed left-0 top-0 h-full w-16 lg:w-64 bg-darcula-card border-r border-darcula-border z-50 transition-all duration-300"
        >
            <div className="p-4 lg:p-6">
                <div className="flex items-center gap-3 justify-center lg:justify-start">
                    <span className="hidden lg:block font-bold text-lg tracking-tight">
                        Blockchain<span className="text-darcula-cyan">Orchestrator</span>
                    </span>
                </div>
            </div>

            <nav className="mt-8 px-2 lg:px-4">
                <NavLink to="/accounts" end className={({ isActive }) => (isActive ? activeLink : baseLink)}>
                    <Wallet className="w-5 h-5" />
                    <span className="hidden lg:block font-medium">Accounts</span>
                </NavLink>
                <NavLink to="/transactions" className={({ isActive }) => (isActive ? activeLink : baseLink)}>
                    <ArrowRightLeft className="w-5 h-5" />
                    <span className="hidden lg:block font-medium">Transactions</span>
                </NavLink>
            </nav>
        </aside>
    );
};

export default Sidebar;
