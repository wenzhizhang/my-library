import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import './hover.css';
import BookCard from './BookCard';
import { API_BASE_URL } from './Config';
import { LIBRARY_PATH } from '../config';

const Books = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();

  const [books, setBooks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(1);
  const [totalBooks, setTotalBooks] = useState(0);
  const [goToPage, setGoToPage] = useState('');

  // Derive state from URL search params
  const page = parseInt(searchParams.get('page')) || 1;
  const limit = parseInt(searchParams.get('limit')) || 10;
  const sortBy = searchParams.get('sort_by') || 'title';

  const [showAdvanced, setShowAdvanced] = useState(false);

  // Input state for form fields (not yet committed)
  const [inputParams, setInputParams] = useState({
    isbn: searchParams.get('isbn') || '',
    title: searchParams.get('title') || '',
    author: searchParams.get('author') || '',
    publisher: searchParams.get('publisher') || '',
    tag: searchParams.get('tag') || ''
  });

  // Sync input state when URL changes externally (e.g. tag link from BookDetails)
  useEffect(() => {
    setInputParams({
      isbn: searchParams.get('isbn') || '',
      title: searchParams.get('title') || '',
      author: searchParams.get('author') || '',
      publisher: searchParams.get('publisher') || '',
      tag: searchParams.get('tag') || ''
    });
  }, [searchParams]);

  const fetchBooks = useCallback(async () => {
    setLoading(true);
    try {
      const params = {
        page,
        limit,
        sort_by: sortBy,
      };

      // Add non-empty filter params
      for (const key of ['isbn', 'title', 'author', 'publisher', 'tag']) {
        const val = searchParams.get(key);
        if (val) params[key] = val;
      }

      const response = await axios.get(`${window.location.origin}${API_BASE_URL}/books/`, { params });
      const data = response.data;
      setBooks(data.books || []);
      setTotalPages(data.total_pages || 1);
      setTotalBooks(data.total_books || 0);
    } catch (error) {
      console.error('Error fetching books:', error);
    }
    setLoading(false);
  }, [page, limit, sortBy, searchParams]);

  useEffect(() => { fetchBooks(); }, [fetchBooks]);

  const handleInputChange = (field, value) => setInputParams(prev => ({ ...prev, [field]: value }));

  const handleSearch = () => {
    const newParams = new URLSearchParams(searchParams);
    for (const key of ['isbn', 'title', 'author', 'publisher', 'tag']) {
      if (inputParams[key]) {
        newParams.set(key, inputParams[key]);
      } else {
        newParams.delete(key);
      }
    }
    newParams.set('page', '1');
    setSearchParams(newParams, { replace: true });
  };

  const handleSortChange = (val) => {
    const newParams = new URLSearchParams(searchParams);
    newParams.set('sort_by', val);
    newParams.set('page', '1');
    setSearchParams(newParams, { replace: true });
  };

  const handleLimitChange = (val) => {
    const newParams = new URLSearchParams(searchParams);
    newParams.set('limit', val);
    newParams.set('page', '1');
    setSearchParams(newParams, { replace: true });
  };

  const handleGoToPage = () => {
    const pageNum = Math.min(Math.max(parseInt(goToPage) || 1, 1), totalPages);
    const newParams = new URLSearchParams(searchParams);
    newParams.set('page', String(pageNum));
    setSearchParams(newParams, { replace: true });
    setGoToPage('');
  };

  const handleKeyDown = (e) => { if (e.key === 'Enter') handleSearch(); };

  const renderPagination = () => {
    const pages = [];
    const startPage = Math.max(1, page - 2);
    const endPage = Math.min(totalPages, page + 2);
    for (let i = startPage; i <= endPage; i++) {
      pages.push(
        <button
          key={i}
          className={`btn-pill-link ${i === page ? 'active' : ''}`}
          onClick={() => {
            const newParams = new URLSearchParams(searchParams);
            newParams.set('page', String(i));
            setSearchParams(newParams, { replace: true });
          }}
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
              <button className="btn-pill-link" onClick={() => {
                const newParams = new URLSearchParams(searchParams);
                newParams.set('page', '1');
                setSearchParams(newParams, { replace: true });
              }}>First</button>
              <button className="btn-pill-link" onClick={() => {
                const newParams = new URLSearchParams(searchParams);
                newParams.set('page', String(page - 1));
                setSearchParams(newParams, { replace: true });
              }}>Previous</button>
            </>
          )}
          {pages}
          {page < totalPages && (
            <>
              <button className="btn-pill-link" onClick={() => {
                const newParams = new URLSearchParams(searchParams);
                newParams.set('page', String(page + 1));
                setSearchParams(newParams, { replace: true });
              }}>Next</button>
              <button className="btn-pill-link" onClick={() => {
                const newParams = new URLSearchParams(searchParams);
                newParams.set('page', String(totalPages));
                setSearchParams(newParams, { replace: true });
              }}>Last</button>
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

  const activeTag = searchParams.get('tag');

  return (
    <section className="section light">
      <div className="container">
        <h1 className="section-heading">Books</h1>

        <button className="btn-primary-blue" onClick={() => { sessionStorage.setItem('booksPageState', window.location.search); navigate(`${LIBRARY_PATH}/books/create`); }}>
          Add New Book
        </button>

        {/* Toolbar */}
        <div className="toolbar">

          {/* Search */}
          <div className="toolbar-search">
            <div className="toolbar-search-row">
              <input className="toolbar-search-input" placeholder="Search title…"
                value={inputParams.title}
                onChange={(e) => handleInputChange('title', e.target.value)}
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
                  <span>ISBN</span>
                  <input placeholder="Search ISBN…"
                    value={inputParams.isbn}
                    onChange={(e) => handleInputChange('isbn', e.target.value)} />
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
        {activeTag && (
          <div className="tag-filter-badge">
            <span className="tag-filter-text">Filtered by tag: {activeTag}</span>
            <button onClick={() => {
              const newParams = new URLSearchParams(searchParams);
              newParams.delete('tag');
              newParams.set('page', '1');
              setSearchParams(newParams, { replace: true });
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
