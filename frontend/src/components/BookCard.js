import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../AuthContext';
import { MEDIA_BASE_URL } from './Config';
import { LIBRARY_PATH } from '../config';

const BookCard = ({ book, onDelete }) => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { isAuthenticated } = useAuth();
  const [showConfirm, setShowConfirm] = React.useState(false);

  const handleViewDetails = () => {
    navigate(`${LIBRARY_PATH}/books/${book.id}`);
  };

  const handleEdit = () => {
    sessionStorage.setItem('booksPageState', window.location.search);
    navigate(`${LIBRARY_PATH}/books/edit/${book.id}`);
  };

  const handleDelete = async () => {
    try {
      const axios = (await import('axios')).default;
      await axios.delete(`${window.location.origin}/api/books/${book.id}`);
      setShowConfirm(false);
      if (onDelete) onDelete(book.id);
    } catch (err) {
      console.error('Error deleting book:', err);
    }
  };

  return (
    <>
    <div className="card">
      {book.thumb_image && (
        <img 
          src={`${MEDIA_BASE_URL}/${book.thumb_image}`}
          alt={book.title_cn || book.title} 
          className="card-image hvr-float-shadow" 
        />
      )}
      <h3 className="card-title">{book.title_cn || book.title}</h3>
      <p className="caption">{t('bookForm.isbn')}: {book.isbn}</p>
      <p className="caption">{t('bookForm.authors')}: {book.authors ? book.authors.join(', ') : t('common.unknown')}</p>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
        <button className="btn-pill-link" onClick={handleViewDetails}>{t('common.view')}</button>
        {isAuthenticated && (
          <>
            <button className="btn-pill-link" onClick={handleEdit}>{t('common.edit')}</button>
            <button className="btn-pill-link" onClick={() => setShowConfirm(true)} style={{ color: '#ff3b30' }}>{t('common.delete')}</button>
          </>
        )}
      </div>
    </div>

    {showConfirm && (
      <div style={{
        position: 'fixed', inset: 0, zIndex: 10001,
        background: 'rgba(0,0,0,0.4)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
      }} onClick={() => setShowConfirm(false)}>
        <div style={{
          background: '#fff',
          boxShadow: '0 25px 50px rgba(0,0,0,0.25)',
          borderRadius: 20, padding: '24px 28px',
          width: Math.min(380, window.innerWidth - 32),
          boxShadow: '0 25px 50px rgba(0,0,0,0.25)',
          textAlign: 'center',
        }} onClick={(e) => e.stopPropagation()}>
          <h3 style={{ margin: '0 0 8px', fontSize: 18 }}>
            {t('common.deleteConfirm')}
          </h3>
          <p style={{ color: '#86868b', margin: '0 0 20px', fontSize: 15 }}>
            {t('common.cannotUndo')}
          </p>
          <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
            <button className="btn-pill-link" onClick={() => setShowConfirm(false)}>{t('common.cancel')}</button>
            <button className="btn-pill-link" onClick={handleDelete} style={{ color: '#ff3b30' }}>{t('common.delete')}</button>
          </div>
        </div>
      </div>
    )}
    </>
  );
};
export default BookCard;
