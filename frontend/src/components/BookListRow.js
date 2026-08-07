import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../AuthContext';
import { MEDIA_BASE_URL } from './Config';
import { LIBRARY_PATH } from '../config';

/**
 * Table row for a book in list view (PageLayout `renderItem`).
 * Mirrors the BookCard action semantics: edit/delete shown when
 * authenticated and allowed by `protectLevel`.
 */
const BookListRow = ({
  book,
  onDeleted,
  protectLevel = 0,
  showCheckbox = false,
  checked = false,
  onCheckChange,
}) => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { isAuthenticated } = useAuth();
  const [showConfirm, setShowConfirm] = useState(false);

  const handleView = () => navigate(`${LIBRARY_PATH}/books/${book.id}`);

  const handleEdit = (e) => {
    e.stopPropagation();
    sessionStorage.setItem('booksPageState', window.location.search);
    navigate(`${LIBRARY_PATH}/books/edit/${book.id}`);
  };

  const handleDelete = async (e) => {
    e.stopPropagation();
    try {
      const axios = (await import('axios')).default;
      await axios.delete(`${window.location.origin}/api/books/${book.id}`);
      setShowConfirm(false);
      if (onDeleted) onDeleted(book.id);
    } catch (err) {
      console.error('Error deleting book:', err);
    }
  };

  return (
    <>
      <tr onClick={handleView}>
        {showCheckbox && (
          <td style={{ width: 40, textAlign: 'center' }}>
            <input
              type="checkbox"
              checked={checked}
              onChange={(e) => onCheckChange && onCheckChange(book.id, e.target.checked)}
              onClick={(e) => e.stopPropagation()}
            />
          </td>
        )}
        <td style={{ width: 48, padding: '6px 8px' }}>
          {book.thumb_image ? (
            <img src={`${MEDIA_BASE_URL}/${book.thumb_image}`} alt="" style={{ width: 40, height: 52, borderRadius: 3, objectFit: 'cover', display: 'block' }} />
          ) : (
            <div style={{ width: 40, height: 52, borderRadius: 3, background: '#e5e5ea', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16, color: '#86868b' }}>
              {book.title?.[0] || 'B'}
            </div>
          )}
        </td>
        <td className="list-cell-secondary" style={{ width: 130, fontSize: 12, fontFamily: 'monospace' }}>{book.isbn || ''}</td>
        <td className="list-cell-primary">{book.title}</td>
        <td className="list-cell-secondary">
          {book.authors?.join(', ') || ''}
        </td>
        <td className="list-cell-secondary">
          {book.publisher?.name || ''}
        </td>
        <td className="list-cell-secondary">
          {book.category?.name || ''}
        </td>
        {isAuthenticated && protectLevel < 2 && (
          <td style={{ width: 80, textAlign: 'right' }}>
            <button className="btn-pill-link" onClick={handleEdit}
              style={{ fontSize: 12, padding: '4px 8px' }}>
              {t('common.edit')}
            </button>
            {protectLevel < 1 && (
              <button className="btn-pill-link" onClick={(e) => { e.stopPropagation(); setShowConfirm(true); }}
                style={{ fontSize: 12, padding: '4px 8px', color: '#ff3b30' }}>
                {t('common.delete')}
              </button>
            )}
          </td>
        )}
      </tr>

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

export default BookListRow;
