import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import { API_BASE_URL } from './Config';

const BookCollections = () => {
  const [collections, setCollections] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [limit, setLimit] = useState(10);
  const [sortBy, setSortBy] = useState("name");
  const [totalPages, setTotalPages] = useState(1);
  const [totalCollections, setTotalCollections] = useState(0);
  const [goToPage, setGoToPage] = useState("");
  const [newName, setNewName] = useState("");
  const [newIntro, setNewIntro] = useState("");
  const [error, setError] = useState(null);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");
  const navigate = useNavigate();

  useEffect(() => {
    fetchCollections();
  }, [page, limit, sortBy]);

  const fetchCollections = async () => {
    setLoading(true);
    try {
      const response = await axios.get(
        `${window.location.origin}${API_BASE_URL}/book-collections/?page=${page}&limit=${limit}&sort_by=${sortBy}`
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
  };

  const handleCreate = async (e) => {
    e.preventDefault();
    if (!newName.trim()) return;
    setCreating(true);
    setCreateError("");
    try {
      await axios.post(
        `${window.location.origin}${API_BASE_URL}/book-collections/`,
        { name: newName.trim(), intro: newIntro.trim() || null }
      );
      setNewName("");
      setNewIntro("");
      setPage(1);
      fetchCollections();
    } catch (err) {
      setCreateError(err.response?.data?.detail || "Failed to create collection");
    }
    setCreating(false);
  };

  const handleSortChange = (newSortBy) => {
    setSortBy(newSortBy);
    setPage(1);
  };

  const handleLimitChange = (newLimit) => {
    setLimit(parseInt(newLimit));
    setPage(1);
  };

  const handleGoToPage = () => {
    const pageNum = Math.min(Math.max(parseInt(goToPage) || 1, 1), totalPages);
    setPage(pageNum);
    setGoToPage('');
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      handleGoToPage();
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
        <h1 className="section-heading">Book Collections</h1>

        <div className="toolbar" style={{ marginBottom: "1rem" }}>
          <form onSubmit={handleCreate} style={{ display: "flex", gap: "0.5rem", alignItems: "center", flexWrap: "wrap" }}>
            <input
              type="text"
              placeholder="Collection name"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              required
              style={{ flex: "1 1 200px", padding: "0.5rem", borderRadius: "8px", border: "1px solid #ccc" }}
            />
            <input
              type="text"
              placeholder="Description (optional)"
              value={newIntro}
              onChange={(e) => setNewIntro(e.target.value)}
              style={{ flex: "2 1 300px", padding: "0.5rem", borderRadius: "8px", border: "1px solid #ccc" }}
            />
            <button type="submit" className="btn-primary-blue" disabled={creating || !newName.trim()}>
              {creating ? "Creating..." : "Create Collection"}
            </button>
          </form>
          {createError && <p style={{ color: "#ff3b30", marginTop: "0.5rem" }}>{createError}</p>}
        </div>

        <div className="controls">
          <div className="control-group">
            <label htmlFor="sort">Sort by:</label>
            <select id="sort" value={sortBy} onChange={(e) => handleSortChange(e.target.value)}>
              <option value="name">Name</option>
              <option value="created_at">Date Created</option>
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
            Page {page} of {totalPages} ({totalCollections} total collections)
          </div>
        </div>

        {error && (
          <div style={{background: '#fff0f0', border: '1px solid #ffc0c0', borderRadius: '8px', padding: '0.75rem 1rem', marginBottom: '1rem', display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
            <span style={{color: '#cc0000'}}>{error}</span>
            <div style={{display: 'flex', gap: '0.5rem'}}>
              <button onClick={() => { setError(null); fetchCollections(); }} className="btn-pill-link" style={{padding: '0.25rem 0.75rem'}}>Retry</button>
              <button onClick={() => setError(null)} className="btn-pill-link" style={{padding: '0.25rem 0.75rem'}}>Dismiss</button>
            </div>
          </div>
        )}
        {!error && collections.length === 0 && (
          <p style={{textAlign: 'center', color: '#888', fontSize: '1.1rem', margin: '2rem 0'}}>No book collections yet.</p>
        )}
        {collections.length > 0 && (
        <div className="grid">
          {collections.map(collection => (
            <div key={collection.id} className="card">
              <h3 className="card-title">{collection.name}</h3>
              {collection.intro && (
                <p className="caption">
                  {collection.intro.length > 100
                    ? collection.intro.substring(0, 100) + '...'
                    : collection.intro}
                </p>
              )}
              {collection.total_books !== undefined && (
                <p className="caption">{collection.total_books} books</p>
              )}
              <button
                className="btn-pill-link"
                onClick={() => navigate(`${collection.id}`)}
              >
                View Details
              </button>
            </div>
          ))}
        </div>
        )}

        {renderPagination()}
      </div>
    </section>
  );
};

export default BookCollections;
