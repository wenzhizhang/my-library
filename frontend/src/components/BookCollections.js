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

const BookCollections = () => {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const page = parseInt(searchParams.get('collectionsPage')) || 1;
  const limit = parseInt(searchParams.get('limit')) || 20;
  const sortBy = searchParams.get('sort_by') || 'weight';

  const [collections, setCollections] = useState([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(1);
  const [totalCollections, setTotalCollections] = useState(0);

  const [searchQuery, setSearchQuery] = useState(searchParams.get('q') || '');
  const [submittedQuery, setSubmittedQuery] = useState(searchParams.get('q') || '');

  const [error, setError] = useState(null);
  const [confirmDelete, setConfirmDelete] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState(null);
  const [formData, setFormData] = useState({ name: '', intro: '' });
  const [saving, setSaving] = useState(false);

  const fetchCollections = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, limit, sort_by: sortBy };
      if (submittedQuery.trim()) params.q = submittedQuery.trim();
      const response = await axios.get(
        `${window.location.origin}${API_BASE_URL}/book-collections/`, { params }
      );
      const data = response.data;
      setError(null);
      setCollections(data.book_collections || []);
      setTotalPages(data.total_pages || 1);
      setTotalCollections(data.total_collections || 0);
    } catch (error) {
      console.error('Error fetching book collections:', error);
      setError('Failed to load collections. Please try again.');
    }
    setLoading(false);
  }, [page, limit, sortBy, submittedQuery]);

  useEffect(() => {
    fetchCollections();
  }, [fetchCollections]);

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
    next.set('collectionsPage', String(p));
    setSearchParams(next, { replace: true });
  };

  const setSortByParam = (s) => {
    const next = new URLSearchParams(searchParams);
    next.set('sort_by', s);
    next.set('collectionsPage', '1');
    setSearchParams(next, { replace: true });
  };

  const setLimitParam = (l) => {
    const next = new URLSearchParams(searchParams);
    next.set('limit', String(l));
    next.set('collectionsPage', '1');
    setSearchParams(next, { replace: true });
  };

  const handleSearch = () => {
    const q = searchQuery.trim();
    setSubmittedQuery(q);
    const next = new URLSearchParams(searchParams);
    if (q) next.set('q', q); else next.delete('q');
    next.set('collectionsPage', '1');
    setSearchParams(next, { replace: true });
  };

  const openCreate = () => {
    setEditingItem(null);
    setFormData({ name: '', intro: '' });
    setModalOpen(true);
  };

  const openEdit = (collection) => {
    setEditingItem(collection);
    setFormData({ name: collection.name || '', intro: collection.intro || '' });
    setModalOpen(true);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!formData.name.trim()) return;
    setSaving(true);
    const payload = {};
    for (const [k, v] of Object.entries(formData)) {
      if (v !== '' && v !== null && v !== undefined) {
        payload[k] = v;
      }
    }
    try {
      if (editingItem) {
        await axios.put(
          `${window.location.origin}${API_BASE_URL}/book-collections/${editingItem.id}`,
          payload
        );
      } else {
        await axios.post(
          `${window.location.origin}${API_BASE_URL}/book-collections/`,
          payload
        );
      }
      setModalOpen(false);
      fetchCollections();
    } catch (err) {
      const msg = err.response?.data?.detail || 'Save failed';
      alert(msg);
    }
    setSaving(false);
  };

  const handleDelete = async (collectionId) => {
    try {
      await axios.delete(`${window.location.origin}${API_BASE_URL}/book-collections/${collectionId}`);
      setConfirmDelete(null);
      fetchCollections();
    } catch (err) {
      const msg = err.response?.data?.detail || 'Delete failed';
      alert(msg);
    }
  };

  const sortOptions = [
    { value: 'weight', label: t('common.bookCount') },
    { value: 'id', label: 'ID' },
    { value: 'name', label: t('common.name') },
  ];

  const listColumns = [t('common.name'), 'Books', t('common.introduction'), 'Actions'];

  const renderItem = (item, viewMode) => {
    if (viewMode === 'list') {
      return (
        <tr key={item.id} onClick={() => navigate(`${item.id}`)}>
          <td className="list-cell-primary">{item.name}</td>
          <td className="list-cell-secondary" style={{ width: 60, textAlign: 'center' }}>{item.total_books ?? ''}</td>
          <td className="list-cell-secondary" style={{ maxWidth: 250 }}>
            {item.intro ? (item.intro.length > 80 ? item.intro.substring(0, 80) + '...' : item.intro) : ''}
          </td>
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
        {item.total_books !== undefined && (
          <p className="caption">{item.total_books} books</p>
        )}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          <button className="btn-pill-link" onClick={() => navigate(`${item.id}`)}>
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

  if (loading) {
    return <div className="loading">Loading...</div>;
  }

  return (
    <>
      {error && (
        <section className="section light">
          <div className="container">
            <div style={{background: '#fff0f0', border: '1px solid #ffc0c0', borderRadius: '8px', padding: '0.75rem 1rem', marginBottom: '1rem', display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
              <span style={{color: '#cc0000'}}>{error}</span>
              <div style={{display: 'flex', gap: '0.5rem'}}>
                <button onClick={() => { setError(null); fetchCollections(); }} className="btn-pill-link" style={{padding: '0.25rem 0.75rem'}}>Retry</button>
                <button onClick={() => setError(null)} className="btn-pill-link" style={{padding: '0.25rem 0.75rem'}}>Dismiss</button>
              </div>
            </div>
          </div>
        </section>
      )}


      <PageLayout
        title={t('collections.title')}
        createButton={
          isAuthenticated ? (
            <button className="btn-pill-link" onClick={openCreate} style={{ marginBottom: 20 }}>
              + Create Collection
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
        totalItems={totalCollections}
        onPageChange={setPageParam}
        layoutKey="collections"
        items={collections}
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
              {editingItem ? t('collections.edit') : t('collections.create')}
            </h2>
            <form onSubmit={handleSave}>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 600, color: '#1d1d1f' }}>
                  {t('common.name')} <span style={{ color: '#ff3b30' }}>*</span>
                </label>
                <input type="text" value={formData.name} required
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  style={inputStyle} placeholder="Collection name" />
              </div>
              <div style={{ marginBottom: 14 }}>
                <label style={labelStyle}>{t('common.introduction')}</label>
                <textarea value={formData.intro} rows={4}
                  onChange={(e) => setFormData({ ...formData, intro: e.target.value })}
                  style={{ ...inputStyle, resize: 'vertical' }} placeholder="Optional description..." />
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

export default BookCollections;
