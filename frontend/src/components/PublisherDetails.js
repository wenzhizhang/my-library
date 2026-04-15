import React, { useState, useEffect } from "react";
import { useParams } from "react-router-dom";
import axios from "axios";
import "./Books.css";
import BookCard from './BookCard';

const PublisherDetails = () => {
  const { id } = useParams();
  const [publisher, setPublisher] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchPublisher();
  }, [id]);

  const fetchPublisher = async () => {
    setLoading(true);
    try {
      const response = await axios.get(
        `http://localhost:8000/api/publishers/${id}`
      );
      setPublisher(response.data);
    } catch (error) {
      console.error("Error fetching publisher:", error);
    }
    setLoading(false);
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
          onClick={() => window.history.back()}
        >
          Back to Publishers
        </button>

        <div className="details-content">
          <h2>Publisher Information</h2>
          <p>
            <strong>Name:</strong> {publisher.name}
          </p>
          {publisher.intro && (
            <p>
              <strong>Introduction:</strong> {publisher.intro}
            </p>
          )}
        </div>
        <div className="grid">
          {publisher.books && publisher.books.length > 0 && (
            publisher.books.map((book) => (
              <BookCard key={book.id} book={book} />
            ))
          )}
        </div>
      </div>
    </section>
  );
};

export default PublisherDetails;

