import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../AuthContext';
import axios from 'axios';
import './Books.css';

const FORMATS = [
  { value: 'sql',      label: 'SQL',        ext: '.sql' },
  { value: 'csv',      label: 'CSV',         ext: '.csv' },
  { value: 'excel',    label: 'Excel',       ext: '.xlsx' },
  { value: 'markdown', label: 'Markdown',    ext: '.md' },
  { value: 'json',     label: 'JSON',        ext: '.json' },
];

const SCOPES = [
  'books', 'authors', 'publishers', 'brands', 'series',
  'categories', 'bookshelves', 'collections',
];

const ExportPage = () => {
  const { t } = useTranslation();
  const { isAuthenticated } = useAuth();
  const [format, setFormat] = useState('json');
  const [scope, setScope] = useState('books');
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState('');

  const handleExport = async () => {
    setExporting(true);
    setError('');
    try {
      const response = await axios.get('/api/export/', {
        params: { format, scope },
        responseType: 'blob',
      });

      const fmt = FORMATS.find((f) => f.value === format);
      const ext = fmt ? fmt.ext : `.${format}`;

      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `${scope}${ext}`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      if (err.response?.status === 401) {
        setError(t('export.loginRequired'));
      } else if (err.response?.data instanceof Blob) {
        try {
          const text = await err.response.data.text();
          const parsed = JSON.parse(text);
          setError(parsed.detail || t('common.error'));
        } catch {
          setError(t('common.error'));
        }
      } else {
        setError(err.response?.data?.detail || t('common.error'));
      }
    } finally {
      setExporting(false);
    }
  };


  return (
    <section className="section light">
      <div style={{ maxWidth: 520, margin: '0 auto', padding: '40px 20px' }}>
        <h1 style={{ fontSize: 28, fontWeight: 700, color: '#1d1d1f', marginBottom: 8 }}>
          {t('export.title')}
        </h1>

        {!isAuthenticated && (
          <div style={{
            backgroundColor: '#fff3cd', color: '#856404', borderRadius: 8,
            padding: '12px 16px', marginBottom: 24, fontSize: 14,
          }}>
            {t('export.loginRequired')}
          </div>
        )}

        <div style={{ marginBottom: 20 }}>
          <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#86868b', marginBottom: 6 }}>
            {t('export.format')}
          </label>
          <select
            value={format}
            onChange={(e) => setFormat(e.target.value)}
            disabled={!isAuthenticated}
            style={{
              width: '100%', padding: '10px 12px', borderRadius: 8,
              border: '1px solid #d2d2d7', fontSize: 15, color: '#1d1d1f',
              backgroundColor: isAuthenticated ? '#fff' : '#f5f5f7',
              outline: 'none', appearance: 'none',
            }}
          >
            {FORMATS.map((f) => (
              <option key={f.value} value={f.value}>{f.label}</option>
            ))}
          </select>
        </div>

        <div style={{ marginBottom: 28 }}>
          <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#86868b', marginBottom: 6 }}>
            {t('export.scope')}
          </label>
          <select
            value={scope}
            onChange={(e) => setScope(e.target.value)}
            disabled={!isAuthenticated}
            style={{
              width: '100%', padding: '10px 12px', borderRadius: 8,
              border: '1px solid #d2d2d7', fontSize: 15, color: '#1d1d1f',
              backgroundColor: isAuthenticated ? '#fff' : '#f5f5f7',
              outline: 'none', appearance: 'none',
            }}
          >
            {SCOPES.map((s) => (
              <option key={s} value={s}>{t(`export.scopes.${s}`)}</option>
            ))}
          </select>
        </div>

        {error && (
          <div style={{ color: '#c0392b', fontSize: 13, marginBottom: 16 }}>{error}</div>
        )}

        <button
          onClick={handleExport}
          disabled={!isAuthenticated || exporting}
          style={{
            width: '100%', padding: '12px 0', borderRadius: 10,
            border: 'none', backgroundColor: isAuthenticated ? '#0071e3' : '#a1a1a6',
            color: '#fff', fontSize: 16, fontWeight: 600, cursor: isAuthenticated ? 'pointer' : 'not-allowed',
            transition: 'background-color 0.2s',
          }}
        >
          {exporting ? t('export.exporting') : t('export.download')}
        </button>

      </div>
    </section>
  );
};

export default ExportPage;
