import React, { useState, useEffect } from "react";
import { useParams } from "react-router-dom";
import axios from "axios";
import "./Books.css";
import BookCard from './BookCard';
import { API_BASE_URL, MEDIA_BASE_URL } from './Config';

const AuthorDetails = () => {
  const { id } = useParams();
  const [author, setAuthor] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchAuthor();
  }, [id]);

  const fetchAuthor = async () => {
    setLoading(true);
    try {
      const response = await axios.get(
        `${API_BASE_URL}/authors/${id}`
      );
      setAuthor(response.data);
    } catch (error) {
      console.error("Error fetching author:", error);
    }
    setLoading(false);
  };

  if (loading) {
    return <div className="loading">Loading...</div>;
  }

  if (!author) {
    return <div className="error">Author not found</div>;
  }

  return (
    <section className="section light">
      <div className="container">
        <h1 className="section-heading">{author.name}</h1>
        <button
          className="btn-primary-blue"
          onClick={() => window.history.back()}
        >
          Back to Authors
        </button>

        <div className="details-content">
          <h2>Author Information</h2>
          <p>
            <strong>Name:</strong> {author.name}
          </p>
          {author.bio && (
            <p>
              <strong>Biography:</strong> {author.bio}
            </p>
          )}
        </div>
        <div className="grid">
          {author.books.map((book) => (
            <BookCard key={book.id} book={book} />
          ))}
        </div>
      </div>
    </section>
  );
};

export default AuthorDetails;

