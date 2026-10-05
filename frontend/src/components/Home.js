import React, { useState, useEffect, useRef, useCallback } from 'react';
import axios from 'axios';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { API_BASE_URL } from './Config';
import { LIBRARY_PATH } from '../config';
import './Home.css';

// One turning sheet per headline number. `field` is the API's snake_case column,
// `label` is the i18n key — they are not interchangeable. Order matters: the
// sheets riffle top-down.
const PAGES = [
  { field: 'total_books', label: 'totalBooks', nav: 'books', to: '/books' },
  { field: 'total_authors', label: 'totalAuthors', nav: 'authors', to: '/authors' },
  { field: 'total_publishers', label: 'totalPublishers', nav: 'publishers', to: '/publishers' },
  { field: 'total_categories', label: 'categories', nav: 'categories', to: '/categories' },
];

// ponytail: jsdom and old browsers have no matchMedia, and the preference can
// flip at runtime — read it live rather than caching it once.
const reducedMotion = () =>
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** Counts 0 → value with an ease-out so the number lands softly instead of snapping. */
function CountUp({ value, duration = 1200 }) {
  const [shown, setShown] = useState(0);

  useEffect(() => {
    if (!value || reducedMotion()) {
      setShown(value);
      return undefined;
    }
    let raf;
    let start;
    const step = (ts) => {
      if (start === undefined) start = ts;
      const p = Math.min((ts - start) / duration, 1);
      setShown(Math.round(value * (1 - (1 - p) ** 3)));
      if (p < 1) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [value, duration]);

  return <>{shown.toLocaleString()}</>;
}

const Home = () => {
  const { t } = useTranslation();
  const [overview, setOverview] = useState(null);
  const stageRef = useRef(null);
  // Pointer position is written straight to CSS custom properties — putting it
  // in state would re-render the whole tree on every pointermove.
  const rectRef = useRef(null);

  useEffect(() => {
    axios
      .get(`${window.location.origin}${API_BASE_URL}/stats/books`)
      .then((res) => setOverview(res.data?.overview || null))
      .catch(() => setOverview(null));
  }, []);

  const cacheRect = useCallback(() => {
    if (stageRef.current) rectRef.current = stageRef.current.getBoundingClientRect();
  }, []);

  const onPointerMove = useCallback((e) => {
    const el = stageRef.current;
    const r = rectRef.current;
    if (!el || !r || reducedMotion()) return;
    el.style.setProperty('--tilt-x', `${(0.5 - (e.clientY - r.top) / r.height) * 7}deg`);
    el.style.setProperty('--tilt-y', `${((e.clientX - r.left) / r.width - 0.5) * 12}deg`);
  }, []);

  const resetTilt = useCallback(() => {
    const el = stageRef.current;
    if (!el) return;
    el.style.setProperty('--tilt-x', '0deg');
    el.style.setProperty('--tilt-y', '0deg');
  }, []);

  const valueOf = (field) => overview?.[field] ?? 0;
  const prefix = LIBRARY_PATH === '/' ? '' : LIBRARY_PATH;
  const getPath = (path) => (path === '/' ? prefix || '/' : `${prefix}${path}`);

  return (
    <div className="home">
      <section className="home-hero">
        <div
          className="book-stage"
          ref={stageRef}
          onPointerEnter={cacheRect}
          onPointerMove={onPointerMove}
          onPointerLeave={resetTilt}
        >
          <div className="book">
            <div className="book__spine" />
            {PAGES.map((p, i) => (
              <div key={p.field} className="page page--turn" style={{ '--i': i }}>
                <div className="page__face page__face--front">
                  <div className="page__half">
                    <span className="page__rule" />
                    <span className="page__chapter">{t(`nav.${p.nav}`)}</span>
                  </div>
                  <div className="page__half">
                    <span className="page__label">{t(`stats.${p.label}`)}</span>
                    <span className="page__num">
                      <CountUp key={valueOf(p.field)} value={valueOf(p.field)} />
                    </span>
                  </div>
                </div>
                <div className="page__face page__face--back">
                  <div className="page__half">
                    <p className="page__blurb">{t('home.tagline')}</p>
                  </div>
                  <div className="page__half">
                    <Link className="page__cta" to={getPath(p.to)}>
                      {t('home.open')} {t(`nav.${p.nav}`)} →
                    </Link>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        <p className="home-hint">{t('home.flipHint')}</p>
      </section>

      <section className="home-stats">
        {PAGES.map((p) => (
          <Link key={p.field} className="home-stat" to={getPath(p.to)}>
            <span className="home-stat__label">{t(`stats.${p.label}`)}</span>
            <span className="home-stat__value">
              <CountUp key={valueOf(p.field)} value={valueOf(p.field)} />
            </span>
          </Link>
        ))}
      </section>
    </div>
  );
};

export default Home;