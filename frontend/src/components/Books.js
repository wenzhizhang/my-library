import React, { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import './hover.css';
import { useAuth } from '../AuthContext';
import BookListRow from './BookListRow';
import BookCard from './BookCard';
import SphereView from './SphereView';
import { API_BASE_URL, MEDIA_BASE_URL } from './Config';
import { LIBRARY_PATH } from '../config';
import PageLayout from './PageLayout';

const Books = () => {
  const { t } = useTranslation();
  const { isAuthenticated } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();

  const [books, setBooks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(1);
  const [totalBooks, setTotalBooks] = useState(0);

  // Derive state from URL search params
  const page = parseInt(searchParams.get('page')) || 1;
  const limit = parseInt(searchParams.get('limit')) || 20;
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
      const params = { page, limit, sort_by: sortBy };
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

  const setPageParam = (p) => {
    const newParams = new URLSearchParams(searchParams);
    newParams.set('page', String(p));
    setSearchParams(newParams, { replace: true });
  };

  const activeTag = searchParams.get('tag');

  const sortOptions = [
    { value: 'id', label: t('books.sortId') },
    { value: 'title', label: t('books.sortTitle') },
    { value: 'book_series', label: t('books.sortSeries') },
  ];

  const listColumns = ['', 'ISBN', t('books.sortTitle'), 'Author', 'Publisher', 'Category', 'Actions'];

  const renderItem = (book, viewMode, cols) => {
    if (viewMode === 'list') {
      return <BookListRow key={book.id} book={book} onDeleted={fetchBooks} />;
    }
    return <BookCard key={book.id} book={book} onDelete={fetchBooks} compact={cols === '4' || cols === '5'} />;
  };

  // Advanced search toolbar
  const extraToolbar = (
    <>
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
      {activeTag && (
        <div className="tag-filter-badge" style={{ marginBottom: 16 }}>
          <span className="tag-filter-text">{t('books.searchTag')}: {activeTag}</span>
          <button onClick={() => {
            const newParams = new URLSearchParams(searchParams);
            newParams.delete('tag');
            newParams.set('page', '1');
            setSearchParams(newParams, { replace: true });
          }} className="tag-filter-close">×</button>
        </div>
      )}
    </>
  );

  if (loading) return <div className="loading">{t('common.loading')}</div>;

  return (
    <>
      <PageLayout
        title={t('books.title')}
        createButton={
          <button className="btn-pill-link" onClick={() => {
            sessionStorage.setItem('booksPageState', window.location.search);
            navigate(`${LIBRARY_PATH}/books/create`);
          }} style={{ marginBottom: 20 }}>
            {t('books.addNew')}
          </button>
        }
        searchValue={inputParams.q}
        onSearchChange={(v) => handleInputChange('q', v)}
        onSearch={handleSearch}
        searchPlaceholder={t('books.searchPlaceholder')}
        sortBy={sortBy}
        sortOptions={sortOptions}
        onSort={handleSortChange}
        limit={limit}
        onLimitChange={handleLimitChange}
        page={page}
        totalPages={totalPages}
        totalItems={totalBooks}
        onPageChange={setPageParam}
        layoutKey="books"
        items={books}
        renderItem={renderItem}
        listColumns={listColumns}
        sphereView={
          <SphereView
            items={books}
            getThumb={(b) => (b.thumb_image ? `${MEDIA_BASE_URL}/${b.thumb_image}` : null)}
            getTitle={(b) => b.title_cn || b.title}
            getSubtitle={(b) => [
              b.authors && b.authors.length ? b.authors.join(', ') : null,
              b.isbn ? `ISBN ${b.isbn}` : null,
            ].filter(Boolean).join(' · ')}
            onSelect={(b) => navigate(`${LIBRARY_PATH}/books/${b.id}`)}
          />
        }
        extraToolbar={extraToolbar}
        extraSearchSlot={
          <button
            className={`btn-pill-link${showAdvanced ? ' active' : ''}`}
            onClick={() => setShowAdvanced(!showAdvanced)}>
            {t('books.advanced')}
          </button>
        }
      />
    </>
  );
};

export default Books;
