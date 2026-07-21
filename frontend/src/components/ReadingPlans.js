import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import { API_BASE_URL } from './Config';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../AuthContext';

const ReadingPlans = () => {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const page = parseInt(searchParams.get('plansPage')) || 1;
  const limit = parseInt(searchParams.get('limit')) || 10;
  const sortBy = searchParams.get('sort_by') || 'name';

  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(1);
  const [totalPlans, setTotalPlans] = useState(0);

  const [searchQuery, setSearchQuery] = useState(searchParams.get('q') || '');
  const [submittedQuery, setSubmittedQuery] = useState(searchParams.get('q') || '');

  const [goToPage, setGoToPage] = useState("");
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

  const handleSortChange = (newSortBy) => {
    setSortByParam(newSortBy);
  };

  const handleLimitChange = (newLimit) => {
    setLimitParam(parseInt(newLimit));
  };

  const handleGoToPage = () => {
    const pageNum = Math.min(Math.max(parseInt(goToPage) || 1, 1), totalPages);
    setPageParam(pageNum);
    setGoToPage('');
  };

  const handleSearch = () => {
    const q = searchQuery.trim();
    setSubmittedQuery(q);
    const next = new URLSearchParams(searchParams);
    if (q) next.set('q', q); else next.delete('q');
    next.set('plansPage', '1');
    setSearchParams(next, { replace: true });
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  // Format date for display
  const formatDate = (d) => {
    if (!d) return null;
    return d;
  };

  const pages = [];
  const startPage = Math.max(1, page - 2);
  const endPage = Math.min(totalPages, page + 2);

  for (let i = startPage; i <= endPage; i++) {
    pages.push(
      <button
        key={i}
        className={`btn-pill-link ${i === page ? 'active' : ''}`}
        onClick={() => setPageParam(i)}
      >
        {i}
      </button>
    );
  }

  if (loading) {
    return <div className="loading">Loading...</div>;
  }

  return (
    <section className="section light">
      <div className="container">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h1 className="section-heading">{t('readingPlans.title')}</h1>
          {isAuthenticated && (
            <button className="btn-pill-link" onClick={openCreate} style={{ marginBottom: 20 }}>
              {t('readingPlans.create')}
            </button>
          )}
        </div>

        <div className="toolbar">
          <div className="toolbar-search">
            <div className="toolbar-search-row">
              <input className="toolbar-search-input" placeholder={t('common.search')}
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyDown={handleKeyDown} />
              <button className="btn-pill-link" onClick={handleSearch}>{t('common.search')}</button>
            </div>
          </div>
          <div className="toolbar-actions">
            <label className="control-label">
              <span className="control-label-text">{t('common.sort')}</span>
              <select value={sortBy} onChange={(e) => handleSortChange(e.target.value)}>
                <option value="name">Name</option>
                <option value="id">ID</option>
                <option value="start_date">Start Date</option>
                <option value="progress">Progress</option>
              </select>
            </label>
            <label className="control-label">
              <span className="control-label-text">{t('common.perPage')}</span>
              <select value={limit} onChange={(e) => handleLimitChange(e.target.value)}>
                <option value="5">5</option>
                <option value="10">10</option>
                <option value="20">20</option>
                <option value="50">50</option>
              </select>
            </label>
          </div>
          <div className="toolbar-info">
            {t('common.page')} {page} {t('common.of')} {totalPages} ({t('common.total')} {totalPlans})
          </div>
        </div>

        {error && (
          <div style={{background: '#fff0f0', border: '1px solid #ffc0c0', borderRadius: '8px', padding: '0.75rem 1rem', marginBottom: '1rem', display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
            <span style={{color: '#cc0000'}}>{error}</span>
            <div style={{display: 'flex', gap: '0.5rem'}}>
              <button onClick={() => { setError(null); fetchPlans(); }} className="btn-pill-link" style={{padding: '0.25rem 0.75rem'}}>Retry</button>
              <button onClick={() => setError(null)} className="btn-pill-link" style={{padding: '0.25rem 0.75rem'}}>Dismiss</button>
            </div>
          </div>
        )}
        {!error && plans.length === 0 && (
          <p style={{textAlign: 'center', color: '#888', fontSize: '1.1rem', margin: '2rem 0'}}>No reading plans yet.</p>
        )}
        {plans.length > 0 && (
        <div className="grid">
          {plans.map(plan => (
            <div key={plan.id} className="card">
              <h3 className="card-title">{plan.name}</h3>
              {plan.intro && (
                <p className="caption">
                  {plan.intro.length > 100
                    ? plan.intro.substring(0, 100) + '...'
                    : plan.intro}
                </p>
              )}
              {plan.start_date && plan.end_date && (
                <p className="caption" style={{ fontSize: 13, color: '#86868b' }}>
                  {formatDate(plan.start_date)} — {formatDate(plan.end_date)}
                </p>
              )}
              {plan.total_books !== undefined && (
                <p className="caption">{plan.total_books} {t('readingPlans.books')}</p>
              )}
              {plan.progress !== undefined && plan.progress !== null && (
                <div style={{ margin: '8px 0' }}>
                  <div style={{
                    height: 6, borderRadius: 3, background: '#e0e0e0',
                    overflow: 'hidden',
                  }}>
                    <div style={{
                      height: '100%', width: `${Math.min(plan.progress, 100)}%`,
                      background: '#34c759', borderRadius: 3,
                      transition: 'width 0.3s ease',
                    }} />
                  </div>
                  <span style={{ fontSize: 12, color: '#86868b' }}>{plan.progress}%</span>
                </div>
              )}
              <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                <button
                  className="btn-pill-link"
                  onClick={() => navigate(`${plan.id}`)}
                >
                  {t('common.view')}
                </button>
                {isAuthenticated && (
                  <>
                    <button className="btn-pill-link" onClick={() => openEdit(plan)}>
                      {t('common.edit')}
                    </button>
                    <button className="btn-pill-link"
                      onClick={() => setConfirmDelete(plan)}
                      style={{ color: '#ff3b30' }}>
                      {t('common.delete')}
                    </button>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
        )}

        <div className="pagination">
          <div className="pagination-links">
            {page > 1 && (
              <>
                <button className="btn-pill-link" onClick={() => setPageParam(1)}>{t('common.first')}</button>
                <button className="btn-pill-link" onClick={() => setPageParam(page - 1)}>{t('common.previous')}</button>
              </>
            )}

            {pages}

            {page < totalPages && (
              <>
                <button className="btn-pill-link" onClick={() => setPageParam(page + 1)}>{t('common.next')}</button>
                <button className="btn-pill-link" onClick={() => setPageParam(totalPages)}>{t('common.last')}</button>
              </>
            )}
          </div>

          <div className="pagination-input">
            <input
              type="number"
              id="page-input"
              min="1"
              max={totalPages}
              value={goToPage}
              onChange={(e) => setGoToPage(e.target.value)}
              onKeyPress={(e) => { if (e.key === 'Enter') handleGoToPage(); }}
            />
            <button className="btn-pill-link" onClick={handleGoToPage}>{t('common.goToPage')}</button>
          </div>
      </div>
      </div>

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
    </section>
  );
};

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

export default ReadingPlans;
