import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { LIBRARY_PATH } from '../config';
import './Auth.css';

const Login = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

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
        <h2 className="auth-title">Login</h2>
        {error && <p className="auth-error">{error}</p>}
        <label className="auth-label">
          <span>Username</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} required />
        </label>
        <label className="auth-label">
          <span>Password</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        <button className="auth-btn" disabled={loading}>
          {loading ? 'Logging in…' : 'Login'}
        </button>
        <button type="button" className="auth-btn auth-btn-guest" onClick={handleGuest}>
          Continue as Guest
        </button>
        <p className="auth-footer">
          Don't have an account? <Link to={`${LIBRARY_PATH}/register`}>Register</Link>
        </p>
      </form>
    </div>
  );
};

export default Login;
