import React, { useState, useEffect, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import axios from "axios";
import { useTranslation } from 'react-i18next';
import "./Books.css";
import BookCard from './BookCard';
import { API_BASE_URL } from './Config';
import SearchableSelect from './SearchableSelect';
import PageLayout from './PageLayout';
import BookListRow from './BookListRow';

const BookCollectionDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [collection, setCollection] = useState(null);
  const [loading, setLoading] = useState(true);
  const [allBooks, setAllBooks] = useState([]);
  const [pendingBooks, setPendingBooks] = useState([]);
  const pendingIdsRef = useRef(new Set());
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState("");
  const [removingSelected, setRemovingSelected] = useState(false);
  const [manageMode, setManageMode] = useState(false);
  const [selectedIds, setSelectedIds] = useState(new Set());
  // Paginated books for display
  const [displayBooks, setDisplayBooks] = useState([]);
  const [bookPage, setBookPage] = useState(1);
  const [bookLimit, setBookLimit] = useState(10);
  const [bookTotalPages, setBookTotalPages] = useState(1);
  const [bookTotalCount, setBookTotalCount] = useState(0);
  const [bookSort, setBookSort] = useState('title');
  const [bookSearchInput, setBookSearchInput] = useState('');
  const [bookQuery, setBookQuery] = useState('');

  useEffect(() => {
    fetchCollection();
  }, [id]);

  useEffect(() => {
    if (id) fetchPaginatedBooks(bookPage);
  }, [id, bookPage, bookSort, bookLimit, bookQuery]);

  useEffect(() => {
    let cancelled = false;
    const loadBooks = async () => {
      try {
        const response = await axios.get(
          `${window.location.origin}${API_BASE_URL}/books/titles`
        );
        if (cancelled) return;
        const allTitles = response.data || [];
        const existingIds = new Set([
          ...(collection?.books || []).map(b => b.id),
          ...pendingIdsRef.current,
        ]);
        if (cancelled) return;
        setAllBooks(allTitles.filter(b => !existingIds.has(b.id)));
      } catch (err) {
        if (!cancelled) {
          console.error('Error fetching book titles:', err);
          setAllBooks([]);
        }
      }
    };
    if (collection) loadBooks();
    return () => { cancelled = true; };
  }, [collection?.id, collection?.books?.length]);

  useEffect(() => {
    pendingIdsRef.current = new Set(pendingBooks.map(b => b.id));
  }, [pendingBooks]);

  const fetchCollection = async () => {
    setLoading(true);
    try {
      const response = await axios.get(
        `${window.location.origin}${API_BASE_URL}/book-collections/${id}`
      );
      setCollection(response.data);
    } catch (err) {
      console.error("Error fetching book collection:", err);
      setCollection(null);
    }
    setLoading(false);
  };

  const fetchPaginatedBooks = async (p = 1) => {
    try {
      const params = { page: p, limit: bookLimit, sort_by: bookSort };
      if (bookQuery) params.q = bookQuery;
      const response = await axios.get(
        `${window.location.origin}${API_BASE_URL}/book-collections/${id}/books`,
        { params }
      );
      setDisplayBooks(response.data.books || []);
      setBookTotalPages(response.data.total_pages || 1);
      setBookTotalCount(response.data.total_books || 0);
    } catch (error) {
      console.error("Error fetching collection books:", error);
    }
  };

  const sortOptions = [
    { value: 'title', label: t('books.sortTitle') },
    { value: 'created_at', label: t('books.sortCreated') },
    { value: 'book_series', label: t('books.sortSeries') },
  ];

  const listColumns = ['', 'ISBN', t('books.sortTitle'), 'Author', 'Publisher', 'Category', 'Actions'];

  const renderItem = (book, viewMode, cols) => {
    if (viewMode === 'list') {
      return (
        <BookListRow
          key={book.id}
          book={book}
          protectLevel={1}
          showCheckbox={manageMode}
          checked={selectedIds.has(book.id)}
          onCheckChange={handleCheckChange}
          onDeleted={() => fetchPaginatedBooks(bookPage)}
        />
      );
    }
    return (
      <BookCard
        key={book.id}
        book={book}
        protectLevel={1}
        showCheckbox={manageMode}
        checked={selectedIds.has(book.id)}
        onCheckChange={handleCheckChange}
        compact={cols === '4' || cols === '5'}
      />
    );
  };

  const handleSelectBook = (bookId) => {
    if (bookId == null) return;
    const book = allBooks.find(b => b.id === bookId);
    if (!book) return;
    setPendingBooks(prev => [...prev, book]);
    setAllBooks(prev => prev.filter(b => b.id !== bookId));
  };

  const handleRemovePending = (bookId) => {
    const removed = pendingBooks.find(b => b.id === bookId);
    setPendingBooks(prev => prev.filter(b => b.id !== bookId));
    if (removed) {
      setAllBooks(prev => [...prev, removed].sort((a, b) => a.name.localeCompare(b.name)));
    }
  };

  const handleAddAll = async () => {
    if (pendingBooks.length === 0) return;
    setAdding(true);
    setError("");
    try {
      const response = await axios.post(
        `${window.location.origin}${API_BASE_URL}/book-collections/${id}/books/batch`,
        { book_ids: pendingBooks.map(b => b.id) }
      );
      setCollection(response.data);
      setPendingBooks([]);
    } catch (err) {
      const msg = err.response?.data?.detail || "Failed to add books";
      setError(msg);
    }
    setAdding(false);
  };

  const handleCheckChange = (bookId, checked) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (checked) next.add(bookId);
      else next.delete(bookId);
      return next;
    });
  };

  const handleRemoveSelected = async () => {
    if (selectedIds.size === 0 || removingSelected) return;
    setRemovingSelected(true);
    setError("");
    const ids = [...selectedIds];
    let firstError = null;
    for (const bookId of ids) {
      try {
        const response = await axios.delete(
          `${window.location.origin}${API_BASE_URL}/book-collections/${id}/books/${bookId}`
        );
        setCollection(response.data);
      } catch (err) {
        if (!firstError) {
          firstError = err.response?.data?.detail || "Failed to remove book";
        }
      }
    }
    fetchPaginatedBooks(bookPage);
    setSelectedIds(new Set());
    setRemovingSelected(false);
    if (firstError) setError(firstError);
  };

  if (loading) {
    return <div className="loading">Loading...</div>;
  }

  if (!collection) {
    return <div className="error">Book Collection not found</div>;
  }

  const chipStyle = {
    display: "inline-flex",
    alignItems: "center",
    gap: "4px",
    padding: "4px 10px",
    background: "#0071e3",
    color: "#fff",
    borderRadius: "980px",
    fontSize: "14px",
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
  };

  const chipRemoveStyle = {
    cursor: "pointer",
    fontSize: "14px",
    lineHeight: 1,
    marginLeft: "2px",
    opacity: 0.7,
  };

  return (
    <section className="section light">
      <div className="container">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <h1 className="section-heading">{collection.name}</h1>
          <div style={{ display: 'flex', gap: 8 }}>
            <button
              className="btn-pill-link"
              onClick={() => navigate('/my-library/book-collections')}
            >
              {t('collections.backToList')}
            </button>
            {bookTotalCount > 0 && (
              <button
                className="btn-pill-link"
                onClick={() => { setManageMode(!manageMode); setSelectedIds(new Set()); }}
              >
                {manageMode ? t('collections.done') : t('collections.manage')}
              </button>
            )}
          </div>
        </div>

        <div className="details-content">
          <h2>{t('bookDetails.basicInfo')}</h2>
          <p>
            <strong>{t('common.name')}:</strong> {collection.name}
          </p>
          {collection.intro && (
            <p>
              <strong>{t('common.introduction')}:</strong> {collection.intro}
            </p>
          )}
          {collection.total_books !== undefined && (
            <p>
              <strong>{t('collections.books')}:</strong> {collection.total_books}
            </p>
          )}
        </div>

        <div className="details-content" style={{ marginTop: "1rem" }}>
          <h2>{t('collections.addBooks')}</h2>
          {error && <p style={{ color: "#ff3b30" }}>{error}</p>}

          <div style={{ display: "flex", gap: "0.5rem", alignItems: "flex-end" }}>
            <div style={{ flex: 1 }}>
              <SearchableSelect
                label={t('collections.searchBook')}
                value={null}
                onChange={handleSelectBook}
                options={allBooks}
                placeholder={t('collections.searchPlaceholder')}
                keepSearchOnSelect={true}
              />
            </div>
            <button
              className="btn-primary-blue"
              onClick={handleAddAll}
              disabled={adding || pendingBooks.length === 0}
              style={{ marginBottom: "24px" }}
            >
              {adding ? t('collections.adding') : t('collections.addAll', { count: pendingBooks.length })}
            </button>
          </div>

          {pendingBooks.length > 0 && (
            <div style={{ display: "flex", flexWrap: "wrap", gap: "6px", marginBottom: "16px" }}>
              {pendingBooks.map(book => (
                <span key={book.id} style={chipStyle}>
                  {book.name}
                  <span
                    style={chipRemoveStyle}
                    onClick={() => handleRemovePending(book.id)}
                    title={t('collections.remove')}
                  >
                    ×
                  </span>
                </span>
              ))}
            </div>
          )}
        </div>

        <div style={{ marginTop: "2rem" }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h2>{t('collections.booksInCollection')}</h2>
            {manageMode && selectedIds.size > 0 && (
              <button
                className="btn-pill-link"
                onClick={handleRemoveSelected}
                disabled={removingSelected}
                style={{ color: '#ff3b30', opacity: removingSelected ? 0.6 : 1 }}
              >
                {removingSelected ? t('collections.removing') : `${t('collections.removeSelected')} (${selectedIds.size})`}
              </button>
            )}
          </div>
          <PageLayout
            embedded
            searchValue={bookSearchInput}
            onSearchChange={setBookSearchInput}
            onSearch={() => { setBookQuery(bookSearchInput.trim()); setBookPage(1); }}
            searchPlaceholder={t('books.searchPlaceholder')}
            sortBy={bookSort}
            sortOptions={sortOptions}
            onSort={(s) => { setBookSort(s); setBookPage(1); }}
            limit={bookLimit}
            onLimitChange={(l) => { setBookLimit(l); setBookPage(1); }}
            page={bookPage}
            totalPages={bookTotalPages}
            totalItems={bookTotalCount}
            onPageChange={setBookPage}
            layoutKey="collection-books"
            items={displayBooks}
            renderItem={renderItem}
            listColumns={listColumns}
          />
        </div>
      </div>
    </section>
  );
};

export default BookCollectionDetails;
