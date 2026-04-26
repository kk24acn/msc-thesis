import { Menu } from 'lucide-react';

const Header = ({ onMenuToggle, title }) => {
    return (
        <header className="sticky top-0 z-40 glass-effect border-b border-darcula-border">
            <div className="flex items-center justify-between px-6 py-4">
                <div className="flex items-center gap-4">
                    <button
                        onClick={onMenuToggle}
                        className="lg:hidden btn-ghost"
                        aria-label="Toggle menu"
                    >
                        <Menu className="w-5 h-5" />
                    </button>
                    <div>
                        <h1 className="text-xl font-semibold">{title}</h1>
                    </div>
                </div>
            </div>
        </header>
    );
};

export default Header;
