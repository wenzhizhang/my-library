import React, { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import axios from "axios";
import "./Books.css";
import BookCard from './BookCard';
import { API_BASE_URL, MEDIA_BASE_URL } from './Config';
import { useAuth } from '../AuthContext';

const AuthorDetails = () => {
  const { id } = useParams();
  const { isAuthenticated } = useAuth();
  const [author, setAuthor] = useState(null);
  const [nations, setNations] = useState(['无']);
  const [dynasties, setDynasties] = useState([]);
  const [loading, setLoading] = useState(true);

  // Edit modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [formData, setFormData] = useState({ name: '', name_cn: '', nation: '无', dynasty: '', intro: '', photo: '' });
  const navigate = useNavigate();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    fetchAuthor();
  }, [id]);
  useEffect(() => {
    axios.get(`${window.location.origin}${API_BASE_URL}/authors/nations`)
      .then(res => setNations(res.data.nations))
      .catch(() => {});
    axios.get(`${window.location.origin}${API_BASE_URL}/authors/dynasties`)
      .then(res => setDynasties(res.data.dynasties))
      .catch(() => {});
  }, []);

  const fetchAuthor = async () => {
    setLoading(true);
    try {
      const response = await axios.get(
        `${window.location.origin}${API_BASE_URL}/authors/${id}`
      );
      setAuthor(response.data);
    } catch (error) {
      console.error("Error fetching author:", error);
    }
    setLoading(false);
  };

  const openEdit = () => {
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

    const payload = {};
    for (const [k, v] of Object.entries(formData)) {
      if (v !== '' && v !== null && v !== undefined) {
        payload[k] = v;
      }
    }

    try {
      await axios.put(
        `${window.location.origin}${API_BASE_URL}/authors/${id}`,
        payload
      );
      setModalOpen(false);
      fetchAuthor();
    } catch (err) {
      const msg = err.response?.data?.detail || 'Save failed';
      alert(msg);
    }
    setSaving(false);
  };

  if (loading) return <div className="loading">Loading...</div>;
  if (!author) return <div className="error">Author not found</div>;

  const displayName = author.name_cn || author.name;
  const nationLabel = author.dynasty && author.nation === '中国'
    ? `[${author.dynasty}]`
    : author.nation
      ? `[${author.nation}]`
      : '';

  return (
    <section className="section light">
      <div className="container">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
          <h1 className="section-heading" style={{ margin: 0 }}>{displayName}</h1>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn-primary-blue" onClick={() => navigate('/my-library/authors')}>
              Back to Authors
            </button>
            {isAuthenticated && (
              <button className="btn-primary-blue" onClick={openEdit}>
                Edit
              </button>
            )}
          </div>
        </div>

        <div className="details-content" style={{ marginTop: 24 }}>
          <h2>Author Information</h2>

          <div style={{
            display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
            gap: 12, marginTop: 12,
          }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              <span style={{ fontSize: 12, fontWeight: 600, color: '#86868b', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Name</span>
              <span>{author.name}</span>
            </div>
            {author.name_cn && author.name_cn !== author.name && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#86868b', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Chinese Name</span>
                <span>{author.name_cn}</span>
              </div>
            )}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              <span style={{ fontSize: 12, fontWeight: 600, color: '#86868b', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Nation / Dynasty</span>
              <span>{nationLabel ? `${nationLabel} ` : ''}{author.nation || '无'}</span>
            </div>
          </div>

          {author.intro && (
            <div style={{ marginTop: 20 }}>
              <h3 style={{ fontSize: 17, fontWeight: 600, marginBottom: 8 }}>Introduction</h3>
              <p style={{ lineHeight: 1.7, color: '#1d1d1f', whiteSpace: 'pre-wrap' }}>
                {author.intro}
              </p>
            </div>
          )}

          {author.photo && (
            <div style={{ marginTop: 20 }}>
              <h3 style={{ fontSize: 17, fontWeight: 600, marginBottom: 8 }}>Photo</h3>
              <img src={`${MEDIA_BASE_URL}/${author.photo}`} alt={displayName}
                style={{ maxWidth: 200, borderRadius: 8 }} />
            </div>
          )}
        </div>

        {author.books && author.books.length > 0 && (
          <>
            <h2 style={{ marginTop: 40, marginBottom: 16 }}>Books ({author.books.length})</h2>
            <div className="grid">
              {author.books.map((book) => (
                <BookCard key={book.id} book={book} />
              ))}
            </div>
          </>
        )}
      </div>

      {/* Edit Modal */}
      {modalOpen && (
        <div style={{
          position: 'fixed', inset: 0, zIndex: 10000,
          background: 'rgba(29,29,31,0.6)',
          backdropFilter: 'blur(12px)', WebkitBackdropFilter: 'blur(12px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
        }} onClick={() => setModalOpen(false)}>
          <div style={{
            background: 'rgba(255, 255, 255, 0.6)',
            backdropFilter: 'blur(30px)', WebkitBackdropFilter: 'blur(30px)',
            borderRadius: 20, padding: '24px 28px',
            width: Math.min(560, window.innerWidth - 32),
            maxHeight: '90vh', overflow: 'auto',
            boxShadow: '0 25px 50px rgba(0,0,0,0.25)',
          }} onClick={(e) => e.stopPropagation()}>
            <h2 style={{ margin: '0 0 20px', fontSize: 22, fontWeight: 600 }}>Edit Author</h2>
            <form onSubmit={handleSave}>
              <div style={{ marginBottom: 14 }}>
                <label style={labelStyle}>Name <span style={{ color: '#ff3b30' }}>*</span></label>
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
                    {nations.map(n => <option key={n} value={n}>{n}</option>)}
                  </select>
                </div>
                <div style={{ flex: 1 }}>
                  <label style={labelStyle}>Dynasty</label>
                  <select value={formData.dynasty}
                    onChange={(e) => setFormData({ ...formData, dynasty: e.target.value })}
                    style={inputStyle}>
                    <option value="">None</option>
                    {dynasties.map(d => <option key={d} value={d}>{d}</option>)}
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

export default AuthorDetails;
