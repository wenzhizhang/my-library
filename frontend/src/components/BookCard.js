import React from 'react';
import { useNavigate } from 'react-router-dom';

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
          // src={`http://localhost:8000/static/images/${book.thumb_image}`} 
          src={`https://zhangwenzhi-1315027057.cos.ap-guangzhou.myqcloud.com/media/${book.thumb_image}`}
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