import React from 'react';
import { useNavigate } from 'react-router-dom';
import { MEDIA_BASE_URL } from './Config';

const BookCard = ({ book }) => {
  const navigate = useNavigate();

  const handleViewDetails = () => {
    navigate(`/books/${book.id}`);
  };

  const handleEdit = () => {
    navigate(`/books/edit/${book.id}`);
  };

  return (
    <div key={book.id} className="card">
      {book.thumb_image && (
        <img 
          src={`${MEDIA_BASE_URL}/${book.thumb_image}`}
          alt={book.title_cn || book.title} 
          className="card-image hvr-float-shadow" 
        />
      )}
      <h3 className="card-title">{book.title_cn || book.title}</h3>
      <p className="caption">ISBN: {book.isbn}</p>
      <p className="caption">Authors: {book.authors ? book.authors.join(', ') : 'Unknown'}</p>
      <button className="btn-pill-link" onClick={handleViewDetails}>View Details</button>
      <button className="btn-pill-link" onClick={handleEdit}>Edit</button>
    </div>
  );
};

export default BookCard;