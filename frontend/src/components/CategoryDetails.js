import React, { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import axios from "axios";
import { useTranslation } from 'react-i18next';
import "./Books.css";
import BookCard from './BookCard';
import { API_BASE_URL } from './Config';

const CategoryDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [category, setCategory] = useState(null);
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchCategory();
  }, [id]);

  const fetchCategory = async () => {
    setLoading(true);
    try {
      const response = await axios.get(
        `${window.location.origin}${API_BASE_URL}/categories/${id}`
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
          onClick={() => navigate('/my-library/categories')}
        >
          {t('categories.backToList')}
        </button>

        <div className="details-content">
          <h2>{t('bookDetails.basicInfo')}</h2>
          <p>
            <strong>{t('common.name')}:</strong> {category.name}
          </p>
          {category.parent !== null && (
            <p>
              <strong>{t('categories.parent')}:</strong> {category.parent}
            </p>
          )}
          {category.intro && (
            <p>
              <strong>{t('common.introduction')}:</strong> {category.intro}
            </p>
          )}
          {category.depth !== null && (
            <p>
              <strong>{t('categories.depth')}:</strong> {category.depth}
            </p>
          )}
          {category.path && (
            <p>
              <strong>{t('categories.path')}:</strong> {category.path}
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

