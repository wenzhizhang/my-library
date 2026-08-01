import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import { API_BASE_URL } from './Config';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../AuthContext';
import PageLayout from './PageLayout';

const labelStyle = {
  display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 600, color: '#1d1d1f',
};

const inputStyle = {
  width: '100%',
  boxSizing: 'border-box',
  border: 'none',
  borderRadius: 8,
  padding: '12px 16px',
  background: 'rgba(0,0,0,0.04)',
  fontSize: 16,
  fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
  outline: 'none',
};

const ReadingPlans = () => {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const page = parseInt(searchParams.get('plansPage')) || 1;
  const limit = parseInt(searchParams.get('limit')) || 20;
  const sortBy = searchParams.get('sort_by') || 'name';

  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(1);
  const [totalPlans, setTotalPlans] = useState(0);

  const [searchQuery, setSearchQuery] = useState(searchParams.get('q') || '');
  const [submittedQuery, setSubmittedQuery] = useState(searchParams.get('q') || '');

  const [error, setError] = useState(null);
  const [confirmDelete, setConfirmDelete] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState(null);
  const [formData, setFormData] = useState({ name: '', intro: '', start_date: '', end_date: '' });
  const [saving, setSaving] = useState(false);

  const fetchPlans = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, limit, sort_by: sortBy };
      if (submittedQuery.trim()) {
        params.q = submittedQuery.trim();
      }
      const response = await axios.get(
        `${window.location.origin}${API_BASE_URL}/reading-plans/`, { params }
      );
      const data = response.data;
      setError(null);
      setPlans(data.reading_plans || []);
      setTotalPages(data.total_pages || 1);
      setTotalPlans(data.total_plans || 0);
    } catch (error) {
      console.error('Error fetching reading plans:', error);
      setError('Failed to load plans. Please try again.');
    }
    setLoading(false);
  }, [page, limit, sortBy, submittedQuery]);

  useEffect(() => {
    fetchPlans();
  }, [fetchPlans]);

  useEffect(() => {
    const q = searchParams.get('q') || '';
    if (q !== submittedQuery) {
      setSubmittedQuery(q);
      setSearchQuery(q);
    }
  }, []); // eslint-disable-line

  const setPageParam = (p) => {
    const next = new URLSearchParams(searchParams);
    next.set('plansPage', String(p));
    setSearchParams(next, { replace: true });
  };

  const setSortByParam = (s) => {
    const next = new URLSearchParams(searchParams);
    next.set('sort_by', s);
    next.set('plansPage', '1');
    setSearchParams(next, { replace: true });
  };

  const setLimitParam = (l) => {
    const next = new URLSearchParams(searchParams);
    next.set('limit', String(l));
    next.set('plansPage', '1');
    setSearchParams(next, { replace: true });
  };

  const openCreate = () => {
    setEditingItem(null);
    setFormData({ name: '', intro: '', start_date: '', end_date: '' });
    setModalOpen(true);
  };

  const openEdit = (plan) => {
    setEditingItem(plan);
    setFormData({
      name: plan.name || '',
      intro: plan.intro || '',
      start_date: plan.start_date || '',
      end_date: plan.end_date || '',
    });
    setModalOpen(true);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!formData.name.trim()) return;
    setSaving(true);
    const payload = {};
    for (const [k, v] of Object.entries(formData)) {
      if (v !== '' && v !== null && v !== undefined) {
        payload[k] = k === 'start_date' || k === 'end_date' ? (v || null) : v;
      }
    }
    // Remove empty date strings
    if (!payload.start_date) delete payload.start_date;
    if (!payload.end_date) delete payload.end_date;
    try {
      if (editingItem) {
        await axios.put(
          `${window.location.origin}${API_BASE_URL}/reading-plans/${editingItem.id}`,
          payload
        );
      } else {
        await axios.post(
          `${window.location.origin}${API_BASE_URL}/reading-plans/`,
          payload
        );
      }
      setModalOpen(false);
      fetchPlans();
    } catch (err) {
      const msg = err.response?.data?.detail || 'Save failed';
      alert(msg);
    }
    setSaving(false);
  };

  const handleDelete = async (planId) => {
    try {
      await axios.delete(`${window.location.origin}${API_BASE_URL}/reading-plans/${planId}`);
      setConfirmDelete(null);
      fetchPlans();
    } catch (err) {
      const msg = err.response?.data?.detail || 'Delete failed';
      alert(msg);
    }
  };

  const handleSearch = () => {
    const q = searchQuery.trim();
    setSubmittedQuery(q);
    const next = new URLSearchParams(searchParams);
    if (q) next.set('q', q); else next.delete('q');
    next.set('plansPage', '1');
    setSearchParams(next, { replace: true });
  };

  // Format date for display
  const formatDate = (d) => {
    if (!d) return null;
    return d;
  };

  const sortOptions = [
    { value: 'name', label: 'Name' },
    { value: 'id', label: 'ID' },
    { value: 'start_date', label: 'Start Date' },
    { value: 'progress', label: 'Progress' },
  ];

  const listColumns = [t('common.name'), 'Period', 'Progress', 'Books', 'Actions'];

  const renderItem = (item, viewMode) => {
    if (viewMode === 'list') {
      return (
        <tr key={item.id} onClick={() => navigate(`${item.id}`)}>
          <td className="list-cell-primary">{item.name}</td>
          <td className="list-cell-secondary" style={{ width: 180, fontSize: 12 }}>
            {item.start_date && item.end_date
              ? `${formatDate(item.start_date)} \u2014 ${formatDate(item.end_date)}`
              : item.start_date
                ? formatDate(item.start_date)
                : item.end_date
                  ? `\u2014 ${formatDate(item.end_date)}`
                  : ''}
          </td>
          <td style={{ width: 120 }}>
            {item.progress !== undefined && item.progress !== null && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <div style={{
                  flex: 1, height: 4, borderRadius: 2, background: '#e0e0e0',
                  overflow: 'hidden', minWidth: 50,
                }}>
                  <div style={{
                    height: '100%', width: `${Math.min(item.progress, 100)}%`,
                    background: '#34c759', borderRadius: 2,
                  }} />
                </div>
                <span style={{ fontSize: 11, color: '#86868b' }}>{item.progress}%</span>
              </div>
            )}
          </td>
          <td className="list-cell-secondary" style={{ width: 50, textAlign: 'center' }}>{item.total_books !== undefined ? item.total_books : ''}</td>
          {isAuthenticated && (
            <td style={{ width: 80, textAlign: 'right' }}>
              <button className="btn-pill-link" onClick={(e) => { e.stopPropagation(); openEdit(item); }}
                style={{ fontSize: 12, padding: '4px 8px' }}>
                {t('common.edit')}
              </button>
              <button className="btn-pill-link" onClick={(e) => { e.stopPropagation(); setConfirmDelete(item); }}
                style={{ fontSize: 12, padding: '4px 8px', color: '#ff3b30' }}>
                {t('common.delete')}
              </button>
            </td>
          )}
        </tr>
      );
    }
    return (
      <div key={item.id} className="card">
        <h3 className="card-title">{item.name}</h3>
        {item.intro && (
          <p className="caption">
            {item.intro.length > 100
              ? item.intro.substring(0, 100) + '...'
              : item.intro}
          </p>
        )}
        {item.start_date && item.end_date && (
          <p className="caption" style={{ fontSize: 13, color: '#86868b' }}>
            {formatDate(item.start_date)} — {formatDate(item.end_date)}
          </p>
        )}
        {item.total_books !== undefined && (
          <p className="caption">{item.total_books} {t('readingPlans.books')}</p>
        )}
        {item.progress !== undefined && item.progress !== null && (
          <div style={{ margin: '8px 0' }}>
            <div style={{
              height: 6, borderRadius: 3, background: '#e0e0e0',
              overflow: 'hidden',
            }}>
              <div style={{
                height: '100%', width: `${Math.min(item.progress, 100)}%`,
                background: '#34c759', borderRadius: 3,
                transition: 'width 0.3s ease',
              }} />
            </div>
            <span style={{ fontSize: 12, color: '#86868b' }}>{item.progress}%</span>
          </div>
        )}
        <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
          <button
            className="btn-pill-link"
            onClick={() => navigate(`${item.id}`)}
          >
            {t('common.view')}
          </button>
          {isAuthenticated && (
            <>
              <button className="btn-pill-link" onClick={() => openEdit(item)}>
                {t('common.edit')}
              </button>
              <button className="btn-pill-link"
                onClick={() => setConfirmDelete(item)}
                style={{ color: '#ff3b30' }}>
                {t('common.delete')}
              </button>
            </>
          )}
        </div>
      </div>
    );
  };

  if (loading) return <div className="loading">Loading...</div>;

  return (
    <>
      {error && (
        <div className="container" style={{ paddingTop: 40 }}>
          <div style={{
            background: '#fff0f0', border: '1px solid #ffc0c0', borderRadius: 8,
            padding: '0.75rem 1rem', marginBottom: '1rem',
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          }}>
            <span style={{ color: '#cc0000' }}>{error}</span>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <button onClick={() => { setError(null); fetchPlans(); }} className="btn-pill-link" style={{ padding: '0.25rem 0.75rem' }}>Retry</button>
              <button onClick={() => setError(null)} className="btn-pill-link" style={{ padding: '0.25rem 0.75rem' }}>Dismiss</button>
            </div>
          </div>
        </div>
      )}

      {!error && (
        <PageLayout
          title={t('readingPlans.title')}
          createButton={
            isAuthenticated ? (
              <button className="btn-pill-link" onClick={openCreate} style={{ marginBottom: 20 }}>
                {t('readingPlans.create')}
              </button>
            ) : null
          }
          searchValue={searchQuery}
          onSearchChange={setSearchQuery}
          onSearch={handleSearch}
          searchPlaceholder={t('common.search')}
          sortBy={sortBy}
          sortOptions={sortOptions}
          onSort={setSortByParam}
          limit={limit}
          onLimitChange={setLimitParam}
          page={page}
          totalPages={totalPages}
          totalItems={totalPlans}
          onPageChange={setPageParam}
          layoutKey="readingPlans"
          items={plans}
          renderItem={renderItem}
          listColumns={listColumns}
        />
      )}

      {/* ── Create / Edit Modal ─────────────────────────────── */}
      {modalOpen && (
        <div style={{
          position: 'fixed', inset: 0, zIndex: 10000,
          background: 'rgba(0,0,0,0.4)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
        }} onClick={() => setModalOpen(false)}>
          <div style={{
            background: '#fff',
            borderRadius: 20, padding: '24px 28px',
            width: Math.min(560, window.innerWidth - 32),
            maxHeight: '90vh', overflow: 'auto',
            boxShadow: '0 25px 50px rgba(0,0,0,0.25)',
          }} onClick={(e) => e.stopPropagation()}>
            <h2 style={{ margin: '0 0 20px', fontSize: 22, fontWeight: 600 }}>
              {editingItem ? t('readingPlans.edit') : t('readingPlans.create')}
            </h2>
            <form onSubmit={handleSave}>
              <div style={{ marginBottom: 14 }}>
                <label style={labelStyle}>
                  {t('common.name')} <span style={{ color: '#ff3b30' }}>*</span>
                </label>
                <input type="text" value={formData.name} required
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  style={inputStyle} placeholder="Plan name" />
              </div>
              <div style={{ marginBottom: 14 }}>
                <label style={labelStyle}>{t('common.introduction')}</label>
                <textarea value={formData.intro} rows={4}
                  onChange={(e) => setFormData({ ...formData, intro: e.target.value })}
                  style={{ ...inputStyle, resize: 'vertical' }} placeholder="Optional description..." />
              </div>
              <div style={{ display: 'flex', gap: 14, marginBottom: 14 }}>
                <div style={{ flex: 1 }}>
                  <label style={labelStyle}>{t('readingPlans.startDate')}</label>
                  <input type="date" value={formData.start_date}
                    onChange={(e) => setFormData({ ...formData, start_date: e.target.value })}
                    style={inputStyle} />
                </div>
                <div style={{ flex: 1 }}>
                  <label style={labelStyle}>{t('readingPlans.endDate')}</label>
                  <input type="date" value={formData.end_date}
                    onChange={(e) => setFormData({ ...formData, end_date: e.target.value })}
                    style={inputStyle} />
                </div>
              </div>
              <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
                <button type="button" className="btn-pill-link"
                  onClick={() => setModalOpen(false)}>{t('common.cancel')}</button>
                <button type="submit" className="btn-pill-link" disabled={saving}
                  style={saving ? { opacity: 0.6 } : {}}>
                  {saving ? t('common.saving') : t('common.save')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {confirmDelete && (
        <div style={{
          position: 'fixed', inset: 0, zIndex: 10001,
          background: 'rgba(0,0,0,0.4)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
        }} onClick={() => setConfirmDelete(null)}>
          <div style={{
            background: '#fff',
            borderRadius: 20, padding: '24px 28px',
            width: Math.min(560, window.innerWidth - 32),
            boxShadow: '0 25px 50px rgba(0,0,0,0.25)',
            textAlign: 'center',
          }} onClick={(e) => e.stopPropagation()}>
            <h3 style={{ margin: '0 0 8px', fontSize: 18 }}>
              Delete "{confirmDelete.name}"?
            </h3>
            <p style={{ color: '#86868b', margin: '0 0 20px', fontSize: 15 }}>
              {t('common.cannotUndo')}
            </p>
            <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
              <button className="btn-pill-link" onClick={() => setConfirmDelete(null)}>{t('common.cancel')}</button>
              <button className="btn-pill-link" onClick={() => handleDelete(confirmDelete.id)}
                style={{ color: '#ff3b30' }}>{t('common.deleteConfirm')}</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default ReadingPlans;
