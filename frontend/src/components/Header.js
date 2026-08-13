import React, { useState, useEffect, useRef, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { LIBRARY_PATH } from '../config';
import i18n from '../i18n';
import './Header.css';
const Header = () => {
  const triggerRef = useRef(null);
  const mobileMenuRef = useRef(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [menuPos, setMenuPos] = useState({ top: 0, right: 0 });
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const { t } = useTranslation();
  const { isAuthenticated, user, logout } = useAuth();
  const [visitCount, setVisitCount] = useState(null);
  const location = useLocation();

  // Close mobile menu on navigation
  useEffect(() => {
    setMobileMenuOpen(false);
  }, [location.pathname]);

  // Report page view on every navigation, update displayed count
  useEffect(() => {
    fetch('/api/stats/page-view', { method: 'POST' })
      .then((r) => r.json())
      .then((data) => setVisitCount(data.total_visits))
      .catch(() => {});
  }, [location.pathname]);
  // Close dropdowns on outside click (trigger, both portals) and Escape
  useEffect(() => {
    const handleClick = (e) => {
      const portal = document.getElementById('nav-user-menu-portal');
      const hitTrigger = triggerRef.current?.contains(e.target);
      const hitPortal = portal?.contains(e.target);
      const hitMobile = mobileMenuRef.current?.contains(e.target);
      const hitHamburger = e.target.closest?.('.nav-hamburger');
      if (!hitTrigger && !hitPortal) {
        setMenuOpen(false);
      }
      if (!hitMobile && !hitHamburger) {
        setMobileMenuOpen(false);
      }
    };
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        setMenuOpen(false);
        setMobileMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClick);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handleClick);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, []);
  const toggleMenu = useCallback(() => {
    if (!menuOpen && triggerRef.current) {
      const rect = triggerRef.current.getBoundingClientRect();
      setMenuPos({ top: rect.bottom + 6, right: window.innerWidth - rect.right });
    }
    setMenuOpen(!menuOpen);
  }, [menuOpen]);
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

  const navItems = [
    { path: '/books', label: t('nav.books') },
    { path: '/authors', label: t('nav.authors') },
    { path: '/publishers', label: t('nav.publishers') },
    { path: '/categories', label: t('nav.categories') },
    { path: '/bookshelves', label: t('nav.bookshelves') },
    { path: '/book-collections', label: t('nav.collections') },
    { path: '/reading-plans', label: t('nav.readingPlans') },
    { path: '/brands', label: t('nav.brands') },
    { path: '/series', label: t('nav.series') },
    { path: '/wishlist', label: t('nav.wishlist') },
    { path: '/stats', label: t('nav.stats') },
  ];

  return (
    <>
    <nav className="nav">
      <Link to={getPath('/')} className="nav-brand">
        <img src="/images/logo/logo-my-library.png" alt="" className="nav-brand-img" />
        <span className="nav-brand-text">{t('nav.home')}</span>
      </Link>
      <div className="nav-center">
        <div className="nav-menu">
          {navItems.map((item) => (
            <Link
              key={item.path}
              to={getPath(item.path)}
              className={`nav-link ${isActive(item.path) ? 'active' : ''}`}
            >
              {item.label}
            </Link>
          ))}
        </div>
      </div>
      <div className="nav-right">
        <button
          type="button"
          className="nav-hamburger"
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          aria-label={t('nav.menu')}
          aria-expanded={mobileMenuOpen}
          aria-controls="nav-mobile-menu"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            {mobileMenuOpen ? (
              <path d="M18 6L6 18M6 6l12 12" />
            ) : (
              <path d="M3 6h18M3 12h18M3 18h18" />
            )}
          </svg>
        </button>
        <div className="nav-lang">
          <select value={i18n.language} onChange={(e) => i18n.changeLanguage(e.target.value)}
            style={{ padding: '4px 8px', borderRadius: 6, border: '1px solid rgba(255,255,255,0.2)', background: 'transparent', color: '#fff', fontSize: 13, cursor: 'pointer', outline: 'none' }}>
            <option value="zh-CN">简</option>
            <option value="zh-TW">繁</option>
            <option value="en">EN</option>
          </select>
        </div>
        {isAuthenticated ? (
          <div className="nav-user-menu">
            <span
              ref={triggerRef}
              className="nav-user-menu-trigger"
              onClick={toggleMenu}
            >
              {user?.username}
            </span>
          </div>
        ) : (
          <Link to={`${LIBRARY_PATH}/login`} className="nav-link nav-login-link">{t('nav.login')}</Link>
        )}
        {visitCount !== null && (
          <span className="nav-visit-count">{visitCount} visits</span>
        )}
        {menuOpen && createPortal(
          <div
            id="nav-user-menu-portal"
            className="nav-user-menu-dropdown"
            style={{ position: 'fixed', top: menuPos.top, right: menuPos.right }}
          >
            <Link
              to={getPath('/archived')}
              className="nav-user-menu-item"
              onClick={() => setMenuOpen(false)}
            >
              {t('archived.title')}
            </Link>
            <Link
              to={getPath('/export')}
              className="nav-user-menu-item"
              onClick={() => setMenuOpen(false)}
            >
              {t('nav.export')}
            </Link>
            <button
              className="nav-user-menu-item"
              onClick={() => { setMenuOpen(false); logout(); }}
            >
              {t('nav.logout')}
            </button>
          </div>,
          document.body
        )}
      </div>
    </nav>
    {mobileMenuOpen && createPortal(
      <div
        id="nav-mobile-menu"
        ref={mobileMenuRef}
        className="nav-mobile-menu open"
      >
        {navItems.map((item) => (
          <Link
            key={item.path}
            to={getPath(item.path)}
            className={`nav-link ${isActive(item.path) ? 'active' : ''}`}
            onClick={() => setMobileMenuOpen(false)}
          >
            {item.label}
          </Link>
        ))}
      </div>,
      document.body
    )}
    </>
  );
};
export default Header;
