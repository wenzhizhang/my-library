// src/components/Header.js
import React, { useState, useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { LIBRARY_PATH } from '../config';
import './Header.css';

const Header = () => {
  const [visitCount, setVisitCount] = useState(null);
  const location = useLocation();
  const { isAuthenticated, user, logout } = useAuth();

  // Report page view on every navigation, update displayed count
  useEffect(() => {
    fetch('/api/stats/page-view', { method: 'POST' })
      .then((r) => r.json())
      .then((data) => setVisitCount(data.total_visits))
      .catch(() => {});
  }, [location.pathname]);
  const prefix = LIBRARY_PATH === '/' ? '' : LIBRARY_PATH;

  const getPath = (path) => {
    if (path === '/') return prefix || '/';
    return `${prefix}${path.startsWith('/') ? path : `/${path}`}`;
  };

  // Consider a link active if the current pathname ends with the link path
  // or contains the path followed by a slash (covers nested routes)
  const isActive = (path) => {
    const p = getPath(path);
    return (
      location.pathname === p ||
      location.pathname.endsWith(p) ||
      location.pathname.includes(p + '/')
    );
  };

  return (
    <nav className="nav">
      <div className="nav-container">
        <Link to={getPath('/')} className="nav-logo">
          <img src="/images/logo/logo-my-library.png" alt="My Library" className="nav-logo-img" />
          My Library
        </Link>
        <div className="nav-links">
          <Link 
            to={getPath('/books')} 
            className={`nav-link ${isActive('/books') ? 'active' : ''}`}
          >
            Books
          </Link>
          <Link 
            to={getPath('/authors')} 
            className={`nav-link ${isActive('/authors') ? 'active' : ''}`}
          >
            Authors
          </Link>
          <Link 
            to={getPath('/publishers')} 
            className={`nav-link ${isActive('/publishers') ? 'active' : ''}`}
          >
            Publishers
          </Link>
          <Link 
            to={getPath('/categories')} 
            className={`nav-link ${isActive('/categories') ? 'active' : ''}`}
          >
            Categories
          </Link>
          <Link 
            to={getPath('/bookshelves')} 
            className={`nav-link ${isActive('/bookshelves') ? 'active' : ''}`}
          >
            Bookshelves
          </Link>
          <Link 
            to={getPath('/book-collections')} 
            className={`nav-link ${isActive('/book-collections') ? 'active' : ''}`}
          >
            Collections
          </Link>
          <Link 
            to={getPath('/brands')} 
            className={`nav-link ${isActive('/brands') ? 'active' : ''}`}
          >
            Brands
          </Link>
          <Link 
            to={getPath('/series')} 
            className={`nav-link ${isActive('/series') ? 'active' : ''}`}
          >
            Series
          </Link>
          <Link 
            to={getPath('/stats')} 
            className={`nav-link ${isActive('/stats') ? 'active' : ''}`}
          >
            Stats
          </Link>
        </div>
        <div className="nav-auth">
          {isAuthenticated ? (
            <div className="nav-auth-user">
              <span className="nav-username">{user?.username}</span>
              <button className="nav-logout" onClick={logout}>Logout</button>
            </div>
          ) : (
            <Link to={`${LIBRARY_PATH}/login`} className="nav-link nav-login-link">Login</Link>
          )}
          {visitCount !== null && (
            <span className="nav-visit-count">{visitCount} visits</span>
          )}
        </div>
      </div>
    </nav>
  );
};

export default Header;