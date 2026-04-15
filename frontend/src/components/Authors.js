import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import './Books.css';

const Authors = () => {
  const [authors, setAuthors] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [limit, setLimit] = useState(10);
  const [sortBy, setSortBy] = useState('name');
  const [totalPages, setTotalPages] = useState(1);
  const [totalAuthors, setTotalAuthors] = useState(0);
  const [goToPage, setGoToPage] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    fetchAuthors();
  }, [page, limit, sortBy]);

  const fetchAuthors = async () => {
    setLoading(true);
    try {
      const response = await axios.get(`http://localhost:8000/api/authors/?page=${page}&limit=${limit}&sort_by=${sortBy}`);
      const data = response.data;
      setAuthors(data.authors || []);
      setTotalPages(data.total_pages || 1);
      setTotalAuthors(data.total_authors || 0);
    } catch (error) {
      console.error('Error fetching authors:', error);
    }
    setLoading(false);
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
        <h1 className="section-heading">Authors</h1>

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
                <img src={`https://zhangwenzhi-1315027057.cos.ap-guangzhou.myqcloud.com/media/${author.photo}`} alt={author.name_cn || author.name} className="card-image authors-image hvr-float-shadow" />
              )}
              <h3 className="card-title">{author.name}</h3>
              {author.bio && <p className="caption">{author.bio.length > 100 ? author.bio.substring(0, 100) + '...' : author.bio}</p>}
              <button className="btn-pill-link" onClick={() => navigate(`/authors/${author.id}`)}>View Details</button>
            </div>
          ))}
        </div>

        {renderPagination()}
      </div>
    </section>
  );
};

export default Authors;
