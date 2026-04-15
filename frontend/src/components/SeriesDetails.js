import React, { useState, useEffect } from "react";
import { useParams, Link } from "react-router-dom";
import axios from "axios";
import "./Books.css";
import BookCard from './BookCard';

const SeriesDetails = () => {
  const { id } = useParams();
  const [series, setSeries] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchSeries();
  }, [id]);

  const fetchSeries = async () => {
    setLoading(true);
    try {
      const response = await axios.get(`http://localhost:8000/api/series/${id}`);
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
        <button className="btn-primary-blue" onClick={() => window.history.back()}>
          Back to Series
        </button>

        <div className="details-content">
          {series.intro && (
            <p>
              <strong>Introduction:</strong> {series.intro}
            </p>
          )}
          {series.books && series.books.length > 0 && (
            <div>
              <strong>Books in this Series:</strong>
          
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

