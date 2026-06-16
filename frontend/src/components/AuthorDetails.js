import React, { useState, useEffect } from "react";
import { useParams } from "react-router-dom";
import axios from "axios";
import "./Books.css";
import BookCard from './BookCard';
import { API_BASE_URL, MEDIA_BASE_URL } from './Config';

const AuthorDetails = () => {
  const { id } = useParams();
  const [author, setAuthor] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchAuthor();
  }, [id]);

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

  if (loading) return <div className="loading">Loading...</div>;
  if (!author) return <div className="error">Author not found</div>;

  const displayName = author.name_cn || author.name;
  const nationLabel = author.dynasty && author.dynasty !== '当代' && author.nation === '中国'
    ? `[${author.dynasty}]`
    : author.nation
      ? `[${author.nation}]`
      : '';

  return (
    <section className="section light">
      <div className="container">
        <h1 className="section-heading">{displayName}</h1>
        <button className="btn-primary-blue" onClick={() => window.history.back()}>
          Back to Authors
        </button>

        <div className="details-content" style={{ marginTop: 24 }}>
          <h2>Author Information</h2>

          <div style={infoGridStyle}>
            <div style={infoItemStyle}>
              <span style={infoLabelStyle}>Name</span>
              <span>{author.name}</span>
            </div>
            {author.name_cn && author.name_cn !== author.name && (
              <div style={infoItemStyle}>
                <span style={infoLabelStyle}>Chinese Name</span>
                <span>{author.name_cn}</span>
              </div>
            )}
            <div style={infoItemStyle}>
              <span style={infoLabelStyle}>Nation / Dynasty</span>
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
    </section>
  );
};

const infoGridStyle = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
  gap: 12,
  marginTop: 12,
};

const infoItemStyle = {
  display: 'flex',
  flexDirection: 'column',
  gap: 2,
};

const infoLabelStyle = {
  fontSize: 12,
  fontWeight: 600,
  color: '#86868b',
  textTransform: 'uppercase',
  letterSpacing: '0.05em',
};

export default AuthorDetails;
