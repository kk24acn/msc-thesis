import { createContext, useContext, useState, useCallback } from 'react';

const PollingContext = createContext();

export const PollingProvider = ({ children }) => {
    const [isPolling, setIsPolling] = useState(true);

    const togglePolling = useCallback(() => {
        setIsPolling((prev) => !prev);
    }, []);

    return (
        <PollingContext.Provider value={{ isPolling, togglePolling }}>
            {children}
        </PollingContext.Provider>
    );
};

export const usePolling = () => useContext(PollingContext);
