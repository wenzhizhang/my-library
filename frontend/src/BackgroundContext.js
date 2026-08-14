import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  useLayoutEffect,
  useMemo,
  useCallback,
} from 'react';
import axios from 'axios';
import { useAuth } from './AuthContext';

const BackgroundContext = createContext(null);

const BACKGROUND_VAR = '--my-library-bg-url';

function toCssUrl(url) {
  return `url("${String(url).replace(/"/g, '\\"')}")`;
}

export function BackgroundProvider({ children }) {
  const { isAuthenticated } = useAuth();
  const [backgrounds, setBackgrounds] = useState([]);
  const [defaultId, setDefaultId] = useState('');
  // null = not loaded yet (keeps the CSS fallback until we know)
  const [selectedId, setSelectedId] = useState(null);
  const [savingId, setSavingId] = useState(null);
  const [error, setError] = useState(null);

  // Guests must never see anyone's personal background — reset synchronously
  // (layout phase) when the session changes, so no painted frame leaks the
  // previous user's background; then refetch the (guest → null) selection.
  useLayoutEffect(() => {
    if (!isAuthenticated) setSelectedId(null);
  }, [isAuthenticated]);

  useEffect(() => {
    let cancelled = false;
    axios
      .get('/api/backgrounds')
      .then((res) => {
        if (cancelled) return;
        setBackgrounds(res.data.backgrounds || []);
        setDefaultId(res.data.default_id || '');
      })
      .catch(() => {});
    axios
      .get('/api/backgrounds/me')
      .then((res) => {
        if (cancelled) return;
        setSelectedId(res.data.background_id ?? null);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated]);

  const current = useMemo(() => {
    if (!backgrounds.length) return null;
    const chosen = backgrounds.find((b) => b.id === selectedId);
    if (chosen) return chosen;
    const def = backgrounds.find((b) => b.id === defaultId);
    return def || backgrounds[0];
  }, [backgrounds, defaultId, selectedId]);

  // Layout phase: the var must be in place before the browser paints the
  // frame that shows the new background (and, on logout, hides the old one).
  useLayoutEffect(() => {
    if (current) {
      document.documentElement.style.setProperty(BACKGROUND_VAR, toCssUrl(current.url));
    }
  }, [current]);

  const setBackground = useCallback(
    async (id) => {
      const prev = selectedId;
      setSelectedId(id);
      setSavingId(id);
      setError(null);
      try {
        await axios.put('/api/backgrounds/me', { background_id: id });
        setSelectedId(id);
      } catch (err) {
        setSelectedId(prev);
        setError(err.response?.status === 401 ? 'authRequired' : 'saveFailed');
      } finally {
        setSavingId(null);
      }
    },
    [selectedId]
  );

  const value = useMemo(
    () => ({ backgrounds, defaultId, selectedId, current, savingId, error, setBackground }),
    [backgrounds, defaultId, selectedId, current, savingId, error, setBackground]
  );

  return <BackgroundContext.Provider value={value}>{children}</BackgroundContext.Provider>;
}

export function useBackground() {
  const ctx = useContext(BackgroundContext);
  if (!ctx) throw new Error('useBackground must be used within BackgroundProvider');
  return ctx;
}
