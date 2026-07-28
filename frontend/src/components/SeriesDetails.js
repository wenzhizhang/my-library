import React, { useState, useEffect } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import axios from "axios";
import { useTranslation } from 'react-i18next';
import "./Books.css";
import BookCard from './BookCard';
import { API_BASE_URL } from './Config';
import PaginationBar from './PaginationBar';

const SeriesDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [series, setSeries] = useState(null);
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [books, setBooks] = useState([]);
  const [bookPage, setBookPage] = useState(1);
  const bookLimit = 10;
  const [bookTotalPages, setBookTotalPages] = useState(1);
  const [bookTotalCount, setBookTotalCount] = useState(0);
  const [goToPage, setGoToPage] = useState('');

  useEffect(() => {
    fetchSeries();
  }, [id]);
  useEffect(() => {
    if (id) fetchBooks(bookPage);
  }, [id, bookPage]);

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
      const response = await axios.get(
        `${window.location.origin}${API_BASE_URL}/series/${id}/books`,
        { params: { page: p, limit: bookLimit } }
      );
      setBooks(response.data.books || []);
      setBookTotalPages(response.data.total_pages || 1);
      setBookTotalCount(response.data.total_books || 0);
    } catch (error) {
      console.error("Error fetching series books:", error);
    }
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
              <strong>{t('series.booksInSeries')}:</strong>
              <div className="grid">
                {books.map((book) => (
                  <BookCard key={book.id} book={book} />
                ))}
              </div>
              {bookTotalPages > 1 && (
                <PaginationBar
                  page={bookPage}
                  totalPages={bookTotalPages}
                  goToPage={goToPage}
                  setGoToPage={setGoToPage}
                  onPageChange={(p) => setBookPage(p)}
                />
              )}
            </div>
          )}

        </div>
      </div>
    </section>
  );
};

export default SeriesDetails;

