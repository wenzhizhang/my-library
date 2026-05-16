import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import App from './App';
import MyLibrary from './MyLibrary';
import { BASE_PATH } from './config';

const root = createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <Router basename={BASE_PATH}>
      <Routes>
        <Route path="/" element={<App />} />
        <Route path="/*" element={<MyLibrary />} />
      </Routes>
    </Router>
  </React.StrictMode>
);