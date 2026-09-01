import React, { useState, useEffect } from "react";
import { useParams, useNavigate, useSearchParams } from "react-router-dom";
import axios from "axios";
import { useTranslation } from 'react-i18next';
import "./Books.css";
import BookCard from './BookCard';
import { API_BASE_URL } from './Config';
import PageLayout from './PageLayout';
import BookSphere from './BookSphere';
import BookListRow from './BookListRow';

const PublisherDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [publisher, setPublisher] = useState(null);
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [books, setBooks] = useState([]);
  const [searchParams, setSearchParams] = useSearchParams();
  const bookPage = parseInt(searchParams.get('page')) || 1;
  const bookLimit = parseInt(searchParams.get('limit')) || 10;
  const [bookTotalPages, setBookTotalPages] = useState(1);
  const [bookTotalCount, setBookTotalCount] = useState(0);
  const bookSort = searchParams.get('sort_by') || 'title';
  const [bookSearchInput, setBookSearchInput] = useState(searchParams.get('q') || '');
  const bookQuery = searchParams.get('q') || '';

  useEffect(() => {
    fetchPublisher();
  }, [id]);

  useEffect(() => {
    if (id) fetchBooks(bookPage);
  }, [id, bookPage, bookSort, bookLimit, bookQuery]);
  const setPageParam = (p) => {
    const next = new URLSearchParams(searchParams);
    next.set('page', String(p));
    setSearchParams(next, { replace: true });
  };
  const setSortParam = (s) => {
    const next = new URLSearchParams(searchParams);
    next.set('sort_by', s);
    next.set('page', '1');
    setSearchParams(next, { replace: true });
  };
  const setLimitParam = (l) => {
    const next = new URLSearchParams(searchParams);
    next.set('limit', String(l));
    next.set('page', '1');
    setSearchParams(next, { replace: true });
  };
  const setQueryParam = (q) => {
    const next = new URLSearchParams(searchParams);
    if (q) next.set('q', q); else next.delete('q');
    next.set('page', '1');
    setSearchParams(next, { replace: true });
  };

  const fetchPublisher = async () => {
    setLoading(true);
    try {
      const response = await axios.get(
        `${window.location.origin}${API_BASE_URL}/publishers/${id}`
      );
      setPublisher(response.data);
    } catch (error) {
      console.error("Error fetching publisher:", error);
    }
    setLoading(false);
  };

  const fetchBooks = async (p = 1) => {
    try {
      const params = { page: p, limit: bookLimit, sort_by: bookSort };
      if (bookQuery) params.q = bookQuery;
      const response = await axios.get(
        `${window.location.origin}${API_BASE_URL}/publishers/${id}/books`,
        { params }
      );
      setBooks(response.data.books || []);
      setBookTotalPages(response.data.total_pages || 1);
      setBookTotalCount(response.data.total_books || 0);
    } catch (error) {
      console.error("Error fetching publisher books:", error);
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

  if (!publisher) {
    return <div className="error">Publisher not found</div>;
  }

  return (
    <section className="section light">
      <div className="container">
        <h1 className="section-heading">{publisher.name}</h1>
        <button
          className="btn-primary-blue"
          onClick={() => navigate('/my-library/publishers')}
        >
          {t('publishers.backToList')}
        </button>

        <div className="details-content">
          <h2>{t('bookDetails.basicInfo')}</h2>
          <p>
            <strong>{t('common.name')}:</strong> {publisher.name}
          </p>
          {publisher.intro && (
            <p>
              <strong>{t('common.introduction')}:</strong> {publisher.intro}
            </p>
          )}
        </div>
        {bookTotalCount > 0 && (
          <PageLayout
            embedded
            searchValue={bookSearchInput}
            onSearchChange={setBookSearchInput}
            onSearch={() => { setQueryParam(bookSearchInput.trim()); }}
            searchPlaceholder={t('books.searchPlaceholder')}
            sortBy={bookSort}
            sortOptions={sortOptions}
            onSort={(s) => { setSortParam(s) }}
            limit={bookLimit}
            onLimitChange={(l) => { setLimitParam(l) }}
            page={bookPage}
            totalPages={bookTotalPages}
            totalItems={bookTotalCount}
            onPageChange={setPageParam}
            layoutKey="publisher-books"
            items={books}
            renderItem={renderItem}
            listColumns={listColumns}
            sphereView={<BookSphere books={books} />}
          />
        )}
      </div>
    </section>
  );
};

export default PublisherDetails;

