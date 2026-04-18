import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import { API_BASE_URL, MEDIA_BASE_URL } from './Config';

const BookDetails = () => {
  const { id } = useParams();
  const [book, setBook] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchBook();
  }, [id]);

  const fetchBook = async () => {
    setLoading(true);
    try {
      const response = await axios.get(`${API_BASE_URL}/books/${id}/`);
      setBook(response.data);
    } catch (error) {
      console.error('Error fetching book:', error);
    }
    setLoading(false);
  };

  if (loading) {
    return <div className="loading">Loading...</div>;
  }

  if (!book) {
    return <div className="error">Book not found</div>;
  }

  return (
    <section className="section light">
      <div className="container">
        <h1 className="section-heading">{book.title_cn || book.title}</h1>
        <button className="btn-primary-blue" onClick={() => window.history.back()}>Back to Books</button>

        <div className="details-grid">
          {book.thumb_image && (
            <div className="details-image">
              <img src={`${MEDIA_BASE_URL}/${book.thumb_image}`} alt={book.title_cn || book.title} style={{ maxWidth: '500px', height: 'auto' }} />
            </div>
          )}

          <div className="details-content">
            <h2>Basic Information</h2>
            <p><strong>Title:</strong> {book.title}</p>
            {book.title_cn && <p><strong>Chinese Title:</strong> {book.title_cn}</p>}
            {book.isbn && <p><strong>ISBN:</strong> {book.isbn}</p>}
            {book.authors && (
              <p><strong>Authors:</strong> {
                book.authors.map((author, index) => (
                  <React.Fragment key={author?.id}>
                    <a href={`/authors/${author?.id}`}>{author?.formated_name}</a>
                    {index < book.authors.length - 1 && ', '}
                  </React.Fragment>
                ))
              }</p>
            )}
            {book.translators && <p><strong>Translators:</strong> {book.translators.join(', ')}</p>}
            {book.publisher && <p><strong>Publisher:</strong> {book.publisher.name}</p>}
            {book.publish_date && <p><strong>Publish Date:</strong> {new Date(book.publish_date).toLocaleDateString()}</p>}

            <h2>Physical Details</h2>
            {book.binding_type && <p><strong>Binding Type:</strong> {book.binding_type}</p>}
            {book.paper_type && <p><strong>Paper Type:</strong> {book.paper_type}</p>}
            {book.pages && <p><strong>Pages:</strong> {book.pages}</p>}
            {book.book_count && <p><strong>Book Count:</strong> {book.book_count}</p>}
            {book.language && <p><strong>Language:</strong> {book.language}</p>}
            {book.compose_type && <p><strong>Compose Type:</strong> {book.compose_type}</p>}

            <h2>Pricing and Purchase</h2>
            {book.price && <p><strong>Price:</strong> ¥ {Number(book.price).toFixed(2)}</p>}
            {book.purchase_price && <p><strong>Purchase Price:</strong> ¥ {Number(book.purchase_price).toFixed(2)}</p>}
            {book.purchase_date && <p><strong>Purchase Date:</strong> {new Date(book.purchase_date).toLocaleDateString()}</p>}
            {book.purchase_store && <p><strong>Purchase Store:</strong> {book.purchase_store}</p>}

            <h2>Classification</h2>
            {book.brand && <p><strong>Brand:</strong> {book.brand.name}</p>}
            {book.book_series && <p><strong>Series:</strong> {book.book_series.name}</p>}
            {book.category && <p><strong>Category:</strong> {book.category.name}</p>}
            {book.bookshelf && <p><strong>Bookshelf:</strong> {book.bookshelf.name}</p>}

            <h2>Reading Status</h2>
            <p><strong>Read State:</strong> {book.read_state || 'Not set'}</p>
            <p><strong>Registered:</strong> {book.registered ? 'Yes' : 'No'}</p>
            <p><strong>In Wish List:</strong> {book.in_wish ? 'Yes' : 'No'}</p>

            <h2>Additional Information</h2>
            {book.edition && <p><strong>Edition:</strong> {book.edition}</p>}
            {book.print && <p><strong>Print:</strong> {book.print}</p>}
            {book.printed_number && <p><strong>Printed Number:</strong> {book.printed_number}</p>}
            {book.douban_score && <p><strong>Douban Score:</strong> {book.douban_score}</p>}
            {book.tags && <p><strong>Tags:</strong> {book.tags.join(', ')}</p>}
            {book.link && <p><strong>Link:</strong> <a href={book.link} target="_blank" rel="noopener noreferrer">{book.link}</a></p>}

            <h2>Content</h2>
            {book.introduction && <p><strong>Introduction:</strong> {book.introduction}</p>}
            {book.summary && <p><strong>Summary:</strong> {book.summary}</p>}
            {book.catalog && <p><strong>Catalog:</strong> {book.catalog}</p>}
          </div>
        </div>
      </div>
    </section>
  );
};

export default BookDetails;
