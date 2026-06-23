import React, { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import axios from "axios";
import "./Books.css";
import BookCard from './BookCard';
import { API_BASE_URL, MEDIA_BASE_URL } from './Config';
import { useAuth } from '../AuthContext';
import { useTranslation } from 'react-i18next';

const AuthorDetails = () => {
  const { id } = useParams();
  const { isAuthenticated } = useAuth();
  const { t } = useTranslation();
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

  if (loading) return <div className="loading">{t('common.loading')}</div>;
  if (!author) return <div className="error">{t('authors.notFound')}</div>;

  // Build heading: [Nation/Dynasty] 中文名 | 原名   or   [Nation/Dynasty] 原名
  const heading = author.name_cn && author.name_cn !== author.name
    ? `${author.nation === '中国' && author.dynasty ? `[${author.dynasty}] ` : author.nation ? `[${author.nation}] ` : ''}${author.name_cn} | ${author.name}`
    : `${author.nation === '中国' && author.dynasty ? `[${author.dynasty}] ` : author.nation ? `[${author.nation}] ` : ''}${author.name}`;

  return (
    <section className="section light">
      <div className="container">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
          <h1 className="section-heading" style={{ margin: 0 }}>{heading}</h1>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn-primary-blue" onClick={() => navigate('/my-library/authors')}>
              {t('authors.backToList')}
            </button>
            {isAuthenticated && (
              <button className="btn-primary-blue" onClick={openEdit}>
                {t('common.edit')}
              </button>
            )}
          </div>
        </div>

        <div className="details-content" style={{ marginTop: 24 }}>

          {author.intro && (
            <div style={{ marginTop: 20 }}>
              <h3 style={{ fontSize: 17, fontWeight: 600, marginBottom: 8 }}>{t('authors.introduction')}</h3>
              <p style={{ lineHeight: 1.7, color: '#1d1d1f', whiteSpace: 'pre-wrap' }}>
                {author.intro}
              </p>
            </div>
          )}

          {author.photo && (
            <div style={{ marginTop: 20 }}>
              <h3 style={{ fontSize: 17, fontWeight: 600, marginBottom: 8 }}>{t('authors.photo')}</h3>
              <img src={`${MEDIA_BASE_URL}/${author.photo}`} alt={author.name_cn || author.name}
                style={{ maxWidth: 200, borderRadius: 8 }} />
            </div>
          )}
        </div>

        {author.books && author.books.length > 0 && (
          <>
            <h2 style={{ marginTop: 40, marginBottom: 16 }}>{t('authors.books', { count: author.books.length })} ({author.books.length})</h2>
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
          background: 'rgba(0,0,0,0.4)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
        }} onClick={() => setModalOpen(false)}>
          <div style={{
            background: 'rgba(255, 255, 255, 0.6)',
            borderRadius: 20, padding: '24px 28px',
            width: Math.min(560, window.innerWidth - 32),
            maxHeight: '90vh', overflow: 'auto',
            boxShadow: '0 25px 50px rgba(0,0,0,0.25)',
          }} onClick={(e) => e.stopPropagation()}>
            <h2 style={{ margin: '0 0 20px', fontSize: 22, fontWeight: 600 }}>{t('authors.edit')}</h2>
            <form onSubmit={handleSave}>
              <div style={{ marginBottom: 14 }}>
                <label style={labelStyle}>{t('authors.name')} <span style={{ color: '#ff3b30' }}>*</span></label>
                <input type="text" value={formData.name} required
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  style={inputStyle} placeholder={t('authors.namePlaceholder')} />
              </div>
              <div style={{ marginBottom: 14 }}>
                <label style={labelStyle}>{t('authors.nameCn')}</label>
                <input type="text" value={formData.name_cn}
                  onChange={(e) => setFormData({ ...formData, name_cn: e.target.value })}
                  style={inputStyle} placeholder={t('authors.namePlaceholder')} />
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
                    <option value="">None</option>
                    {dynasties.map(d => <option key={d} value={d}>{d}</option>)}
                  </select>
                </div>
              </div>
              <div style={{ marginBottom: 14 }}>
                <label style={labelStyle}>{t('authors.introduction')}</label>
                <textarea value={formData.intro} rows={4}
                  onChange={(e) => setFormData({ ...formData, intro: e.target.value })}
                  style={{ ...inputStyle, resize: 'vertical' }} placeholder={t('authors.bioPlaceholder')} />
              </div>
              <div style={{ marginBottom: 20 }}>
                <label style={labelStyle}>{t('authors.photoUrl')}</label>
                <input type="text" value={formData.photo}
                  onChange={(e) => setFormData({ ...formData, photo: e.target.value })}
                  style={inputStyle} placeholder={t('authors.photoPlaceholder')} />
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
