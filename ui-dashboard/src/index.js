import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import { Web3Provider } from './context/Web3Context';
import { TransactionsProvider } from './context/TransactionsContext';

const root = ReactDOM.createRoot(document.getElementById('root'));

root.render(
  <React.StrictMode>
    <Web3Provider>
      <TransactionsProvider>
        <App />
      </TransactionsProvider>
    </Web3Provider>
  </React.StrictMode>
);
