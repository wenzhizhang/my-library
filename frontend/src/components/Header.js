// src/components/Header.js
import React from 'react';
import { Link, useLocation } from 'react-router-dom'; // 添加useLocation用于检测当前路由
import { LIBRARY_PATH } from '../config';
import './Header.css';

const Header = ({ isMyLibrary = false }) => {
  const location = useLocation();

  // 统一使用绝对 /my-library 前缀，避免相对路径拼接问题
  const getPath = (path) => {
    const normalizedPath = path === '/' ? '' : path.startsWith('/') ? path : `/${path}`;
    return `${LIBRARY_PATH}${normalizedPath}`.replace(/\/\/+/, '/');
  };
  
  // 检查当前路径是否激活
  const isActive = (path) => {
    const fullPath = `${LIBRARY_PATH}${path}`.replace(/\/\/+/, '/');
    return location.pathname === fullPath || location.pathname.startsWith(fullPath + '/');
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