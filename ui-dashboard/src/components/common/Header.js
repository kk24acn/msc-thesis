const Header = ({ title }) => {
    return (
        <header className="sticky top-0 z-40 glass-effect border-b border-darcula-border">
            <div className="flex items-center justify-between px-6 py-4">
                <div className="flex items-center gap-4">
                    <div>
                        <h1 className="text-xl font-semibold">{title}</h1>
                    </div>
                </div>
            </div>
        </header>
    );
};

export default Header;
