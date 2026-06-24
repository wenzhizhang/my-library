// src/components/Header.js
import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { LIBRARY_PATH } from '../config';
import i18n from '../i18n';
import './Header.css';
const Header = () => {
  const { t } = useTranslation();
  const { isAuthenticated, user, logout } = useAuth();
  const [visitCount, setVisitCount] = useState(null);
  const location = useLocation();

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
      <Link to={getPath('/')} className="nav-brand">
        <img src="/images/logo/logo-my-library.png" alt="" className="nav-brand-img" />
        <span className="nav-brand-text">{t('nav.home')}</span>
      </Link>
      <div className="nav-center">
        <div className="nav-menu">
          <Link to={getPath('/books')} className={`nav-link ${isActive('/books') ? 'active' : ''}`}>
            {t('nav.books')}
          </Link>
          <Link to={getPath('/authors')} className={`nav-link ${isActive('/authors') ? 'active' : ''}`}>
            {t('nav.authors')}
          </Link>
          <Link to={getPath('/publishers')} className={`nav-link ${isActive('/publishers') ? 'active' : ''}`}>
            {t('nav.publishers')}
          </Link>
          <Link to={getPath('/categories')} className={`nav-link ${isActive('/categories') ? 'active' : ''}`}>
            {t('nav.categories')}
          </Link>
          <Link to={getPath('/bookshelves')} className={`nav-link ${isActive('/bookshelves') ? 'active' : ''}`}>
            {t('nav.bookshelves')}
          </Link>
          <Link to={getPath('/book-collections')} className={`nav-link ${isActive('/book-collections') ? 'active' : ''}`}>
            {t('nav.collections')}
          </Link>
          <Link to={getPath('/reading-plans')} className={`nav-link ${isActive('/reading-plans') ? 'active' : ''}`}>
            {t('nav.readingPlans')}
          </Link>
          <Link to={getPath('/brands')} className={`nav-link ${isActive('/brands') ? 'active' : ''}`}>
            {t('nav.brands')}
          </Link>
          <Link to={getPath('/series')} className={`nav-link ${isActive('/series') ? 'active' : ''}`}>
            {t('nav.series')}
          </Link>
          <Link to={getPath('/stats')} className={`nav-link ${isActive('/stats') ? 'active' : ''}`}>
            {t('nav.stats')}
          </Link>
        </div>
      </div>
      <div className="nav-right">
        <div className="nav-lang">
          <select value={i18n.language} onChange={(e) => i18n.changeLanguage(e.target.value)}
            style={{ padding: '4px 8px', borderRadius: 6, border: '1px solid rgba(255,255,255,0.2)', background: 'transparent', color: '#fff', fontSize: 13, cursor: 'pointer', outline: 'none' }}>
            <option value="zh-CN">简</option>
            <option value="zh-TW">繁</option>
            <option value="en">EN</option>
          </select>
        </div>
        {isAuthenticated ? (
          <div className="nav-auth-user">
            <span className="nav-username">{user?.username}</span>
            <button className="nav-logout" onClick={logout}>{t('nav.logout')}</button>
          </div>
        ) : (
          <Link to={`${LIBRARY_PATH}/login`} className="nav-link nav-login-link">{t('nav.login')}</Link>
        )}
        {visitCount !== null && (
          <span className="nav-visit-count">{visitCount} visits</span>
        )}
      </div>
    </nav>
  );
};
export default Header;
