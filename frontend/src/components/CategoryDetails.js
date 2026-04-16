import React, { useState, useEffect } from "react";
import { useParams } from "react-router-dom";
import axios from "axios";
import "./Books.css";
import BookCard from './BookCard';
import { API_BASE_URL } from './Config';

const CategoryDetails = () => {
  const { id } = useParams();
  const [category, setCategory] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchCategory();
  }, [id]);

  const fetchCategory = async () => {
    setLoading(true);
    try {
      const response = await axios.get(
        `${API_BASE_URL}/categories/${id}`
      );
      setCategory(response.data);
    } catch (error) {
      console.error("Error fetching category:", error);
    }
    setLoading(false);
  };

  if (loading) {
    return <div className="loading">Loading...</div>;
  }

  if (!category) {
    return <div className="error">Category not found</div>;
  }

  return (
    <section className="section light">
      <div className="container">
        <h1 className="section-heading">{category.name}</h1>
        <button
          className="btn-primary-blue"
          onClick={() => window.history.back()}
        >
          Back to Categories
        </button>

        <div className="details-content">
          <h2>Category Information</h2>
          <p>
            <strong>Name:</strong> {category.name}
          </p>
          {category.parent !== null && (
            <p>
              <strong>Parent ID:</strong> {category.parent}
            </p>
          )}
          {category.intro && (
            <p>
              <strong>Introduction:</strong> {category.intro}
            </p>
          )}
          {category.depth !== null && (
            <p>
              <strong>Depth:</strong> {category.depth}
            </p>
          )}
          {category.path && (
            <p>
              <strong>Path:</strong> {category.path}
            </p>
          )}
        </div>
        <div className="grid">
          {category.books && category.books.length > 0 && (
            category.books.map((book) => (
              <BookCard key={book.id} book={book} />
            ))
          )}
        </div>
      </div>
    </section>
  );
};

export default CategoryDetails;

