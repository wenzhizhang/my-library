import React, { useState, useEffect } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import axios from "axios";
import { useTranslation } from 'react-i18next';
import "./Books.css";
import BookCard from './BookCard';
import { API_BASE_URL } from './Config';

const SeriesDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [series, setSeries] = useState(null);
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchSeries();
  }, [id]);

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
          {series.books && series.books.length > 0 && (
            <div>
              <strong>{t('series.booksInSeries')}:</strong>
          
              <div className="grid">
                {series.books.map((book) => (
                  <BookCard key={book.id} book={book} />
                ))}
                </div>
            </div>
          )}

        </div>
      </div>
    </section>
  );
};

export default SeriesDetails;

