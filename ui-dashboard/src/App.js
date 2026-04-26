import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Accounts from './pages/Accounts';
import Transactions from './pages/Transactions';

const App = () => {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/accounts" element={<Accounts />} />
        <Route path="/transactions" element={<Transactions />} />
        <Route path="*" element={<Navigate to="/accounts" replace />} />
      </Routes>
    </BrowserRouter>
  );
};

export default App;
