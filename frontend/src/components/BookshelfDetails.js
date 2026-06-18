import React, { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import axios from "axios";
import "./Books.css";
import BookCard from './BookCard';
import { API_BASE_URL } from './Config';

const BookshelfDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [bookshelf, setBookshelf] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchBookshelf();
  }, [id]);

  const fetchBookshelf = async () => {
    setLoading(true);
    try {
      const response = await axios.get(
        `${window.location.origin}${API_BASE_URL}/bookshelves/${id}`
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
        <button className="btn-primary-blue"
          onClick={() => navigate('/my-library/bookshelves')}
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

