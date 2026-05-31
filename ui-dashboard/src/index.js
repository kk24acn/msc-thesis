import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import { PollingProvider } from './context/PollingContext';

const root = ReactDOM.createRoot(document.getElementById('root'));

root.render(
  <React.StrictMode>
    <PollingProvider>
      <App />
    </PollingProvider>
  </React.StrictMode>
);

