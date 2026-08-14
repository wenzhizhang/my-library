import React from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { useBackground } from '../BackgroundContext';
import { LIBRARY_PATH } from '../config';
import './Settings.css';

const Settings = () => {
  const { t } = useTranslation();
  const { isAuthenticated } = useAuth();
  const { backgrounds, defaultId, selectedId, current, savingId, error, setBackground } =
    useBackground();

  const activeId = selectedId || defaultId;

  return (
    <div className="settings-page">
      <div className="container">
        <h1 className="settings-title">{t('settings.title')}</h1>

        <section className="settings-section">
          <h2 className="settings-section-title">{t('settings.backgroundSection')}</h2>
          <p className="settings-section-hint">{t('settings.backgroundHint')}</p>

          {!isAuthenticated && (
            <p className="settings-guest-hint">
              {t('settings.guestHint')}{' '}
              <Link to={`${LIBRARY_PATH}/login`} className="settings-login-link">
                {t('nav.login')}
              </Link>
            </p>
          )}

          {error === 'authRequired' && (
            <p className="settings-error">{t('settings.authRequired')}</p>
          )}
          {error === 'saveFailed' && (
            <p className="settings-error">{t('settings.saveFailed')}</p>
          )}

          {backgrounds.length === 0 ? (
            <p className="settings-loading">{t('common.loading')}</p>
          ) : (
            <div className="settings-bg-grid">
              {backgrounds.map((bg) => {
                const isActive = bg.id === activeId;
                const isSaving = savingId === bg.id;
                return (
                  <button
                    key={bg.id}
                    type="button"
                    className={`settings-bg-card ${isActive ? 'active' : ''}`}
                    onClick={() => setBackground(bg.id)}
                    disabled={!isAuthenticated || savingId !== null}
                    aria-pressed={isActive}
                    aria-label={bg.name}
                  >
                    <span
                      className="settings-bg-thumb"
                      style={{ backgroundImage: `url("${bg.url}")` }}
                    />
                    <span className="settings-bg-name">{bg.name}</span>
                    {isSaving && <span className="settings-bg-saving">{t('settings.saving')}</span>}
                    {isActive && !isSaving && (
                      <span className="settings-bg-check" aria-hidden="true">
                        ✓
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          )}

          {current && (
            <p className="settings-current">
              {t('settings.currentBackground')}: {current.name}
            </p>
          )}
        </section>
      </div>
    </div>
  );
};

export default Settings;
