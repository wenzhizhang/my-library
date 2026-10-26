import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import axios from 'axios';

const AuthContext = createContext(null);
const TOKEN_KEY = 'my_library_token';
const GUEST_KEY = 'my_library_guest';

axios.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(TOKEN_KEY);
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
  },
  (error) => Promise.reject(error)
);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY));
  const [user, setUser] = useState(null);
  const [isGuest, setIsGuest] = useState(() => localStorage.getItem(GUEST_KEY) === '1');

  const exitGuest = useCallback(() => {
    localStorage.removeItem(GUEST_KEY);
    setIsGuest(false);
  }, []);

  useEffect(() => {
    if (token) {
      // An account session always wins over an earlier guest session.
      exitGuest();
      localStorage.setItem(TOKEN_KEY, token);
      fetch('/api/auth/me', {
        headers: { Authorization: `Bearer ${token}` },
      })
        .then((r) => (r.ok ? r.json() : null))
        .then((data) => setUser(data))
        .catch(() => {});
    } else {
      localStorage.removeItem(TOKEN_KEY);
      setUser(null);
    }
  }, [token, exitGuest]);

  const login = (accessToken, userData) => {
    exitGuest();
    setToken(accessToken);
    setUser(userData);
  };

  const enterGuest = () => {
    localStorage.setItem(GUEST_KEY, '1');
    setIsGuest(true);
  };

  const logout = () => {
    exitGuest();
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        token,
        user,
        login,
        logout,
        enterGuest,
        isGuest,
        isAuthenticated: !!token,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
