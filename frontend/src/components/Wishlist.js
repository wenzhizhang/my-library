import React, { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import './hover.css';
import BookCard from './BookCard';
import { API_BASE_URL, MEDIA_BASE_URL } from './Config';
import { LIBRARY_PATH } from '../config';
import PageLayout from './PageLayout';

const Wishlist = () => {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();

  const [books, setBooks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(1);
  const [totalBooks, setTotalBooks] = useState(0);

  const page = parseInt(searchParams.get('page')) || 1;
  const limit = parseInt(searchParams.get('limit')) || 20;
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

  const setPageParam = (p) => {
    const newParams = new URLSearchParams(searchParams);
    newParams.set('page', String(p));
    setSearchParams(newParams, { replace: true });
  };

  const sortOptions = [
    { value: 'created_at', label: t('books.sortId') },
    { value: 'title', label: t('books.sortTitle') },
    { value: 'book_series', label: t('books.sortSeries') },
  ];

  const listColumns = ['', 'ISBN', t('books.sortTitle'), 'Author', 'Publisher', ''];

  const renderItem = (book, viewMode, cols) => {
    if (viewMode === 'list') {
      return (
        <tr key={book.id} onClick={() => navigate(`${LIBRARY_PATH}/books/${book.id}`)}>
          <td style={{ width: 48, padding: '6px 8px' }}>
            {book.thumb_image ? (
              <img src={`${MEDIA_BASE_URL}/${book.thumb_image}`} alt="" style={{ width: 40, height: 52, borderRadius: 3, objectFit: 'cover', display: 'block' }} />
            ) : (
              <div style={{ width: 40, height: 52, borderRadius: 3, background: '#e5e5ea', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16, color: '#86868b' }}>
                {book.title?.[0] || 'B'}
              </div>
            )}
          </td>
          <td className="list-cell-secondary" style={{ width: 130, fontSize: 12, fontFamily: 'monospace' }}>{book.isbn || ''}</td>
          <td className="list-cell-primary">{book.title}</td>
          <td className="list-cell-secondary">
            {book.authors?.join(', ') || ''}
          </td>
          <td className="list-cell-secondary">
            {book.publisher?.name || ''}
          </td>
          <td style={{ width: 60, textAlign: 'right' }}>
            {book.douban_score != null && (
              <span style={{ color: '#f5a623', fontSize: 13 }}>★ {book.douban_score}</span>
            )}
          </td>
        </tr>
      );
    }
    return <BookCard key={book.id} book={book} compact={cols === '4' || cols === '5'} />;
  };

  if (loading) return <div className="loading">{t('common.loading')}</div>;

  return (
    <PageLayout
      title={t('nav.wishlist')}
      createButton={
        <button className="btn-primary-blue" onClick={() => navigate(`${LIBRARY_PATH}/books/create?wishlist=true`)}>
          {t('books.addNew')}
        </button>
      }
      searchValue=""
      onSearchChange={() => {}}
      onSearch={null}
      sortBy={sortBy}
      sortOptions={sortOptions}
      onSort={handleSortChange}
      limit={limit}
      onLimitChange={handleLimitChange}
      page={page}
      totalPages={totalPages}
      totalItems={totalBooks}
      onPageChange={setPageParam}
      layoutKey="wishlist"
      items={books}
      renderItem={renderItem}
      listColumns={listColumns}
    />
  );
};

export default Wishlist;
