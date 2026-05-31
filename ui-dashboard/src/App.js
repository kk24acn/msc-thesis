import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Accounts from './pages/Accounts';
import Transactions from './pages/Transactions';
import { Web3Provider } from './context/Web3Context';
import { TransactionsProvider } from './context/TransactionsContext';

const App = () => {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/accounts" element={<Web3Provider><Accounts /></Web3Provider>} />
        <Route path="/transactions" element={<TransactionsProvider><Transactions /></TransactionsProvider>} />
        <Route path="*" element={<Navigate to="/accounts" replace />} />
      </Routes>
    </BrowserRouter>
  );
};

export default App;
