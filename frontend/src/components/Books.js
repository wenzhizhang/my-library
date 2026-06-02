import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import './hover.css';
import BookCard from './BookCard';
import { API_BASE_URL } from './Config';
import { LIBRARY_PATH } from '../config';

const Books = () => {
  const [books, setBooks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [limit, setLimit] = useState(10);
  const [sortBy, setSortBy] = useState('title');
  const [totalPages, setTotalPages] = useState(1);
  const [totalBooks, setTotalBooks] = useState(0);
  const [goToPage, setGoToPage] = useState('');
  const [showAdvanced, setShowAdvanced] = useState(false);

  const [inputParams, setInputParams] = useState({
    isbn: '', title: '', author: '', publisher: '', tag: ''
  });

  const [searchParams, setSearchParams] = useState({
    isbn: '', title: '', author: '', publisher: '', tag: ''
  });

  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const init = {
      isbn: params.get('isbn') || '',
      title: params.get('title') || '',
      author: params.get('author') || '',
      publisher: params.get('publisher') || '',
      tag: params.get('tag') || ''
    };
    setPage(parseInt(params.get('page')) || 1);
    setLimit(parseInt(params.get('limit')) || 10);
    setSortBy(params.get('sort_by') || 'title');
    setInputParams(init);
    setSearchParams(init);
  }, []);

  useEffect(() => {
    const params = new URLSearchParams({
      page, limit, sort_by: sortBy,
      ...Object.fromEntries(Object.entries(searchParams).filter(([_, v]) => v))
    });
    navigate(`?${params.toString()}`, { replace: true });
  }, [page, limit, sortBy, searchParams]);

  useEffect(() => { fetchBooks(); }, [page, limit, sortBy, searchParams]);

  const fetchBooks = async () => {
    setLoading(true);
    try {
      const params = {
        page, limit, sort_by: sortBy,
        ...Object.fromEntries(Object.entries(searchParams).filter(([_, v]) => v))
      };
      const response = await axios.get(`${window.location.origin}${API_BASE_URL}/books/`, { params });
      const data = response.data;
      setBooks(data.books || []);
      setTotalPages(data.total_pages || 1);
      setTotalBooks(data.total_books || 0);
    } catch (error) {
      console.error('Error fetching books:', error);
    }
    setLoading(false);
  };

  const handleInputChange = (field, value) => setInputParams(prev => ({ ...prev, [field]: value }));
  const handleSearch = () => { setSearchParams(inputParams); setPage(1); };
  const handleSortChange = (val) => { setSortBy(val); setPage(1); };
  const handleLimitChange = (val) => { setLimit(parseInt(val)); setPage(1); };
  const handleGoToPage = () => {
    const pageNum = Math.min(Math.max(parseInt(goToPage) || 1, 1), totalPages);
    setPage(pageNum);
    setGoToPage('');
  };
  const handleKeyDown = (e) => { if (e.key === 'Enter') handleSearch(); };

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
          <input type="number" min="1" max={totalPages} value={goToPage}
            onChange={(e) => setGoToPage(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleGoToPage()} />
          <button className="btn-pill-link" onClick={handleGoToPage}>Go</button>
        </div>
      </div>
    );
  };

  if (loading) return <div className="loading">Loading...</div>;

  return (
    <section className="section light">
      <div className="container">
        <h1 className="section-heading">Books</h1>

        <button className="btn-primary-blue" onClick={() => navigate(`${LIBRARY_PATH}/books/create`)}>
          Add New Book
        </button>

        {/* Toolbar */}
        <div className="toolbar">

          {/* Search */}
          <div className="toolbar-search">
            <div className="toolbar-search-row">
              <input className="toolbar-search-input" placeholder="ISBN"
                value={inputParams.isbn}
                onChange={(e) => handleInputChange('isbn', e.target.value)}
                onKeyDown={handleKeyDown} />
              <button className="btn-pill-link" onClick={handleSearch}>Search</button>
              <button
                className={`btn-pill-link${showAdvanced ? ' active' : ''}`}
                onClick={() => setShowAdvanced(!showAdvanced)}>
                Advanced
              </button>
            </div>

            {showAdvanced && (
              <div className="toolbar-search-advanced">
                <label className="search-label">
                  <span>Title</span>
                  <input placeholder="Search title…"
                    value={inputParams.title}
                    onChange={(e) => handleInputChange('title', e.target.value)} />
                </label>
                <label className="search-label">
                  <span>Author</span>
                  <input placeholder="Search author…"
                    value={inputParams.author}
                    onChange={(e) => handleInputChange('author', e.target.value)} />
                </label>
                <label className="search-label">
                  <span>Publisher</span>
                  <input placeholder="Search publisher…"
                    value={inputParams.publisher}
                    onChange={(e) => handleInputChange('publisher', e.target.value)} />
                </label>
                <label className="search-label">
                  <span>Tag</span>
                  <input placeholder="Search tag…"
                    value={inputParams.tag}
                    onChange={(e) => handleInputChange('tag', e.target.value)} />
                </label>
              </div>
            )}
          </div>

          {/* Sort & limit */}
          <div className="toolbar-actions">
            <label className="control-label">
              <span className="control-label-text">Sort</span>
              <select value={sortBy} onChange={(e) => handleSortChange(e.target.value)}>
                <option value="id">ID</option>
                <option value="title">Title</option>
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

          {/* Page info */}
          <div className="toolbar-info">
            Page {page} / {totalPages} ({totalBooks})
          </div>
        </div>

        {/* Tag filter badge */}
        {searchParams.tag && (
          <div className="tag-filter-badge">
            <span className="tag-filter-text">Filtered by tag: {searchParams.tag}</span>
            <button onClick={() => {
              const newParams = { ...searchParams, tag: '' };
              setInputParams(prev => ({ ...prev, tag: '' }));
              setSearchParams(newParams);
              setPage(1);
            }} className="tag-filter-close">×</button>
          </div>
        )}

        <div className="grid">
          {books.map(book => (
            <BookCard key={book.id} book={book} />
          ))}
        </div>

        {renderPagination()}
      </div>
    </section>
  );
};

export default Books;
