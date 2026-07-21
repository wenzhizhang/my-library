import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import { API_BASE_URL, MEDIA_BASE_URL } from './Config';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../AuthContext';


const Authors = () => {
  const { t } = useTranslation();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  // Derive state from URL search params
  const page = parseInt(searchParams.get('page')) || 1;
  const limit = parseInt(searchParams.get('limit')) || 10;
  const sortBy = searchParams.get('sort_by') || 'name';
  const [authors, setAuthors] = useState([]);
  const [loading, setLoading] = useState(true);

  const [totalPages, setTotalPages] = useState(1);
  const [totalAuthors, setTotalAuthors] = useState(0);
  const [goToPage, setGoToPage] = useState('');

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editingAuthor, setEditingAuthor] = useState(null);
  const [formData, setFormData] = useState({ name: '', name_cn: '', nation: '无', dynasty: '', intro: '', photo: '' });
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(null);

  // Search input state (synced from URL)
  const [searchQuery, setSearchQuery] = useState(searchParams.get('q') || '');
  const [submittedQuery, setSubmittedQuery] = useState(searchParams.get('q') || '');
  const [nations, setNations] = useState(['无']);
  const [dynasties, setDynasties] = useState([]);
  const fetchAuthors = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, limit, sort_by: sortBy };
      if (submittedQuery) params.q = submittedQuery;
      const res = await axios.get(
        `${window.location.origin}${API_BASE_URL}/authors/`, { params }
      );
      setAuthors(res.data.authors || []);
      setTotalPages(res.data.total_pages || 1);
      setTotalAuthors(res.data.total_authors || 0);
    } catch (err) {
      console.error('Error fetching authors:', err);
    }
    setLoading(false);
  }, [page, limit, sortBy, submittedQuery]);

  const handleSearch = () => {
    const q = searchQuery.trim();
    setSubmittedQuery(q);
    const next = new URLSearchParams(searchParams);
    if (q) next.set('q', q); else next.delete('q');
    next.set('page', '1');
    setSearchParams(next, { replace: true });
  };

  const handleKeyDown = (e) => { if (e.key === 'Enter') handleSearch(); };

  useEffect(() => {
    fetchAuthors();
  }, [fetchAuthors]);

  // ── Modal helpers ──────────────────────────────────────────
  useEffect(() => {
    const fetchLists = async () => {
      try {
        const [nationsRes, dynastiesRes] = await Promise.all([
          axios.get(`${window.location.origin}${API_BASE_URL}/authors/nations`),
          axios.get(`${window.location.origin}${API_BASE_URL}/authors/dynasties`),
        ]);
        const n = nationsRes.data.nations || ['无'];
        if (!n.includes('无')) n.unshift('无');
        setNations(n);
        setDynasties(dynastiesRes.data.dynasties || []);
      } catch (err) {
        console.error('Error fetching nation/dynasty lists:', err);
      }
    };
    fetchLists();
  }, []);

  const openCreate = () => {
    setEditingAuthor(null);
    setFormData({ name: '', name_cn: '', nation: '无', dynasty: '', intro: '', photo: '' });
    setModalOpen(true);
  };

  const openEdit = (author) => {
    setEditingAuthor(author);
    setFormData({
      name: author.name || '',
      name_cn: author.name_cn || '',
      nation: author.nation || '无',
      dynasty: author.dynasty || '',
      intro: author.intro || '',
      photo: author.photo || '',
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
      if (editingAuthor) {
        await axios.put(
          `${window.location.origin}${API_BASE_URL}/authors/${editingAuthor.id}`,
          payload
        );
      } else {
        await axios.post(
          `${window.location.origin}${API_BASE_URL}/authors/`,
          payload
        );
      }
      setModalOpen(false);
      fetchAuthors();
    } catch (err) {
      const msg = err.response?.data?.detail || 'Save failed';
      alert(msg);
    }
    setSaving(false);
  };

  const handleDelete = async (authorId) => {
    try {
      await axios.delete(`${window.location.origin}${API_BASE_URL}/authors/${authorId}`);
      setConfirmDelete(null);
      fetchAuthors();
    } catch (err) {
      const msg = err.response?.data?.detail || 'Delete failed';
      alert(msg);
    }
  };

  // ── Pagination ─────────────────────────────────────────────

  const setPageParam = (p) => {
    const next = new URLSearchParams(searchParams);
    next.set('page', String(p));
    setSearchParams(next, { replace: true });
  };

  const handleSortChange = (newSortBy) => {
    const next = new URLSearchParams(searchParams);
    next.set('sort_by', newSortBy);
    next.set('page', '1');
    setSearchParams(next, { replace: true });
  };

  const handleLimitChange = (newLimit) => {
    const next = new URLSearchParams(searchParams);
    next.set('limit', newLimit);
    next.set('page', '1');
    setSearchParams(next, { replace: true });
  };

  const handleGoToPage = () => {
    const pageNum = Math.min(Math.max(parseInt(goToPage) || 1, 1), totalPages);
    setPageParam(pageNum);
    setGoToPage('');
  };
  const handleKeyPress = (e) => { if (e.key === 'Enter') handleGoToPage(); };

  const renderPagination = () => {
    const pages = [];
    const startPage = Math.max(1, page - 2);
    const endPage = Math.min(totalPages, page + 2);
    for (let i = startPage; i <= endPage; i++) {
      pages.push(
        <button key={i} className={`btn-pill-link ${i === page ? 'active' : ''}`} onClick={() => setPageParam(i)}>
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
          <input type="number" id="page-input" min="1" max={totalPages} value={goToPage}
            onChange={(e) => setGoToPage(e.target.value)} onKeyPress={handleKeyPress} />
          <button className="btn-pill-link" onClick={handleGoToPage}>{t('common.goToPage')}</button>
        </div>
      </div>
    );
  };

  // ── Render ─────────────────────────────────────────────────

  if (loading) {
    return <div className="loading">{t('common.loading')}</div>;
  }

  return (
    <section className="section light">
      <div className="container">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h1 className="section-heading">{t('authors.title')}</h1>
          {isAuthenticated && (
            <button className="btn-pill-link" onClick={openCreate} style={{ marginBottom: 20 }}>
              {t('authors.create')}
            </button>
          )}
        </div>

        {/* Toolbar */}
        <div className="toolbar">
          <div className="toolbar-search">
            <div className="toolbar-search-row">
              <input className="toolbar-search-input" placeholder={t('authors.searchPlaceholder')}
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
            {t('common.page')} {page} {t('common.of')} {totalPages} ({t('common.total')} {totalAuthors})
          </div>
        </div>

        <div className="grid">
          {authors.map(author => (
            <div key={author.id} className="card">
              {author.photo && (
                <img src={`${MEDIA_BASE_URL}/${author.photo}`} alt={author.name_cn || author.name}
                  className="card-image authors-image hvr-float-shadow" />
              )}
              <p className="caption">
                {author.nation === '中国' && author.dynasty
                  ? `[${author.dynasty}] `
                  : `[${author.nation || '无'}] `}
                {author.name}
              </p>
              {author.intro && (
                <p className="caption">
                  {author.intro.length > 80 ? author.intro.substring(0, 80) + '...' : author.intro}
                </p>
              )}
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                <button className="btn-pill-link" onClick={() => navigate(`${author.id}`)}>
                  {t('common.view')}
                </button>
                {isAuthenticated && (
                  <>
                    <button className="btn-pill-link" onClick={() => openEdit(author)}>
                      {t('common.edit')}
                    </button>
                    <button className="btn-pill-link"
                      onClick={() => setConfirmDelete(author)}
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
              {editingAuthor ? t('common.editEntity') + ' ' + t('authors.title') : t('common.createEntity') + ' ' + t('authors.title')}
            </h2>
            <form onSubmit={handleSave}>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 600, color: '#1d1d1f' }}>
                  {t('authors.name')} <span style={{ color: '#ff3b30' }}>*</span>
                </label>
                <input type="text" value={formData.name} required
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  style={inputStyle} placeholder="e.g. 曹雪芹" />
              </div>
              <div style={{ marginBottom: 14 }}>
                <label style={labelStyle}>{t('authors.nameCn')}</label>
                <input type="text" value={formData.name_cn}
                  onChange={(e) => setFormData({ ...formData, name_cn: e.target.value })}
                  style={inputStyle} placeholder="e.g. 曹雪芹" />
              </div>
              <div style={{ display: 'flex', gap: 12, marginBottom: 14 }}>
                <div style={{ flex: 1 }}>
                  <label style={labelStyle}>{t('authors.nation')}</label>
                  <select value={formData.nation}
                    onChange={(e) => setFormData({ ...formData, nation: e.target.value })}
                    style={inputStyle}>
                    {nations.map(n => <option key={n} value={n}>{n}</option>)}
                  </select>
                </div>
                <div style={{ flex: 1 }}>
                  <label style={labelStyle}>{t('authors.dynasty')}</label>
                  <select value={formData.dynasty}
                    onChange={(e) => setFormData({ ...formData, dynasty: e.target.value })}
                    style={inputStyle}>
                    <option value="">{t('common.none')}</option>
                    {dynasties.map(d => <option key={d} value={d}>{d}</option>)}
                  </select>
                </div>
              </div>
              <div style={{ marginBottom: 14 }}>
                <label style={labelStyle}>{t('authors.introduction')}</label>
                <textarea value={formData.intro} rows={4}
                  onChange={(e) => setFormData({ ...formData, intro: e.target.value })}
                  style={{ ...inputStyle, resize: 'vertical' }} placeholder="Author biography..." />
              </div>
              <div style={{ marginBottom: 20 }}>
                <label style={labelStyle}>{t('authors.photoUrl')}</label>
                <input type="text" value={formData.photo}
                  onChange={(e) => setFormData({ ...formData, photo: e.target.value })}
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
              {t('common.deleteConfirm')} &quot;{confirmDelete.name_cn || confirmDelete.name}&quot;?
            </h3>
            <p style={{ color: '#86868b', margin: '0 0 20px', fontSize: 15 }}>
              {t('common.cannotUndo')}
            </p>
            <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
              <button className="btn-pill-link" onClick={() => setConfirmDelete(null)}>{t('common.cancel')}</button>
              <button className="btn-pill-link" onClick={() => handleDelete(confirmDelete.id)}
                style={{ color: '#ff3b30' }}>{t('common.delete')}</button>
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

export default Authors;
