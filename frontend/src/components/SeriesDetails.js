import React, { useState, useEffect } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import axios from "axios";
import { useTranslation } from 'react-i18next';
import "./Books.css";
import BookCard from './BookCard';
import { API_BASE_URL } from './Config';
import PageLayout from './PageLayout';
import BookListRow from './BookListRow';

const SeriesDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [series, setSeries] = useState(null);
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [books, setBooks] = useState([]);
  const [bookPage, setBookPage] = useState(1);
  const [bookLimit, setBookLimit] = useState(10);
  const [bookTotalPages, setBookTotalPages] = useState(1);
  const [bookTotalCount, setBookTotalCount] = useState(0);
  const [bookSort, setBookSort] = useState('title');
  const [bookSearchInput, setBookSearchInput] = useState('');
  const [bookQuery, setBookQuery] = useState('');

  useEffect(() => {
    fetchSeries();
  }, [id]);
  useEffect(() => {
    if (id) fetchBooks(bookPage);
  }, [id, bookPage, bookSort, bookLimit, bookQuery]);

  const fetchSeries = async () => {
    setLoading(true);
    try {
      const response = await axios.get(`${window.location.origin}${API_BASE_URL}/series/${id}`);
      setSeries(response.data);
    } catch (error) {
      console.error("Error fetching series:", error);
    }
    setLoading(false);
  };

  const fetchBooks = async (p = 1) => {
    try {
      const params = { page: p, limit: bookLimit, sort_by: bookSort };
      if (bookQuery) params.q = bookQuery;
      const response = await axios.get(
        `${window.location.origin}${API_BASE_URL}/series/${id}/books`,
        { params }
      );
      setBooks(response.data.books || []);
      setBookTotalPages(response.data.total_pages || 1);
      setBookTotalCount(response.data.total_books || 0);
    } catch (error) {
      console.error("Error fetching series books:", error);
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
      return <BookListRow key={book.id} book={book} onDeleted={() => fetchBooks(bookPage)} />;
    }
    return <BookCard key={book.id} book={book} compact={cols === '4' || cols === '5'} />;
  };

  if (loading) {
    return <div className="loading">Loading...</div>;
  }

  if (!series) {
    return <div className="error">Series not found</div>;
  }

  return (
    <section className="section light">
      <div className="container">
        <h1 className="section-heading">{series.name}</h1>
        <button className="btn-primary-blue" onClick={() => navigate('/my-library/series')}>
          {t('series.backToList')}
        </button>

        <div className="details-content">
          {series.intro && (
            <p>
              <strong>{t('common.introduction')}:</strong> {series.intro}
            </p>
          )}
          {bookTotalCount > 0 && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <strong>{t('series.booksInSeries')}:</strong>
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
                layoutKey="series-books"
                items={books}
                renderItem={renderItem}
                listColumns={listColumns}
              />
            </div>
          )}

        </div>
      </div>
    </section>
  );
};

export default SeriesDetails;

