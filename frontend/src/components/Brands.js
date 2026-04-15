import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import './Books.css';

const Brands = () => {
  const [brands, setBrands] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [limit, setLimit] = useState(10);
  const [sortBy, setSortBy] = useState('name');
  const [totalPages, setTotalPages] = useState(1);
  const [totalBrands, setTotalBrands] = useState(0);
  const [goToPage, setGoToPage] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    fetchBrands();
  }, [page, limit, sortBy]);

  const fetchBrands = async () => {
    setLoading(true);
    try {
      const response = await axios.get(`http://localhost:8000/api/brands/?page=${page}&limit=${limit}&sort_by=${sortBy}`);
      const data = response.data;
      setBrands(data.brands || []);
      // Since the API might not have pagination, set defaults
      setTotalPages(data.total_pages || 1);
      setTotalBrands(data.total_brands || 0);
    } catch (error) {
      console.error('Error fetching brands:', error);
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
        <h1 className="section-heading">Brands</h1>

        <div className="controls">
          <div className="control-group">
            <label htmlFor="sort">Sort by:</label>
            <select id="sort" value={sortBy} onChange={(e) => handleSortChange(e.target.value)}>
              <option value="id">ID</option>
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
            Page {page} of {totalPages} ({totalBrands} total brands)
          </div>
        </div>

        <div className="grid">
          {brands.map(brand => (
            <div key={brand.id} className="card">
              <h3 className="card-title">{brand.name}</h3>
              {brand.intro && <p className="caption">{brand.intro.length > 100 ? brand.intro.substring(0, 100) + '...' : brand.intro}</p>}
              <button className="btn-pill-link" onClick={() => navigate(`/brands/${brand.id}`)}>View Details</button>
            </div>
          ))}
        </div>

        {renderPagination()}
      </div>
    </section>
  );
};

export default Brands;
