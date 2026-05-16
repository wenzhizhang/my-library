// src/components/Header.js
import React from 'react';
import { Link, useLocation } from 'react-router-dom'; // 添加useLocation用于检测当前路由
// Links should rely on Router basename; remove LIBRARY_PATH to avoid duplication
import './Header.css';

const Header = ({ isMyLibrary = false }) => {
  const location = useLocation();

  // Links should be absolute within the app (Router basename will prefix them)
  const getPath = (path) => {
    if (path === '/') return '/';
    return path.startsWith('/') ? path : `/${path}`;
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
        <Link to={getPath('/')} className="nav-logo">My Library</Link>
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
        </div>
      </div>
    </nav>
  );
};

export default Header;