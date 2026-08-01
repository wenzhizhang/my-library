import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import { API_BASE_URL, MEDIA_BASE_URL } from './Config';
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

const Publishers = () => {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const page = parseInt(searchParams.get('page')) || 1;
  const limit = parseInt(searchParams.get('limit')) || 20;
  const sortBy = searchParams.get('sort_by') || 'name';

  const [publishers, setPublishers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(1);
  const [totalPublishers, setTotalPublishers] = useState(0);

  const [searchQuery, setSearchQuery] = useState(searchParams.get('q') || '');
  const [submittedQuery, setSubmittedQuery] = useState(searchParams.get('q') || '');

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editingPublisher, setEditingPublisher] = useState(null);
  const [formData, setFormData] = useState({ name: '', intro: '', logo: '' });
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(null);

  const fetchPublishers = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, limit, sort_by: sortBy };
      if (submittedQuery.trim()) params.q = submittedQuery.trim();
      const response = await axios.get(`${window.location.origin}${API_BASE_URL}/publishers/`, { params });
      const data = response.data;
      setPublishers(data.publishers || []);
      setTotalPages(data.total_pages || 1);
      setTotalPublishers(data.total_publishers || 0);
    } catch (error) {
      console.error('Error fetching publishers:', error);
    }
    setLoading(false);
  }, [page, limit, sortBy, submittedQuery]);

  useEffect(() => {
    fetchPublishers();
  }, [fetchPublishers]);

  // Sync submittedQuery from URL on mount
  useEffect(() => {
    const q = searchParams.get('q') || '';
    if (q !== submittedQuery) {
      setSubmittedQuery(q);
      setSearchQuery(q);
    }
  }, []); // eslint-disable-line

  const setPageParam = (p) => {
    const next = new URLSearchParams(searchParams);
    next.set('page', String(p));
    setSearchParams(next, { replace: true });
  };
  const setSortByParam = (s) => {
    const next = new URLSearchParams(searchParams);
    next.set('sort_by', s);
    next.set('page', '1');
    setSearchParams(next, { replace: true });
  };
  const setLimitParam = (l) => {
    const next = new URLSearchParams(searchParams);
    next.set('limit', String(l));
    next.set('page', '1');
    setSearchParams(next, { replace: true });
  };

  const handleSearch = () => {
    const q = searchQuery.trim();
    setSubmittedQuery(q);
    const next = new URLSearchParams(searchParams);
    if (q) next.set('q', q); else next.delete('q');
    next.set('page', '1');
    setSearchParams(next, { replace: true });
  };

  // ── Modal helpers ──────────────────────────────────────────
  const openCreate = () => {
    setEditingPublisher(null);
    setFormData({ name: '', intro: '', logo: '' });
    setModalOpen(true);
  };
  const openEdit = (publisher) => {
    setEditingPublisher(publisher);
    setFormData({ name: publisher.name || '', intro: publisher.intro || '', logo: publisher.logo || '' });
    setModalOpen(true);
  };
  const handleSave = async (e) => {
    e.preventDefault();
    if (!formData.name.trim()) return;
    setSaving(true);
    const payload = {};
    for (const [k, v] of Object.entries(formData)) {
      if (v !== '' && v !== null && v !== undefined) payload[k] = v;
    }
    try {
      if (editingPublisher) {
        await axios.put(`${window.location.origin}${API_BASE_URL}/publishers/${editingPublisher.id}`, payload);
      } else {
        await axios.post(`${window.location.origin}${API_BASE_URL}/publishers/`, payload);
      }
      setModalOpen(false);
      fetchPublishers();
    } catch (err) {
      const msg = err.response?.data?.detail || 'Save failed';
      alert(msg);
    }
    setSaving(false);
  };
  const handleDelete = async (publisherId) => {
    try {
      await axios.delete(`${window.location.origin}${API_BASE_URL}/publishers/${publisherId}`);
      setConfirmDelete(null);
      fetchPublishers();
    } catch (err) {
      const msg = err.response?.data?.detail || 'Delete failed';
      alert(msg);
    }
  };

  const sortOptions = [
    { value: 'id', label: 'ID' },
    { value: 'name', label: t('common.name') },
  ];

  const listColumns = ['', t('common.name'), t('common.introduction'), 'Actions'];

  const renderItem = (publisher, viewMode) => {
    if (viewMode === 'list') {
      return (
        <tr key={publisher.id} onClick={() => navigate(`${publisher.id}`)}>
          <td style={{ width: 44, padding: '6px 4px' }}>
            {publisher.logo && (
              <img src={publisher.logo.startsWith('http') ? publisher.logo : `${MEDIA_BASE_URL}/${publisher.logo}`}
                alt={publisher.name} style={{ width: 36, height: 36, objectFit: 'contain', borderRadius: 4, display: 'block' }} />
            )}
          </td>
          <td className="list-cell-primary">{publisher.name}</td>
          <td className="list-cell-secondary" style={{ maxWidth: 300, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{publisher.intro || ''}</td>
          {isAuthenticated && (
            <td style={{ width: 80, textAlign: 'right' }}>
              <button className="btn-pill-link" onClick={(e) => { e.stopPropagation(); openEdit(publisher); }}
                style={{ fontSize: 12, padding: '4px 8px' }}>
                {t('common.edit')}
              </button>
              <button className="btn-pill-link" onClick={(e) => { e.stopPropagation(); setConfirmDelete(publisher); }}
                style={{ fontSize: 12, padding: '4px 8px', color: '#ff3b30' }}>
                {t('common.delete')}
              </button>
            </td>
          )}
        </tr>
      );
    }
    return (
      <div key={publisher.id} className="card">
        {publisher.logo && (
          <img src={publisher.logo.startsWith('http') ? publisher.logo : `${MEDIA_BASE_URL}/${publisher.logo}`}
            alt={publisher.name}
            className="card-image" style={{ maxHeight: 120, objectFit: 'contain' }} />
        )}
        <h3 className="card-title">{publisher.name}</h3>
        {publisher.intro && <p className="caption">{publisher.intro.length > 100 ? publisher.intro.substring(0, 100) + '...' : publisher.intro}</p>}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          <button className="btn-pill-link" onClick={() => navigate(`${publisher.id}`)}>{t('common.view')}</button>
          {isAuthenticated && (
            <>
              <button className="btn-pill-link" onClick={() => openEdit(publisher)}>{t('common.edit')}</button>
              <button className="btn-pill-link"
                onClick={() => setConfirmDelete(publisher)}
                style={{ color: '#ff3b30' }}>{t('common.delete')}</button>
            </>
          )}
        </div>
      </div>
    );
  };

  if (loading) return <div className="loading">Loading...</div>;

  return (
    <>
      <PageLayout
        title={t('publishers.title')}
        createButton={
          isAuthenticated ? (
            <button className="btn-pill-link" onClick={openCreate} style={{ marginBottom: 20 }}>
              + Create Publisher
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
        totalItems={totalPublishers}
        onPageChange={setPageParam}
        layoutKey="publishers"
        items={publishers}
        renderItem={renderItem}
        listColumns={listColumns}
      />

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
              {editingPublisher ? t('publishers.edit') : t('publishers.create')}
            </h2>
            <form onSubmit={handleSave}>
              <div style={{ marginBottom: 14 }}>
                <label style={labelStyle}>{t('common.name')} <span style={{ color: '#ff3b30' }}>*</span></label>
                <input type="text" value={formData.name} required
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  style={inputStyle} placeholder="e.g. 中华书局" />
              </div>
              <div style={{ marginBottom: 14 }}>
                <label style={labelStyle}>{t('common.introduction')}</label>
                <textarea value={formData.intro} rows={4}
                  onChange={(e) => setFormData({ ...formData, intro: e.target.value })}
                  style={{ ...inputStyle, resize: 'vertical' }} placeholder="Publisher description..." />
              </div>
              <div style={{ marginBottom: 20 }}>
                <label style={labelStyle}>Logo URL</label>
                <input type="text" value={formData.logo}
                  onChange={(e) => setFormData({ ...formData, logo: e.target.value })}
                  style={inputStyle} placeholder="https://..." />
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

      {/* ── Delete Confirmation ─────────────────────────────── */}
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

export default Publishers;
