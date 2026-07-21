import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import { API_BASE_URL } from './Config';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../AuthContext';

const Bookshelves = () => {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const page = parseInt(searchParams.get('page')) || 1;
  const limit = parseInt(searchParams.get('limit')) || 10;
  const sortBy = searchParams.get('sort_by') || 'name';

  const [bookshelves, setBookshelves] = useState([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(1);
  const [totalBookshelves, setTotalBookshelves] = useState(0);

  const [searchQuery, setSearchQuery] = useState(searchParams.get('q') || '');
  const [submittedQuery, setSubmittedQuery] = useState(searchParams.get('q') || '');

  const [goToPage, setGoToPage] = useState('');

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState(null);
  const [formData, setFormData] = useState({ name: '', intro: '' });
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(null);

  const fetchBookshelves = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, limit, sort_by: sortBy };
      if (submittedQuery.trim()) {
        params.q = submittedQuery.trim();
      }
      const response = await axios.get(`${window.location.origin}${API_BASE_URL}/bookshelves/`, { params });
      const data = response.data;
      setBookshelves(data.bookshelves || []);
      setTotalPages(data.total_pages || 1);
      setTotalBookshelves(data.total_bookshelves || 0);
    } catch (error) {
      console.error('Error fetching bookshelves:', error);
    }
    setLoading(false);
  }, [page, limit, sortBy, submittedQuery]);

  useEffect(() => {
    fetchBookshelves();
  }, [fetchBookshelves]);

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

  // ── Modal helpers ──────────────────────────────────────────

  const openCreate = () => {
    setEditingItem(null);
    setFormData({ name: '', intro: '' });
    setModalOpen(true);
  };

  const openEdit = (bookshelf) => {
    setEditingItem(bookshelf);
    setFormData({
      name: bookshelf.name || '',
      intro: bookshelf.description || '',
    });
    setModalOpen(true);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!formData.name.trim()) return;
    setSaving(true);

    // Strip empty strings so Pydantic validators don't reject them
    const payload = {};
    for (const [k, v] of Object.entries(formData)) {
      if (v !== '' && v !== null && v !== undefined) {
        payload[k] = v;
      }
    }

    try {
      if (editingItem) {
        await axios.put(
          `${window.location.origin}${API_BASE_URL}/bookshelves/${editingItem.id}`,
          payload
        );
      } else {
        await axios.post(
          `${window.location.origin}${API_BASE_URL}/bookshelves/`,
          payload
        );
      }
      setModalOpen(false);
      fetchBookshelves();
    } catch (err) {
      const msg = err.response?.data?.detail || 'Save failed';
      alert(msg);
    }
    setSaving(false);
  };

  const handleDelete = async (bookshelfId) => {
    try {
      await axios.delete(`${window.location.origin}${API_BASE_URL}/bookshelves/${bookshelfId}`);
      setConfirmDelete(null);
      fetchBookshelves();
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

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      handleGoToPage();
    }
  };

  const handleSearch = () => {
    const q = searchQuery.trim();
    setSubmittedQuery(q);
    const next = new URLSearchParams(searchParams);
    if (q) next.set('q', q); else next.delete('q');
    next.set('page', '1');
    setSearchParams(next, { replace: true });
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const renderPagination = () => {
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

    return (
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
            onKeyPress={handleKeyPress}
          />
          <button className="btn-pill-link" onClick={handleGoToPage}>{t('common.goToPage')}</button>
        </div>
      </div>
    );
  };

  if (loading) {
    return <div className="loading">Loading...</div>;
  }

  return (
    <section className="section light">
      <div className="container">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h1 className="section-heading">{t('bookshelves.title')}</h1>
          {isAuthenticated && (
            <button className="btn-pill-link" onClick={openCreate} style={{ marginBottom: 20 }}>
              + Create Bookshelf
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
                <option value="id">ID</option>
                <option value="name">Name</option>
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
            {t('common.page')} {page} {t('common.of')} {totalPages} ({t('common.total')} {totalBookshelves})
          </div>
        </div>
        <div className="grid">
          {bookshelves.map(bookshelf => (
            <div key={bookshelf.id} className="card">
              <h3 className="card-title">{bookshelf.name}</h3>
              {bookshelf.description && <p className="caption">{bookshelf.description.length > 100 ? bookshelf.description.substring(0, 100) + '...' : bookshelf.description}</p>}
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                <button className="btn-pill-link" onClick={() => navigate(`${bookshelf.id}`)}>
                  {t('common.view')}
                </button>
                {isAuthenticated && (
                  <>
                    <button className="btn-pill-link" onClick={() => openEdit(bookshelf)}>
                      {t('common.edit')}
                    </button>
                    <button className="btn-pill-link"
                      onClick={() => setConfirmDelete(bookshelf)}
                      style={{ color: '#ff3b30' }}>
                      {t('common.delete')}
                    </button>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>

        {renderPagination()}
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
              {editingItem ? t('bookshelves.edit') : t('bookshelves.create')}
            </h2>
            <form onSubmit={handleSave}>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 600, color: '#1d1d1f' }}>
                  {t('common.name')} <span style={{ color: '#ff3b30' }}>*</span>
                </label>
                <input type="text" value={formData.name} required
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  style={inputStyle} placeholder="Bookshelf name" />
              </div>
              <div style={{ marginBottom: 14 }}>
                <label style={labelStyle}>{t('common.introduction')}</label>
                <textarea value={formData.intro} rows={4}
                  onChange={(e) => setFormData({ ...formData, intro: e.target.value })}
                  style={{ ...inputStyle, resize: 'vertical' }} placeholder="Bookshelf description..." />
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
              Delete &quot;{confirmDelete.name}&quot;?
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
  width: '100%', padding: '8px 12px', border: '1px solid #d2d2d7', borderRadius: 8,
  fontSize: 14, outline: 'none', boxSizing: 'border-box',
};

export default Bookshelves;
