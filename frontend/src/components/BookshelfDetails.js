import React, { useState, useEffect } from "react";
import { useParams } from "react-router-dom";
import axios from "axios";
import "./Books.css";
import BookCard from './BookCard';

const BookshelfDetails = () => {
  const { id } = useParams();
  const [bookshelf, setBookshelf] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchBookshelf();
  }, [id]);

  const fetchBookshelf = async () => {
    setLoading(true);
    try {
      const response = await axios.get(
        `http://localhost:8000/api/bookshelves/${id}`
      );
      setBookshelf(response.data);
    } catch (error) {
      console.error("Error fetching bookshelf:", error);
    }
    setLoading(false);
  };

  if (loading) {
    return <div className="loading">Loading...</div>;
  }

  if (!bookshelf) {
    return <div className="error">Bookshelf not found</div>;
  }

  return (
    <section className="section light">
      <div className="container">
        <h1 className="section-heading">{bookshelf.name}</h1>
        <button
          className="btn-primary-blue"
          onClick={() => window.history.back()}
        >
          Back to Bookshelves
        </button>

        <div className="details-content">
          <h2>Bookshelf Information</h2>
          <p>
            <strong>Name:</strong> {bookshelf.name}
          </p>
          {bookshelf.description && (
            <p>
              <strong>Description:</strong> {bookshelf.description}
            </p>
          )}
        </div>
        <div className="grid">
          {bookshelf.books && bookshelf.books.length > 0 && (
            bookshelf.books.map((book) => (
              <BookCard key={book.id} book={book} />
            ))
          )}
        </div>
      </div>
    </section>
  );
};

export default BookshelfDetails;

