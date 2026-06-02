import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { LIBRARY_PATH } from '../config';
import './Auth.css';

const Register = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (password !== confirm) return setError('Passwords do not match');
    setLoading(true);
    try {
      const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.detail || 'Registration failed');
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
        <h2 className="auth-title">Register</h2>
        {error && <p className="auth-error">{error}</p>}
        <label className="auth-label">
          <span>Username</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} required />
        </label>
        <label className="auth-label">
          <span>Password</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        <label className="auth-label">
          <span>Confirm Password</span>
          <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
        </label>
        <button className="auth-btn" disabled={loading}>
          {loading ? 'Registering…' : 'Register'}
        </button>
        <button type="button" className="auth-btn auth-btn-guest" onClick={handleGuest}>
          Continue as Guest
        </button>
        <p className="auth-footer">
          Already have an account? <Link to={`${LIBRARY_PATH}/login`}>Login</Link>
        </p>
      </form>
    </div>
  );
};

export default Register;
