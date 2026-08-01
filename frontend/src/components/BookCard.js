import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../AuthContext';
import { MEDIA_BASE_URL } from './Config';
import { LIBRARY_PATH } from '../config';

const BookCard = ({ book, onDelete, protectLevel = 0, showCheckbox = false, checked = false, onCheckChange, compact = false }) => {
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

  // ── Compact mode (>3 cols): hover for menu, click cover for detail ──
  if (compact) {
    return (
      <>
      <div className="card card-compact">
        <div className="card-compact-cover" onClick={handleViewDetails}>
          {book.thumb_image ? (
            <img
              src={`${MEDIA_BASE_URL}/${book.thumb_image}`}
              alt={book.title_cn || book.title}
              className="card-compact-img hvr-float-shadow"
            />
          ) : (
            <div className="card-compact-placeholder hvr-float-shadow">
              <span>{(book.title_cn || book.title)?.[0] || 'B'}</span>
            </div>
          )}
        </div>

        {/* Title overlay — covers entire card */}
        <div className="card-compact-title-overlay">
          <span>{(book.title_cn || book.title) || ''}</span>
        </div>

        {/* Action menu — only if there are actions */}
        {isAuthenticated && (protectLevel < 2 || protectLevel < 1) && (
          <div className="card-compact-menu" onClick={(e) => e.stopPropagation()}>
            {protectLevel < 2 && (
              <button className="btn-pill-link" onClick={handleEdit}>
                {t('common.edit')}
              </button>
            )}
            {protectLevel < 1 && (
              <button className="btn-pill-link" onClick={() => setShowConfirm(true)}
                style={{ color: '#ff3b30' }}>
                {t('common.delete')}
              </button>
            )}
          </div>
        )}
      </div>

      {showConfirm && (
        <div style={{
          position: 'fixed', inset: 0, zIndex: 10001,
          background: 'rgba(0,0,0,0.4)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
        }} onClick={() => setShowConfirm(false)}>
          <div style={{
            background: '#fff',
            borderRadius: 20, padding: '24px 28px',
            width: Math.min(380, window.innerWidth - 32),
            boxShadow: '0 25px 50px rgba(0,0,0,0.25)',
            textAlign: 'center',
          }} onClick={(e) => e.stopPropagation()}>
            <h3 style={{ margin: '0 0 8px', fontSize: 18 }}>{t('common.deleteConfirm')}</h3>
            <p style={{ color: '#86868b', margin: '0 0 20px', fontSize: 15 }}>{t('common.cannotUndo')}</p>
            <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
              <button className="btn-pill-link" onClick={() => setShowConfirm(false)}>{t('common.cancel')}</button>
              <button className="btn-pill-link" onClick={handleDelete} style={{ color: '#ff3b30' }}>{t('common.delete')}</button>
            </div>
          </div>
        </div>
      )}
      </>
    );
  }

  // ── Normal card ──
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
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, justifyContent: 'center' }}>
        <button className="btn-pill-link" onClick={handleViewDetails}>{t('common.view')}</button>
        {isAuthenticated && protectLevel < 2 && (
          <button className="btn-pill-link" onClick={handleEdit}>{t('common.edit')}</button>
        )}
        {isAuthenticated && protectLevel < 1 && (
          <button className="btn-pill-link" onClick={() => setShowConfirm(true)} style={{ color: '#ff3b30' }}>{t('common.delete')}</button>
        )}
        {showCheckbox && (
          <label style={{ display: 'flex', alignItems: 'center', gap: 4, marginLeft: 'auto', cursor: 'pointer', fontSize: 13, color: '#86868b' }}>
            <input
              type="checkbox"
              checked={checked}
              onChange={(e) => onCheckChange && onCheckChange(book.id, e.target.checked)}
            />
            {t('common.select')}
          </label>
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
