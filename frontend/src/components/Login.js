import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { LIBRARY_PATH } from '../config';
import { useTranslation } from 'react-i18next';
import './Auth.css';

const Login = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();
  const { t } = useTranslation();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.detail || 'Login failed');
      login(data.access_token, { id: data.user_id, username: data.username, uuid: data.uuid });
      navigate(`${LIBRARY_PATH}/books`);
    } catch (err) {
      setError(err.message);
    }
    setLoading(false);
  };

  const handleGuest = () => {
    navigate(`${LIBRARY_PATH}/books`);
  };

  return (
    <div className="auth-page">
      <form className="auth-form" onSubmit={handleSubmit}>
        <h2 className="auth-title">{t('nav.login')}</h2>
        {error && <p className="auth-error">{error}</p>}
        <label className="auth-label">
          <span>{t('auth.username')}</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} required />
        </label>
        <label className="auth-label">
          <span>{t('auth.password')}</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        <button className="auth-btn" disabled={loading}>
          {loading ? t('common.loading') : t('nav.login')}
        </button>
        <button type="button" className="auth-btn auth-btn-guest" onClick={handleGuest}>
          {t('auth.guest')}
        </button>
        <p className="auth-footer">
          {t('auth.noAccount')} <Link to={`${LIBRARY_PATH}/register`}>{t('nav.register')}</Link>
        </p>
      </form>
    </div>
  );
};

export default Login;
