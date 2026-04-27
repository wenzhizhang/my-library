// src/components/Header.js
import React from 'react';
import { Link, useLocation } from 'react-router-dom'; // 添加useLocation用于检测当前路由
import './Header.css';

const Header = ({ isMyLibrary = false }) => {
  const location = useLocation();
  
  // 根据isMyLibrary参数决定是否添加/my-library前缀
  const getBasePath = (path) => {
    if (isMyLibrary) {
      return `/my-library${path}`;
    }
    return path;
  };
  
  // 检查当前路径是否激活
  const isActive = (path) => {
    const fullPath = isMyLibrary ? `/my-library${path}` : path;
    return location.pathname === fullPath || location.pathname.startsWith(fullPath + '/');
  };

  return (
    <nav className="nav">
      <div className="nav-container">
        <Link to={getBasePath('/')} className="nav-logo">My Library</Link>
        <div className="nav-links">
          <Link 
            to={getBasePath('/books')} 
            className={`nav-link ${isActive('/books') ? 'active' : ''}`}
          >
            Books
          </Link>
          <Link 
            to={getBasePath('/authors')} 
            className={`nav-link ${isActive('/authors') ? 'active' : ''}`}
          >
            Authors
          </Link>
          <Link 
            to={getBasePath('/publishers')} 
            className={`nav-link ${isActive('/publishers') ? 'active' : ''}`}
          >
            Publishers
          </Link>
          <Link 
            to={getBasePath('/categories')} 
            className={`nav-link ${isActive('/categories') ? 'active' : ''}`}
          >
            Categories
          </Link>
          <Link 
            to={getBasePath('/bookshelves')} 
            className={`nav-link ${isActive('/bookshelves') ? 'active' : ''}`}
          >
            Bookshelves
          </Link>
          <Link 
            to={getBasePath('/brands')} 
            className={`nav-link ${isActive('/brands') ? 'active' : ''}`}
          >
            Brands
          </Link>
          <Link 
            to={getBasePath('/series')} 
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