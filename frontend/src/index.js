import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import App from './App';
import MyLibrary from './MyLibrary';

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <Router>
      <Routes>
        <Route path="/" element={<App />} />
        <Route path="/my-library/*" element={<MyLibrary />} /> {/* 注意这里的通配符 */}
      </Routes>
    </Router>
  </React.StrictMode>
);