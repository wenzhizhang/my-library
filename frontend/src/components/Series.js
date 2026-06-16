import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import "./Books.css";
import { API_BASE_URL } from './Config';

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

  useEffect(() => {
    fetchSeries();
  }, [page, limit, sortBy, submittedQuery]);

  const fetchSeries = async () => {
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
        <h1 className="section-heading">Series</h1>

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
                <option value="created_at">Date Added</option>
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
              {item.description && (
                <p className="caption">
                  {item.description.length > 100
                    ? item.description.substring(0, 100) + "..."
                    : item.description}
                </p>
              )}
              <button
                className="btn-pill-link"
                onClick={() => navigate(`${item.id}`)}
              >
                View Details
              </button>
            </div>
          ))}
        </div>

        {renderPagination()}
      </div>
    </section>
  );
};

export default Series;

