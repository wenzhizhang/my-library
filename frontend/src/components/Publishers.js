import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import { API_BASE_URL, MEDIA_BASE_URL } from './Config';
import { useAuth } from '../AuthContext';

const labelStyle = {
  display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 600, color: '#1d1d1f',
};

const inputStyle = {
  width: '100%', borderRadius: 10, border: '1px solid #d2d2d7', padding: '8px 12px',
  fontSize: 14, outline: 'none', boxSizing: 'border-box',
};

const Publishers = () => {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const [publishers, setPublishers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [limit, setLimit] = useState(10);
  const [sortBy, setSortBy] = useState('name');
  const [totalPages, setTotalPages] = useState(1);
  const [totalPublishers, setTotalPublishers] = useState(0);
  const [goToPage, setGoToPage] = useState('');

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editingPublisher, setEditingPublisher] = useState(null);
  const [formData, setFormData] = useState({
    name: '',
    intro: '',
    logo: '',
  });
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(null);

  const [searchQuery, setSearchQuery] = useState('');
  const [submittedQuery, setSubmittedQuery] = useState('');

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

  const handleSearch = () => {
    setSubmittedQuery(searchQuery.trim());
    setPage(1);
  };

  const handleKeyDown = (e) => { if (e.key === 'Enter') handleSearch(); };

  useEffect(() => {
    fetchPublishers();
  }, [fetchPublishers]);

  // ── Modal helpers ──────────────────────────────────────────

  const openCreate = () => {
    setEditingPublisher(null);
    setFormData({ name: '', intro: '', logo: '' });
    setModalOpen(true);
  };

  const openEdit = (publisher) => {
    setEditingPublisher(publisher);
    setFormData({
      name: publisher.name || '',
      intro: publisher.intro || '',
      logo: publisher.logo || '',
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
      if (editingPublisher) {
        await axios.put(
          `${window.location.origin}${API_BASE_URL}/publishers/${editingPublisher.id}`,
          payload
        );
      } else {
        await axios.post(
          `${window.location.origin}${API_BASE_URL}/publishers/`,
          payload
        );
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

  // ── Pagination ─────────────────────────────────────────────

  const handleSortChange = (newSortBy) => { setSortBy(newSortBy); setPage(1); };
  const handleLimitChange = (newLimit) => { setLimit(parseInt(newLimit)); setPage(1); };
  const handleGoToPage = () => {
    const pageNum = Math.min(Math.max(parseInt(goToPage) || 1, 1), totalPages);
    setPage(pageNum);
    setGoToPage('');
  };
  const handleKeyPress = (e) => { if (e.key === 'Enter') handleGoToPage(); };

  const renderPagination = () => {
    const pages = [];
    const startPage = Math.max(1, page - 2);
    const endPage = Math.min(totalPages, page + 2);

    for (let i = startPage; i <= endPage; i++) {
      pages.push(
        <button
          key={i}
          className={`btn-pill-link ${i === page ? 'active' : ''}`}
          onClick={() => setPage(i)}
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
              <button className="btn-pill-link" onClick={() => setPage(1)}>First</button>
              <button className="btn-pill-link" onClick={() => setPage(page - 1)}>Previous</button>
            </>
          )}

          {pages}

          {page < totalPages && (
            <>
              <button className="btn-pill-link" onClick={() => setPage(page + 1)}>Next</button>
              <button className="btn-pill-link" onClick={() => setPage(totalPages)}>Last</button>
            </>
          )}
        </div>

        <div className="pagination-input">
          <label htmlFor="page-input">Go to page:</label>
          <input
            type="number"
            id="page-input"
            min="1"
            max={totalPages}
            value={goToPage}
            onChange={(e) => setGoToPage(e.target.value)}
            onKeyPress={handleKeyPress}
          />
          <button className="btn-pill-link" onClick={handleGoToPage}>Go</button>
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
          <h1 className="section-heading">Publishers</h1>
          {isAuthenticated && (
            <button className="btn-pill-link" onClick={openCreate} style={{ marginBottom: 20 }}>
              + Create Publisher
            </button>
          )}
        </div>

        <div className="toolbar">
          <div className="toolbar-search">
            <div className="toolbar-search-row">
              <input className="toolbar-search-input" placeholder="Search publishers…"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyDown={handleKeyDown} />
              <button className="btn-pill-link" onClick={handleSearch}>Search</button>
            </div>
          </div>
          <div className="toolbar-actions">
            <label className="control-label">
              <span className="control-label-text">Sort</span>
              <select value={sortBy} onChange={(e) => handleSortChange(e.target.value)}>
                <option value="id">ID</option>
                <option value="name">Name</option>
              </select>
            </label>
            <label className="control-label">
              <span className="control-label-text">Per page</span>
              <select value={limit} onChange={(e) => handleLimitChange(e.target.value)}>
                <option value="5">5</option>
                <option value="10">10</option>
                <option value="20">20</option>
                <option value="50">50</option>
              </select>
            </label>
          </div>
          <div className="toolbar-info">
            Page {page} / {totalPages} ({totalPublishers})
          </div>
        </div>

        <div className="grid">
          {publishers.map(publisher => (
            <div key={publisher.id} className="card">
              <h3 className="card-title">{publisher.name}</h3>
              {publisher.logo && (
                <img src={publisher.logo.startsWith('http') ? publisher.logo : `${MEDIA_BASE_URL}/${publisher.logo}`}
                  alt={publisher.name}
                  className="card-image" style={{ maxHeight: 120, objectFit: 'contain' }} />
              )}
              {publisher.intro && <p className="caption">{publisher.intro.length > 100 ? publisher.intro.substring(0, 100) + '...' : publisher.intro}</p>}
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                <button className="btn-pill-link" onClick={() => navigate(`${publisher.id}`)}>View</button>
                {isAuthenticated && (
                  <>
                    <button className="btn-pill-link" onClick={() => openEdit(publisher)}>Edit</button>
                    <button className="btn-pill-link"
                      onClick={() => setConfirmDelete(publisher)}
                      style={{ color: '#ff3b30' }}>Delete</button>
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
          background: 'rgba(29,29,31,0.6)',
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
        }} onClick={() => setModalOpen(false)}>
          <div style={{
            background: 'rgba(255, 255, 255, 0.6)',
            backdropFilter: 'blur(30px)',
            WebkitBackdropFilter: 'blur(30px)',
            borderRadius: 20,
            width: Math.min(560, window.innerWidth - 32),
            padding: '24px 28px',
            maxHeight: '90vh', overflow: 'auto',
            boxShadow: '0 25px 50px rgba(0,0,0,0.25)',
          }} onClick={(e) => e.stopPropagation()}>
            <h2 style={{ margin: '0 0 20px', fontSize: 22, fontWeight: 600 }}>
              {editingPublisher ? 'Edit Publisher' : 'Create Publisher'}
            </h2>
            <form onSubmit={handleSave}>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 600, color: '#1d1d1f' }}>
                  Name <span style={{ color: '#ff3b30' }}>*</span>
                </label>
                <input type="text" value={formData.name} required
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  style={inputStyle} placeholder="e.g. O'Reilly Media" />
              </div>
              <div style={{ marginBottom: 14 }}>
                <label style={labelStyle}>Introduction</label>
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
                  onClick={() => setModalOpen(false)}>Cancel</button>
                <button type="submit" className="btn-pill-link" disabled={saving}
                  style={saving ? { opacity: 0.6 } : {}}>
                  {saving ? 'Saving...' : 'Save'}
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
          background: 'rgba(29,29,31,0.6)',
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
        }} onClick={() => setConfirmDelete(null)}>
          <div style={{
            background: 'rgba(255, 255, 255, 0.6)',
            backdropFilter: 'blur(30px)',
            WebkitBackdropFilter: 'blur(30px)',
            borderRadius: 20,
            width: Math.min(560, window.innerWidth - 32),
            padding: '24px 28px',
            boxShadow: '0 25px 50px rgba(0,0,0,0.25)',
            textAlign: 'center',
          }} onClick={(e) => e.stopPropagation()}>
            <h3 style={{ margin: '0 0 8px', fontSize: 18 }}>
              Delete "{confirmDelete.name}"?
            </h3>
            <p style={{ color: '#86868b', margin: '0 0 20px', fontSize: 15 }}>
              This action cannot be undone.
            </p>
            <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
              <button className="btn-pill-link" onClick={() => setConfirmDelete(null)}>Cancel</button>
              <button className="btn-pill-link" onClick={() => handleDelete(confirmDelete.id)}
                style={{ color: '#ff3b30' }}>Delete</button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
};

export default Publishers;
