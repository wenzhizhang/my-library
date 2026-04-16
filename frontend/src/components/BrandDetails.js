import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import BookCard from './BookCard';
import { API_BASE_URL } from './Config';

const BrandDetails = () => {
  const { id } = useParams();
  const [brand, setBrand] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchBrand();
  }, [id]);

  const fetchBrand = async () => {
    setLoading(true);
    try {
      const response = await axios.get(`${API_BASE_URL}/brands/${id}`);
      setBrand(response.data);
    } catch (error) {
      console.error('Error fetching brand:', error);
    }
    setLoading(false);
  };

  if (loading) {
    return <div className="loading">Loading...</div>;
  }

  if (!brand) {
    return <div className="error">Brand not found</div>;
  }

  return (
    <section className="section light">
      <div className="container">
        <h1 className="section-heading">{brand.name}</h1>
        <button className="btn-primary-blue" onClick={() => window.history.back()}>Back to Brands</button>

        <div className="details-content">
          <h2>Brand Information</h2>
          <p><strong>Name:</strong> {brand.name}</p>
          {brand.intro && <p><strong>Introduction:</strong> {brand.intro}</p>}
        </div>
        <div className="grid">
          {brand.books && brand.books.length > 0 && (
            brand.books.map((book) => (
              <BookCard key={book.id} book={book} />
            ))
          )}
        </div>
      </div>
    </section>
  );
};

export default BrandDetails;
