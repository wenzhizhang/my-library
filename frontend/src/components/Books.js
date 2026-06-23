import React, { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import './hover.css';
import BookCard from './BookCard';
import { API_BASE_URL } from './Config';
import { LIBRARY_PATH } from '../config';

const Books = () => {
  const { t } = useTranslation();
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
    q: searchParams.get('q') || '',
    isbn: searchParams.get('isbn') || '',
    title: searchParams.get('title') || '',
    author: searchParams.get('author') || '',
    publisher: searchParams.get('publisher') || '',
    tag: searchParams.get('tag') || '',
    purchase_year: searchParams.get('purchase_year') || '',
    purchase_month: searchParams.get('purchase_month') || '',
  });
  // Sync input state when URL changes externally
  useEffect(() => {
    setInputParams({
      q: searchParams.get('q') || '',
      isbn: searchParams.get('isbn') || '',
      title: searchParams.get('title') || '',
      author: searchParams.get('author') || '',
      publisher: searchParams.get('publisher') || '',
      tag: searchParams.get('tag') || '',
      purchase_year: searchParams.get('purchase_year') || '',
      purchase_month: searchParams.get('purchase_month') || '',
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
      for (const key of ['q', 'isbn', 'title', 'author', 'publisher', 'tag', 'purchase_year', 'purchase_month']) {
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
    for (const key of ['q', 'isbn', 'title', 'author', 'publisher', 'tag', 'purchase_year', 'purchase_month']) {
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
                }}>{t('common.first')}</button>
              <button className="btn-pill-link" onClick={() => {
                const newParams = new URLSearchParams(searchParams);
                newParams.set('page', String(page - 1));
                setSearchParams(newParams, { replace: true });
                }}>{t('common.previous')}</button>
            </>
          )}
          {pages}
          {page < totalPages && (
            <>
              <button className="btn-pill-link" onClick={() => {
                const newParams = new URLSearchParams(searchParams);
                newParams.set('page', String(page + 1));
                setSearchParams(newParams, { replace: true });
                }}>{t('common.next')}</button>
              <button className="btn-pill-link" onClick={() => {
                const newParams = new URLSearchParams(searchParams);
                newParams.set('page', String(totalPages));
                setSearchParams(newParams, { replace: true });
                }}>{t('common.last')}</button>
            </>
          )}
        </div>
        <div className="pagination-input">
          <input type="number" min="1" max={totalPages} value={goToPage}
            onChange={(e) => setGoToPage(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleGoToPage()} />
          <button className="btn-pill-link" onClick={handleGoToPage}>{t('common.goToPage')}</button>
        </div>
      </div>
    );
  };

    if (loading) return <div className="loading">{t('common.loading')}</div>;

  const activeTag = searchParams.get('tag');

  return (
    <section className="section light">
      <div className="container">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h1 className="section-heading">{t('books.title')}</h1>
          <button className="btn-pill-link" onClick={() => { sessionStorage.setItem('booksPageState', window.location.search); navigate(`${LIBRARY_PATH}/books/create`); }} style={{ marginBottom: 20 }}>
            {t('books.addNew')}
          </button>
        </div>

        {/* Toolbar */}
        <div className="toolbar">
          {/* Search */}
          <div className="toolbar-search">
            <div className="toolbar-search-row">
              <input className="toolbar-search-input" placeholder={t('books.searchPlaceholder')}
                value={inputParams.q}
                onChange={(e) => handleInputChange('q', e.target.value)}
                onKeyDown={handleKeyDown} />
              <button className="btn-pill-link" onClick={handleSearch}>{t('common.search')}</button>
              <button
                className={`btn-pill-link${showAdvanced ? ' active' : ''}`}
                onClick={() => setShowAdvanced(!showAdvanced)}>
                {t('books.advanced')}
              </button>
            </div>

            {showAdvanced && (
              <div className="toolbar-search-advanced">
                <label className="search-label">
                  <span>{t('books.searchIsbn')}</span>
                  <input placeholder={t('books.searchIsbn')}
                    value={inputParams.isbn}
                    onChange={(e) => handleInputChange('isbn', e.target.value)} />
                </label>
                <label className="search-label">
                  <span>{t('books.searchAuthor')}</span>
                  <input placeholder={t('books.searchAuthor')}
                    value={inputParams.author}
                    onChange={(e) => handleInputChange('author', e.target.value)} />
                </label>
                <label className="search-label">
                  <span>{t('books.searchPublisher')}</span>
                  <input placeholder={t('books.searchPublisher')}
                    value={inputParams.publisher}
                    onChange={(e) => handleInputChange('publisher', e.target.value)} />
                </label>
                <label className="search-label">
                  <span>{t('books.searchTag')}</span>
                  <input placeholder={t('books.searchTag')}
                    value={inputParams.tag}
                    onChange={(e) => handleInputChange('tag', e.target.value)} />
                </label>
                <label className="search-label">
                  <span>{t('books.purchaseYear')}</span>
                  <input type="number" min="2000" max="2099" step="1" placeholder={t('books.purchaseYear')}
                    value={inputParams.purchase_year}
                    onChange={(e) => handleInputChange('purchase_year', e.target.value)} />
                </label>
                <label className="search-label">
                  <span>{t('books.purchaseMonth')}</span>
                  <input type="number" min="1" max="12" step="1" placeholder={t('books.purchaseMonth')}
                    value={inputParams.purchase_month}
                    onChange={(e) => handleInputChange('purchase_month', e.target.value)} />
                </label>
              </div>
            )}
          </div>

          {/* Sort & limit */}
          <div className="toolbar-actions">
            <label className="control-label">
              <span className="control-label-text">{t('common.sort')}</span>
              <select value={sortBy} onChange={(e) => handleSortChange(e.target.value)}>
                <option value="id">{t('books.sortId')}</option>
                <option value="title">{t('books.sortTitle')}</option>
              </select>
            </label>
            <label className="control-label">
              <span className="control-label-text">{t('common.perPage')}</span>
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
            {t('common.page')} {page} {t('common.of')} {totalPages} {t('common.total')}({totalBooks})
          </div>
        </div>

        {/* Tag filter badge */}
        {activeTag && (
          <div className="tag-filter-badge">
            <span className="tag-filter-text">{t('books.searchTag')}: {activeTag}</span>
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
            <BookCard key={book.id} book={book} onDelete={fetchBooks} />
          ))}
        </div>

        {renderPagination()}
      </div>
    </section>
  );
};

export default Books;
