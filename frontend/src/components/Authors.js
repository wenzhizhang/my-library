import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import { API_BASE_URL, MEDIA_BASE_URL } from './Config';
import { useAuth } from '../AuthContext';

const NATIONS = [
  "中国", "俄罗斯", "前苏联", "希腊", "美国", "英国", "法国", "德国",
  "古巴", "西班牙", "古罗马", "加拿大", "爱尔兰", "澳大利亚", "瑞士",
  "阿根廷", "哥伦比亚", "奥地利", "挪威", "瑞典", "意大利", "比利时",
  "墨西哥", "荷兰", "巴西", "波兰", "伊朗", "波斯", "智利", "南非",
  "马来西亚", "捷克", "毛里求斯", "丹麦", "葡萄牙", "黎巴嫩", "冰岛",
  "以色列", "日本",
];

const DYNASTIES = [
  "上古", "夏", "商", "西周", "东周", "春秋", "战国",
  "秦", "西汉", "东汉", "魏", "蜀", "吴", "西晋", "东晋",
  "南北朝", "隋", "唐", "五代", "北宋", "南宋",
  "元", "明", "清", "民国", "现代", "当代",
];

const Authors = () => {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const [authors, setAuthors] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [limit, setLimit] = useState(10);
  const [sortBy, setSortBy] = useState('name');
  const [totalPages, setTotalPages] = useState(1);
  const [totalAuthors, setTotalAuthors] = useState(0);
  const [goToPage, setGoToPage] = useState('');

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editingAuthor, setEditingAuthor] = useState(null);
  const [formData, setFormData] = useState({
    name: '',
    name_cn: '',
    nation: '无',
    dynasty: '当代',
    intro: '',
    photo: '',
  });
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(null);

  const fetchAuthors = useCallback(async () => {
    setLoading(true);
    try {
      const res = await axios.get(
        `${window.location.origin}${API_BASE_URL}/authors/?page=${page}&limit=${limit}&sort_by=${sortBy}`
      );
      setAuthors(res.data.authors || []);
      setTotalPages(res.data.total_pages || 1);
      setTotalAuthors(res.data.total_authors || 0);
    } catch (err) {
      console.error('Error fetching authors:', err);
    }
    setLoading(false);
  }, [page, limit, sortBy]);

  useEffect(() => {
    fetchAuthors();
  }, [fetchAuthors]);

  // ── Modal helpers ──────────────────────────────────────────

  const openCreate = () => {
    setEditingAuthor(null);
    setFormData({ name: '', name_cn: '', nation: '无', dynasty: '当代', intro: '', photo: '' });
    setModalOpen(true);
  };

  const openEdit = (author) => {
    setEditingAuthor(author);
    setFormData({
      name: author.name || '',
      name_cn: author.name_cn || '',
      nation: author.nation || '无',
      dynasty: author.dynasty || '当代',
      intro: author.intro || '',
      photo: author.photo || '',
    });
    setModalOpen(true);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!formData.name.trim()) return;
    setSaving(true);
    try {
      if (editingAuthor) {
        await axios.put(
          `${window.location.origin}${API_BASE_URL}/authors/${editingAuthor.id}`,
          formData
        );
      } else {
        await axios.post(
          `${window.location.origin}${API_BASE_URL}/authors/`,
          formData
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
        <button key={i} className={`btn-pill-link ${i === page ? 'active' : ''}`} onClick={() => setPage(i)}>
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
          <input type="number" id="page-input" min="1" max={totalPages} value={goToPage}
            onChange={(e) => setGoToPage(e.target.value)} onKeyPress={handleKeyPress} />
          <button className="btn-pill-link" onClick={handleGoToPage}>Go</button>
        </div>
      </div>
    );
  };

  // ── Render ─────────────────────────────────────────────────

  if (loading) {
    return <div className="loading">Loading...</div>;
  }

  return (
    <section className="section light">
      <div className="container">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h1 className="section-heading">Authors</h1>
          {isAuthenticated && (
            <button className="btn-pill-link" onClick={openCreate} style={{ marginBottom: 20 }}>
              + Create Author
            </button>
          )}
        </div>

        <div className="controls">
          <div className="control-group">
            <label htmlFor="sort">Sort by:</label>
            <select id="sort" value={sortBy} onChange={(e) => handleSortChange(e.target.value)}>
              <option value="id">Id</option>
              <option value="name">Name</option>
              <option value="created_at">Date Added</option>
            </select>
            <label htmlFor="limit">Items per page:</label>
            <select id="limit" value={limit} onChange={(e) => handleLimitChange(e.target.value)}>
              <option value="5">5</option>
              <option value="10">10</option>
              <option value="20">20</option>
              <option value="50">50</option>
            </select>
          </div>
          <div className="page-info">
            Page {page} of {totalPages} ({totalAuthors} total authors)
          </div>
        </div>

        <div className="grid">
          {authors.map(author => (
            <div key={author.id} className="card">
              {author.photo && (
                <img src={`${MEDIA_BASE_URL}/${author.photo}`} alt={author.name_cn || author.name}
                  className="card-image authors-image hvr-float-shadow" />
              )}
              <h3 className="card-title">{author.name_cn || author.name}</h3>
              <p className="caption">
                {(author.dynasty && author.dynasty !== '当代' && author.nation === '中国')
                  ? `[${author.dynasty}] `
                  : `[${author.nation || '无'}] `}
                {author.name}
              </p>
              {author.intro && (
                <p className="caption">
                  {author.intro.length > 80 ? author.intro.substring(0, 80) + '...' : author.intro}
                </p>
              )}
              <button className="btn-pill-link" onClick={() => navigate(`${author.id}`)}>
                View Details
              </button>
              {isAuthenticated && (
                <>
                  <button className="btn-pill-link" onClick={() => openEdit(author)}>
                    Edit
                  </button>
                  <button className="btn-pill-link"
                    onClick={() => setConfirmDelete(author)}
                    style={{ color: '#ff3b30' }}>
                    Delete
                  </button>
                </>
              )}
            </div>
          ))}
        </div>

        {renderPagination()}
      </div>

      {/* ── Create / Edit Modal ─────────────────────────────── */}
      {modalOpen && (
        <div style={{
          position: 'fixed', inset: 0, zIndex: 10000,
          background: 'rgba(29,29,31,0.92)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
        }} onClick={() => setModalOpen(false)}>
          <div style={{
            background: '#fff', borderRadius: 20, padding: '24px 28px',
            width: Math.min(480, window.innerWidth - 32),
            maxHeight: '90vh', overflow: 'auto',
            boxShadow: '0 25px 50px rgba(0,0,0,0.25)',
          }} onClick={(e) => e.stopPropagation()}>
            <h2 style={{ margin: '0 0 20px', fontSize: 22, fontWeight: 600 }}>
              {editingAuthor ? 'Edit Author' : 'Create Author'}
            </h2>
            <form onSubmit={handleSave}>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 600, color: '#1d1d1f' }}>
                  Name <span style={{ color: '#ff3b30' }}>*</span>
                </label>
                <input type="text" value={formData.name} required
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  style={inputStyle} placeholder="e.g. 东野圭吾" />
              </div>
              <div style={{ marginBottom: 14 }}>
                <label style={labelStyle}>Name (Chinese)</label>
                <input type="text" value={formData.name_cn}
                  onChange={(e) => setFormData({ ...formData, name_cn: e.target.value })}
                  style={inputStyle} placeholder="e.g. 东野圭吾" />
              </div>
              <div style={{ display: 'flex', gap: 12, marginBottom: 14 }}>
                <div style={{ flex: 1 }}>
                  <label style={labelStyle}>Nation</label>
                  <select value={formData.nation}
                    onChange={(e) => setFormData({ ...formData, nation: e.target.value })}
                    style={inputStyle}>
                    {NATIONS.map(n => <option key={n} value={n}>{n}</option>)}
                  </select>
                </div>
                <div style={{ flex: 1 }}>
                  <label style={labelStyle}>Dynasty</label>
                  <select value={formData.dynasty}
                    onChange={(e) => setFormData({ ...formData, dynasty: e.target.value })}
                    style={inputStyle}>
                    <option value="">None</option>
                    {DYNASTIES.map(d => <option key={d} value={d}>{d}</option>)}
                  </select>
                </div>
              </div>
              <div style={{ marginBottom: 14 }}>
                <label style={labelStyle}>Introduction</label>
                <textarea value={formData.intro} rows={4}
                  onChange={(e) => setFormData({ ...formData, intro: e.target.value })}
                  style={{ ...inputStyle, resize: 'vertical' }} placeholder="Author biography..." />
              </div>
              <div style={{ marginBottom: 20 }}>
                <label style={labelStyle}>Photo URL</label>
                <input type="text" value={formData.photo}
                  onChange={(e) => setFormData({ ...formData, photo: e.target.value })}
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
          background: 'rgba(29,29,31,0.92)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
        }} onClick={() => setConfirmDelete(null)}>
          <div style={{
            background: '#fff', borderRadius: 20, padding: '24px 28px',
            width: Math.min(380, window.innerWidth - 32),
            boxShadow: '0 25px 50px rgba(0,0,0,0.25)',
            textAlign: 'center',
          }} onClick={(e) => e.stopPropagation()}>
            <h3 style={{ margin: '0 0 8px', fontSize: 18 }}>
              Delete "{confirmDelete.name_cn || confirmDelete.name}"?
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
