import React from 'react';
import { useTranslation } from 'react-i18next';

const PaginationBar = ({ page, totalPages, goToPage, setGoToPage, onPageChange }) => {
  const { t } = useTranslation();

  const pages = [];
  const startPage = Math.max(1, page - 2);
  const endPage = Math.min(totalPages, page + 2);
  for (let i = startPage; i <= endPage; i++) {
    pages.push(
      <button
        key={i}
        className={`btn-pill-link ${i === page ? 'active' : ''}`}
        onClick={() => onPageChange(i)}
      >
        {i}
      </button>
    );
  }

  const handleGoToPage = () => {
    const p = parseInt(goToPage, 10);
    if (p >= 1 && p <= totalPages) {
      onPageChange(p);
      setGoToPage('');
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') handleGoToPage();
  };

  return (
    <div className="pagination">
      <div className="pagination-links">
        {page > 1 && (
          <>
            <button className="btn-pill-link" onClick={() => onPageChange(1)}>
              {t('common.first')}
            </button>
            <button className="btn-pill-link" onClick={() => onPageChange(page - 1)}>
              {t('common.previous')}
            </button>
          </>
        )}
        {pages}
        {page < totalPages && (
          <>
            <button className="btn-pill-link" onClick={() => onPageChange(page + 1)}>
              {t('common.next')}
            </button>
            <button className="btn-pill-link" onClick={() => onPageChange(totalPages)}>
              {t('common.last')}
            </button>
          </>
        )}
      </div>
      <div className="pagination-input">
        <input
          type="number"
          min="1"
          max={totalPages}
          value={goToPage}
          onChange={(e) => setGoToPage(e.target.value)}
          onKeyDown={handleKeyDown}
        />
        <button className="btn-pill-link" onClick={handleGoToPage}>
          {t('common.goToPage')}
        </button>
      </div>
    </div>
  );
};

export default PaginationBar;
