import React, { useState, useCallback, useMemo, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import './PageLayout.css';

const STORAGE_PREFIX = 'page_layout_';
const DEFAULTS = { columns: 'auto', viewMode: 'grid' };
const COLUMN_OPTIONS = [
  { value: 'auto', label: 'Auto' },
  { value: '2', label: '2 cols' },
  { value: '3', label: '3 cols' },
  { value: '4', label: '4 cols' },
  { value: '5', label: '5 cols' },
];

function loadPrefs(key) {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + key);
    if (raw) return { ...DEFAULTS, ...JSON.parse(raw) };
  } catch { /* ignore */ }
  return { ...DEFAULTS };
}

function savePrefs(key, prefs) {
  try {
    localStorage.setItem(STORAGE_PREFIX + key, JSON.stringify(prefs));
  } catch { /* ignore */ }
}

export default function PageLayout({
  embedded = false,
  title,
  createButton,
  searchValue,
  onSearchChange,
  onSearch,
  searchPlaceholder,
  sortBy,
  sortOptions = [],
  onSort,
  limit,
  limitOptions = [10, 20, 50, 100],
  onLimitChange,
  page,
  totalPages,
  totalItems,
  onPageChange,
  layoutKey = 'default',
  extraToolbar,
  extraSearchSlot,
  items = [],
  renderItem,
  listColumns,
}) {
  const { t } = useTranslation();
  const [prefs, setPrefs] = useState(() => loadPrefs(layoutKey));
  const [goToPage, setGoToPage] = useState('');

  // Reset goToPage when page changes externally
  useEffect(() => { setGoToPage(''); }, [page]);

  const updatePrefs = useCallback((patch) => {
    setPrefs((prev) => {
      const next = { ...prev, ...patch };
      savePrefs(layoutKey, next);
      return next;
    });
  }, [layoutKey]);


  const viewMode = prefs.viewMode || 'grid';
  const columns = prefs.columns || 'auto';

  const gridClass = columns === 'auto'
    ? 'page-layout-grid-auto'
    : `page-layout-grid page-layout-grid-cols-${columns}`;

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && onSearch) onSearch();
  };

  const handleGoToPage = () => {
    const n = Math.min(Math.max(parseInt(goToPage) || 1, 1), totalPages);
    onPageChange(n);
    setGoToPage('');
  };

  const pages = useMemo(() => {
    const result = [];
    const s = Math.max(1, page - 2);
    const e = Math.min(totalPages, page + 2);
    for (let i = s; i <= e; i++) result.push(i);
    return result;
  }, [page, totalPages]);

  const layout = (
    <>
      {!embedded && (
        <div className="page-layout-header">
          <h1 className="section-heading">{title}</h1>
          {createButton}
        </div>
      )}

      {/* Toolbar */}
        <div className="toolbar">
          {/* Search */}
          {onSearch && (
            <div className="toolbar-search">
              <div className="toolbar-search-row">
                <input
                  className="toolbar-search-input"
                  placeholder={searchPlaceholder || t('common.searchPlaceholder')}
                  value={searchValue}
                  onChange={(e) => onSearchChange(e.target.value)}
                  onKeyDown={handleKeyDown}
                />
                <button className="btn-pill-link" onClick={onSearch}>
                  {t('common.search')}
                </button>
                {extraSearchSlot}
              </div>
            </div>
          )}

          {/* Sort & limit & layout controls */}
          <div className="toolbar-actions">
            {sortOptions.length > 0 && (
              <label className="control-label">
                <span className="control-label-text">{t('common.sort')}</span>
                <select value={sortBy} onChange={(e) => onSort(e.target.value)}>
                  {sortOptions.map((o) => (
                    <option key={o.value} value={o.value}>{o.label}</option>
                  ))}
                </select>
              </label>
            )}
            {onLimitChange && (
              <label className="control-label">
                <span className="control-label-text">{t('common.perPage')}</span>
                <select
                  value={limit}
                  onChange={(e) => {
                    const v = parseInt(e.target.value);
                    onLimitChange(v);
                  }}
                >
                  {limitOptions.map((n) => (
                    <option key={n} value={n}>{n}</option>
                  ))}
                </select>
              </label>
            )}

            {/* Column selector */}
            <label className="control-label">
              <span className="control-label-text">Cols</span>
              <select
                value={columns}
                onChange={(e) => updatePrefs({ columns: e.target.value })}
              >
                {COLUMN_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </label>

            {/* View mode toggle */}
            <button
              className={`btn-pill-link view-toggle ${viewMode === 'list' ? 'active' : ''}`}
              onClick={() => updatePrefs({ viewMode: viewMode === 'grid' ? 'list' : 'grid' })}
              title={viewMode === 'grid' ? 'Switch to list view' : 'Switch to grid view'}
            >
              {viewMode === 'grid' ? '☰' : '▦'}
            </button>
          </div>

          {/* Page info */}
          <div className="toolbar-info">
            {t('common.page')} {page} {t('common.of')} {totalPages} {t('common.total')}({totalItems})
          </div>
        </div>

        {/* Extra toolbar slot (e.g. advanced filters, tag badges) */}
        {extraToolbar}

        {/* Items */}
        {viewMode === 'list' && listColumns ? (
          <>
            {items.length === 0 ? (
              <div className="empty-state">No items found.</div>
            ) : (
              <table className="list-table">
                <thead>
                  <tr>
                    {listColumns.map((col, i) => (
                      <th key={i}>{col}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {items.map((item) => renderItem(item, 'list', columns))}
                </tbody>
              </table>
            )}
          </>
        ) : (
          <div className={gridClass}>
            {items.length === 0 ? (
              <div className="empty-state">No items found.</div>
            ) : (
              items.map((item) => renderItem(item, 'grid', columns))
            )}
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
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
              {pages.map((i) => (
                <button
                  key={i}
                  className={`btn-pill-link ${i === page ? 'active' : ''}`}
                  onClick={() => onPageChange(i)}
                >
                  {i}
                </button>
              ))}
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
                onKeyDown={(e) => { if (e.key === 'Enter') handleGoToPage(); }}
              />
              <button className="btn-pill-link" onClick={handleGoToPage}>
                {t('common.goToPage')}
              </button>
            </div>
          </div>
        )}
    </>
  );

  if (embedded) return layout;
  return (
    <section className="section light">
      <div className="container">{layout}</div>
    </section>
  );
}
