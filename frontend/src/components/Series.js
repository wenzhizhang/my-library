import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from "react-router-dom";
import axios from "axios";
import "./Books.css";
import { API_BASE_URL } from './Config';
import { useAuth } from '../AuthContext';

const Series = () => {
  const [series, setSeries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [limit, setLimit] = useState(10);
  const [sortBy, setSortBy] = useState("name");
  const [totalPages, setTotalPages] = useState(1);
  const [totalSeries, setTotalSeries] = useState(0);
  const [goToPage, setGoToPage] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [submittedQuery, setSubmittedQuery] = useState("");
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState(null);
  const [formData, setFormData] = useState({ name: '', intro: '' });
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(null);
  const fetchSeries = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, limit, sort_by: sortBy };
      if (submittedQuery.trim()) {
        params.q = submittedQuery.trim();
      }
      const response = await axios.get(
        `${window.location.origin}${API_BASE_URL}/series/`,
        { params }
      );
      const data = response.data;
      setSeries(data.series || []);
      setTotalPages(data.total_pages || 1);
      setTotalSeries(data.total_series || 0);
    } catch (error) {
      console.error("Error fetching series:", error);
    }
    setLoading(false);
  }, [page, limit, sortBy, submittedQuery]);

  useEffect(() => {
    fetchSeries();
  }, [fetchSeries]);


  const handleSortChange = (newSortBy) => {
    setSortBy(newSortBy);
    setPage(1);
  };

  const handleLimitChange = (newLimit) => {
    setLimit(parseInt(newLimit));
    setPage(1);
  };

  const handleGoToPage = () => {
    const pageNum = Math.min(
      Math.max(parseInt(goToPage) || 1, 1),
      totalPages
    );
    setPage(pageNum);
    setGoToPage("");
  };

  const handleKeyPress = (e) => {
    if (e.key === "Enter") {
      handleGoToPage();
    }
  };

  const handleSearch = () => {
    setSubmittedQuery(searchQuery.trim());
    setPage(1);
    setGoToPage("");
  };

  const handleKeyDown = (e) => {
    if (e.key === "Enter") {
      handleSearch();
    }
  };

  // ── Modal helpers ──────────────────────────────────────────

  const openCreate = () => {
    setEditingItem(null);
    setFormData({ name: '', intro: '' });
    setModalOpen(true);
  };

  const openEdit = (item) => {
    setEditingItem(item);
    setFormData({ name: item.name || '', intro: item.intro || '' });
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
          `${window.location.origin}${API_BASE_URL}/series/${editingItem.id}`,
          payload
        );
      } else {
        await axios.post(
          `${window.location.origin}${API_BASE_URL}/series/`,
          payload
        );
      }
      setModalOpen(false);
      fetchSeries();
    } catch (err) {
      const msg = err.response?.data?.detail || 'Save failed';
      alert(msg);
    }
    setSaving(false);
  };

  const handleDelete = async (itemId) => {
    try {
      await axios.delete(`${window.location.origin}${API_BASE_URL}/series/${itemId}`);
      setConfirmDelete(null);
      fetchSeries();
    } catch (err) {
      const msg = err.response?.data?.detail || 'Delete failed';
      alert(msg);
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
          className={`btn-pill-link ${i === page ? "active" : ""}`}
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
              <button
                className="btn-pill-link"
                onClick={() => setPage(1)}
              >
                First
              </button>
              <button
                className="btn-pill-link"
                onClick={() => setPage(page - 1)}
              >
                Previous
              </button>
            </>
          )}

          {pages}

          {page < totalPages && (
            <>
              <button
                className="btn-pill-link"
                onClick={() => setPage(page + 1)}
              >
                Next
              </button>
              <button
                className="btn-pill-link"
                onClick={() => setPage(totalPages)}
              >
                Last
              </button>
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
          <button className="btn-pill-link" onClick={handleGoToPage}>
            Go
          </button>
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
          <h1 className="section-heading">Series</h1>
          {isAuthenticated && (
            <button className="btn-pill-link" onClick={openCreate} style={{ marginBottom: 20 }}>
              + Create Series
            </button>
          )}
        </div>

        <div className="toolbar">
          <div className="toolbar-search">
            <div className="toolbar-search-row">
              <input className="toolbar-search-input" placeholder="Search series…"
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
            Page {page} / {totalPages} ({totalSeries})
          </div>
        </div>

        <div className="grid">
          {series.map((item) => (
            <div key={item.id} className="card">
              <h3 className="card-title">{item.name}</h3>

              {item.intro && (
                <p className="caption">
                  {item.intro.length > 100
                    ? item.intro.substring(0, 100) + "..."
                    : item.intro}
                </p>
              )}
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                <button
                  className="btn-pill-link"
                  onClick={() => navigate(`${item.id}`)}
                >
                  View
                </button>
                {isAuthenticated && (
                  <>
                    <button className="btn-pill-link" onClick={() => openEdit(item)}>
                      Edit
                    </button>
                    <button className="btn-pill-link"
                      onClick={() => setConfirmDelete(item)}
                      style={{ color: '#ff3b30' }}>
                      Delete
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
          background: 'rgba(29,29,31,0.6)',
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
        }} onClick={() => setModalOpen(false)}>
          <div style={{
            background: 'rgba(255, 255, 255, 0.6)',
            backdropFilter: 'blur(30px)',
            WebkitBackdropFilter: 'blur(30px)', borderRadius: 20, padding: '24px 28px',
            width: Math.min(560, window.innerWidth - 32),
            maxHeight: '90vh', overflow: 'auto',
            boxShadow: '0 25px 50px rgba(0,0,0,0.25)',
          }} onClick={(e) => e.stopPropagation()}>
            <h2 style={{ margin: '0 0 20px', fontSize: 22, fontWeight: 600 }}>
              {editingItem ? 'Edit Series' : 'Create Series'}
            </h2>
            <form onSubmit={handleSave}>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 600, color: '#1d1d1f' }}>
                  Name <span style={{ color: '#ff3b30' }}>*</span>
                </label>
                <input type="text" value={formData.name} required
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  style={{
                    width: '100%', padding: '10px 12px', borderRadius: 12, border: '1px solid #d2d2d7',
                    fontSize: 15, outline: 'none', boxSizing: 'border-box',
                  }} placeholder="Series name" />
              </div>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 600, color: '#1d1d1f' }}>
                  Introduction
                </label>
                <textarea value={formData.intro} rows={4}
                  onChange={(e) => setFormData({ ...formData, intro: e.target.value })}
                  style={{
                    width: '100%', padding: '10px 12px', borderRadius: 12, border: '1px solid #d2d2d7',
                    fontSize: 15, outline: 'none', boxSizing: 'border-box', resize: 'vertical',
                  }} placeholder="Series introduction..." />
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
            WebkitBackdropFilter: 'blur(30px)', borderRadius: 20, padding: '24px 28px',
            width: Math.min(560, window.innerWidth - 32),
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

export default Series;

