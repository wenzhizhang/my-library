import React, { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import './hover.css';
import BookCard from './BookCard';
import { API_BASE_URL } from './Config';
import { LIBRARY_PATH } from '../config';

const Wishlist = () => {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();

  const [books, setBooks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(1);
  const [totalBooks, setTotalBooks] = useState(0);
  const [goToPage, setGoToPage] = useState('');

  const page = parseInt(searchParams.get('page')) || 1;
  const limit = parseInt(searchParams.get('limit')) || 10;
  const sortBy = searchParams.get('sort_by') || 'created_at';

  const fetchBooks = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, limit, sort_by: sortBy };
      const response = await axios.get(`${window.location.origin}${API_BASE_URL}/books/wishlist`, { params });
      const data = response.data;
      setBooks(data.books || []);
      setTotalPages(data.total_pages || 1);
      setTotalBooks(data.total_books || 0);
    } catch (error) {
      console.error('Error fetching wishlist:', error);
    }
    setLoading(false);
  }, [page, limit, sortBy]);

  useEffect(() => { fetchBooks(); }, [fetchBooks]);

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

  return (
    <section className="section light">
      <div className="container">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', marginBottom: 24 }}>
          <h1 className="section-heading" style={{ marginBottom: 0 }}>{t('nav.wishlist')}</h1>
          <button className="btn-primary-blue" onClick={() => navigate(`${LIBRARY_PATH}/books/create?wishlist=true`)}>
            {t('books.addNew')}
          </button>
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12, marginBottom: 24 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <label style={{ fontSize: 14, color: '#86868b' }}>{t('books.sortBy')}:</label>
            <select value={sortBy} onChange={(e) => handleSortChange(e.target.value)}
              style={{ padding: '6px 12px', borderRadius: 8, border: '1px solid #d2d2d7', fontSize: 14 }}>
              <option value="created_at">{t('books.sortId')}</option>
              <option value="title">{t('books.sortTitle')}</option>
            </select>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <label style={{ fontSize: 14, color: '#86868b' }}>{t('books.perPage')}:</label>
            <select value={limit} onChange={(e) => handleLimitChange(e.target.value)}
              style={{ padding: '6px 12px', borderRadius: 8, border: '1px solid #d2d2d7', fontSize: 14 }}>
              <option value="10">10</option>
              <option value="20">20</option>
              <option value="50">50</option>
            </select>
          </div>
        </div>

        <div style={{ marginBottom: 16, color: '#86868b', fontSize: 14 }}>
          {totalBooks} {t('books.total')}
        </div>

        <div className="grid">
          {books.length > 0 ? (
            books.map((book) => (
              <BookCard key={book.id} book={book} />
            ))
          ) : (
            <p style={{ gridColumn: '1 / -1', textAlign: 'center', color: '#86868b', padding: 40 }}>
              {t('wishlist.empty')}
            </p>
          )}
        </div>

        {totalPages > 1 && renderPagination()}
      </div>
    </section>
  );
};

export default Wishlist;
