import React, { useState, useEffect } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useAuth } from '../AuthContext';
import { useTranslation } from 'react-i18next';
import './Books.css';
import './BookDetails.css';
import { API_BASE_URL, MEDIA_BASE_URL } from './Config';
import { LIBRARY_PATH } from '../config';

/* ------------------------------------------------------------------ */
/*  Helper: render a list of links with separators                     */
/* ------------------------------------------------------------------ */
const LinkList = ({ items, basePath, labelKey = 'name', idKey = 'id' }) => {
  if (!items || items.length === 0) return <span className="bd-info-empty">—</span>;
  return (
    <>
      {items.map((item, i) => (
        <React.Fragment key={item?.[idKey]}>
          <Link to={`${LIBRARY_PATH}/${basePath}/${item?.[idKey]}`}>
            {item?.[labelKey]}
          </Link>
          {i < items.length - 1 && ', '}
        </React.Fragment>
      ))}
    </>
  );
};

/* ------------------------------------------------------------------ */
/*  Helper: single linked entity                                        */
/* ------------------------------------------------------------------ */
const EntityLink = ({ entity, basePath, labelKey = 'name' }) => {
  if (!entity) return <span className="bd-info-empty">—</span>;
  return (
    <Link to={`${LIBRARY_PATH}/${basePath}/${entity.id}`}>
      {entity[labelKey]}
    </Link>
  );
};

/* ------------------------------------------------------------------ */
/*  Helper: formatted date                                              */
/* ------------------------------------------------------------------ */
const fmtDate = (d) => {
  if (!d) return null;
  return new Date(d).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
};

/* ------------------------------------------------------------------ */
/*  Helper: read-state dot colour                                       */
/* ------------------------------------------------------------------ */
const readStateClass = (state) => {
  if (!state) return 'unread';
  const s = state.toLowerCase();
  if (s.includes('read') || s.includes('已读')) return 'read';
  if (s.includes('wish') || s.includes('想读')) return 'wish';
  return 'unread';
};

/* ------------------------------------------------------------------ */
/*  InfoCard — reusable card with icon and title                        */
/* ------------------------------------------------------------------ */
const InfoCard = ({ icon, title, children }) => (
  <div className="bd-info-card">
    <div className="bd-info-card-header">
      <span className="bd-info-card-icon">{icon}</span>
      <h2 className="bd-info-card-title">{title}</h2>
    </div>
    <div className="bd-info-grid">
      {children}
    </div>
  </div>
);

/* ------------------------------------------------------------------ */
/*  InfoItem — a single key-value row inside the grid                   */
/* ------------------------------------------------------------------ */
const InfoItem = ({ label, value, full = false, className = '' }) => {
  if (value === null || value === undefined || value === '') return null;
  return (
    <div className={`bd-info-item${full ? ' full' : ''}`}>
      <span className="bd-info-label">{label}</span>
      <span className={`bd-info-value ${className}`}>{value}</span>
    </div>
  );
};

/* ================================================================== */
/*  BookDetails                                                        */
/* ================================================================== */
const BookDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { isAuthenticated } = useAuth();

  const [book, setBook] = useState(null);
  const [similarBooks, setSimilarBooks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [movingToLibrary, setMovingToLibrary] = useState(false);
  const [moveError, setMoveError] = useState(false);

  useEffect(() => {
    let cancelled = false;

    const fetchBook = async () => {
      try {
        const res = await axios.get(
          `${window.location.origin}${API_BASE_URL}/books/${id}`
        );
        if (!cancelled) setBook(res.data);
      } catch (err) {
        console.error('Error fetching book:', err);
        if (!cancelled) setError(true);
      }
    };

    const fetchSimilar = async () => {
      try {
        const res = await axios.get(
          `${window.location.origin}${API_BASE_URL}/books/${id}/similar?limit=8`
        );
        if (!cancelled) setSimilarBooks(res.data.similar_books || []);
      } catch (err) {
        console.error('Error fetching similar books:', err);
      }
    };

    const load = async () => {
      if (!cancelled) setLoading(true);
      await fetchBook();
      if (!cancelled) setLoading(false);
      await fetchSimilar();
    };

    load();

    return () => { cancelled = true; };
  }, [id]);

  const handleMoveToLibrary = async () => {
    setMovingToLibrary(true);
    setMoveError(false);
    try {
      await axios.put(`${window.location.origin}${API_BASE_URL}/books/${id}`, { in_wish: false });
      setBook(prev => ({ ...prev, in_wish: false }));
    } catch (err) {
      console.error('Error moving book to library:', err);
      setMoveError(true);
    }
    setMovingToLibrary(false);
  };

  /* ---- Loading Skeleton ---- */
  if (loading) {
    return (
      <div className="bd-page">
        <div className="bd-container">
          <div className="bd-skeleton bd-skeleton-title" />
          <div className="bd-skeleton bd-skeleton-subtitle" />
          <div className="bd-skeleton-meta">
            <div className="bd-skeleton bd-skeleton-chip" />
            <div className="bd-skeleton bd-skeleton-chip" />
            <div className="bd-skeleton bd-skeleton-chip" />
          </div>
          <div className="bd-skeleton-main">
            <div className="bd-skeleton bd-skeleton-cover" />
            <div className="bd-skeleton-cards">
              <div className="bd-skeleton bd-skeleton-card" />
              <div className="bd-skeleton bd-skeleton-card" />
              <div className="bd-skeleton bd-skeleton-card" />
            </div>
          </div>
        </div>
      </div>
    );
  }

  /* ---- Error State ---- */
  if (error || !book) {
    return (
      <div className="bd-page">
        <div className="bd-container bd-error">
          <div className="bd-error-icon">📖</div>
          <h2 className="bd-error-title">{t('books.notFound')}</h2>
          <p className="bd-error-message">
            {t('books.notFoundDetail')}
          </p>
          <button
            className="bd-back"
            onClick={() => navigate(`${LIBRARY_PATH}/books`)}
          >
            <span className="bd-back-arrow">←</span> {t('books.backToList')}
          </button>
        </div>
      </div>
    );
  }

  /* ---- Normal Render ---- */
  const title = book.title_cn || book.title;

  return (
    <div className="bd-page">
      <div className="bd-container">

        {/* Back */}
        <button
          className="bd-back"
          onClick={() => navigate(`${LIBRARY_PATH}/books`)}
        >
          <span className="bd-back-arrow">←</span> {t('books.backToList')}
        </button>

        {/* ── Hero ── */}
        <header className="bd-hero">
          <h1 className="bd-title">{title}</h1>

          {/* Subtitle: authors + publisher */}
          <div className="bd-subtitle">
            {book.authors && book.authors.length > 0 && (
              <>
                <LinkList items={book.authors} basePath="authors" />
              </>
            )}
            {book.publisher && (
              <>
                {book.authors?.length > 0 && (
                  <span className="bd-subtitle-sep">·</span>
                )}
                <EntityLink entity={book.publisher} basePath="publishers" />
              </>
            )}
          </div>
          {isAuthenticated && book.in_wish && (
            <div style={{ marginTop: '12px', marginBottom: '4px' }}>
              <button
                className="btn-primary-blue"
                onClick={handleMoveToLibrary}
                disabled={movingToLibrary}
              >
                {movingToLibrary ? '…' : t('bookDetails.moveToLibrary')}
              </button>
              {moveError && (
                <span style={{ marginLeft: 12, fontSize: 14, color: '#ff3b30', verticalAlign: 'middle' }}>
                  {t('common.error')}
                </span>
              )}
            </div>
          )}

          {/* Meta Chips */}
          <div className="bd-meta-bar">
            {book.douban_score && (
              <div className="bd-meta-chip">
                <span className="bd-meta-chip-icon">⭐</span>
                豆瓣 <span className="bd-meta-chip-value">{book.douban_score}</span>
              </div>
            )}
            {book.pages && (
              <div className="bd-meta-chip">
                <span className="bd-meta-chip-icon">📄</span>
                <span className="bd-meta-chip-value">{book.pages}</span> {t('bookForm.pages')}
              </div>
            )}
            {(book.price || book.purchase_price) && (
              <div className="bd-meta-chip">
                <span className="bd-meta-chip-icon">💰</span>
                ¥<span className="bd-meta-chip-value">
                  {Number(book.purchase_price || book.price).toFixed(2)}
                </span>
              </div>
            )}
            <div className="bd-meta-chip">
              <span className="bd-meta-chip-icon">📚</span>
              <span className="bd-info-value status">
                <span className={`bd-status-dot ${readStateClass(book.read_state)}`} />
                {book.read_state || t('common.none')}
              </span>
            </div>
            {book.isbn && (
              <div className="bd-meta-chip">
                <span className="bd-meta-chip-icon">🔖</span>
                {t('bookForm.isbn')} <span className="bd-meta-chip-value">{book.isbn}</span>
              </div>
            )}
          </div>
        </header>

        {/* ── Main: Cover + Info Cards ── */}
        <div className="bd-main">
          {/* Cover */}
          <aside className="bd-cover">
            {book.thumb_image ? (
              <div className="bd-cover-card">
                <img
                  className="bd-cover-img"
                  src={`${MEDIA_BASE_URL}/${book.thumb_image}`}
                  alt={title}
                />
              </div>
            ) : (
              <div className="bd-cover-card" style={{ aspectRatio: '3/4', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--bd-text-tertiary)', fontSize: '14px' }}>
                {t('bookDetails.noCover')}
              </div>
            )}
          </aside>

          {/* Info Cards */}
          <div className="bd-info">

            {/* Basic Info */}
            <InfoCard icon="📋" title={t('bookDetails.basicInfo')}>
              <InfoItem label={t('bookForm.titleEn')} value={book.title} full />
              {book.title_cn && book.title !== book.title_cn && (
                <InfoItem label={t('bookForm.titleCn')} value={book.title_cn} full />
              )}
              <InfoItem label={t('bookForm.isbn')} value={book.isbn} />
              <InfoItem label={t('bookForm.language')} value={book.language} />
              <InfoItem
                label={t('bookForm.authors')}
                value={
                  book.authors?.length > 0 ? (
                    <LinkList items={book.authors} basePath="authors" />
                  ) : null
                }
                full
              />
              <InfoItem label={t('bookForm.translator')} value={book.translator} full />
              <InfoItem
                label={t('bookForm.publisher')}
                value={<EntityLink entity={book.publisher} basePath="publishers" />}
              />
              <InfoItem label={t('bookForm.publishDate')} value={fmtDate(book.publish_date)} />
            </InfoCard>

            {/* Physical Details */}
            {(book.binding_type || book.paper_type || book.pages || book.book_count || book.compose_type) && (
              <InfoCard icon="📖" title={t('bookDetails.bindingInfo')}>
                <InfoItem label={t('bookForm.binding')} value={book.binding_type} />
                <InfoItem label={t('bookForm.paper')} value={book.paper_type} />
                <InfoItem label={t('bookForm.pages')} value={book.pages} />
                <InfoItem label={t('bookForm.bookCount')} value={book.book_count} />
                <InfoItem label={t('bookForm.compose')} value={book.compose_type} />
              </InfoCard>
            )}

            {/* Purchase Info */}
            <InfoCard icon="🛒" title={t('bookDetails.purchaseInfo')}>
              <InfoItem
                label={t('bookForm.price')}
                value={book.price ? `¥ ${Number(book.price).toFixed(2)}` : null}
                className="price"
              />
              {book.price && book.purchase_price && book.price > 0 && (
                <InfoItem
                  label={t('bookForm.discount')}
                  value={`${(book.purchase_price / book.price * 100).toFixed(1)}%`}
                  className={book.purchase_price / book.price < 0.5 ? 'highlight' : ''}
                />
              )}
              <InfoItem
                label={t('bookForm.purchasePrice')}
                value={`¥ ${Number(book.purchase_price).toFixed(2)}`}
              />
              <InfoItem label={t('bookForm.purchaseDate')} value={fmtDate(book.purchase_date)} />
              <InfoItem label={t('bookForm.purchaseStore')} value={book.purchase_store} />
            </InfoCard>

            {/* Publishing Info */}
            {(book.brand || book.book_series || book.category || book.bookshelf || book.edition || book.printing_info) && (
              <InfoCard icon="🏷️" title={t('bookDetails.publishingInfo')}>
                <InfoItem
                  label={t('bookForm.brand')}
                  value={<EntityLink entity={book.brand} basePath="brands" />}
                />
                <InfoItem
                  label={t('bookForm.series')}
                  value={<EntityLink entity={book.book_series} basePath="series" />}
                />
                <InfoItem
                  label={t('bookForm.category')}
                  value={
                    book.category ? (
                      <Link to={`${LIBRARY_PATH}/categories/${book.category.id}`}>
                        {book.category.path || book.category.name}
                      </Link>
                    ) : null
                  }
                />
                <InfoItem
                  label={t('bookForm.bookshelf')}
                  value={<EntityLink entity={book.bookshelf} basePath="bookshelves" />}
                />
                <InfoItem label={t('bookForm.edition')} value={book.edition} />
                <InfoItem label={t('bookForm.printing')} value={book.printing_info} />
                <InfoItem label={t('bookForm.printedNumber')} value={book.printed_number} />
              </InfoCard>
            )}

            {/* Status */}
            <InfoCard icon="📌" title={t('bookDetails.status')}>
              <InfoItem
                label={t('bookForm.readState')}
                value={
                  <span className="bd-info-value status">
                    <span className={`bd-status-dot ${readStateClass(book.read_state)}`} />
                    {book.read_state || t('common.none')}
                  </span>
                }
              />
              <InfoItem
                label={t('bookForm.registered')}
                value={book.registered ? t('bookDetails.yes') : t('bookDetails.no')}
              />
              <InfoItem
                label={t('bookForm.wishlist')}
                value={book.in_wish ? t('bookDetails.yes') : t('bookDetails.no')}
              />
            </InfoCard>

            {/* Tags */}
            {book.tags && book.tags.length > 0 && (
              <InfoCard icon="🔖" title={t('bookForm.tags')}>
                <div className="bd-tags" style={{ gridColumn: '1 / -1' }}>
                  {book.tags.map((tag) => (
                    <Link
                      key={tag}
                      to={`${LIBRARY_PATH}/books?tag=${encodeURIComponent(tag)}`}
                      className="bd-tag"
                    >
                      {tag}
                    </Link>
                  ))}
                </div>
              </InfoCard>
            )}

            {/* Content */}
            {(book.introduction || book.summary || book.catalog) && (
              <InfoCard icon="📝" title={t('bookDetails.content')}>
                {book.introduction && (
                  <InfoItem label={t('bookDetails.introduction')} value={book.introduction} full />
                )}
                {book.summary && (
                  <InfoItem label={t('bookDetails.summary')} value={book.summary} full />
                )}
                {book.catalog && (
                  <InfoItem
                    label={t('bookDetails.catalog')}
                    value={
                      <div className="bd-content-block catalog">
                        {book.catalog}
                      </div>
                    }
                    full
                  />
                )}
              </InfoCard>
            )}

          </div>
        </div>

        {/* ── Similar Books ── */}
        {similarBooks.length > 0 && (
          <section className="bd-similar">
            <div className="bd-similar-header">
              <h2 className="bd-similar-title">{t('bookDetails.similarBooks')}</h2>
              <span className="bd-similar-hint">{t('bookDetails.similarHint')}</span>
            </div>
            <div className="bd-similar-scroll">
              {similarBooks.map((sb) => (
                <div
                  key={sb.id}
                  className="bd-similar-card"
                  onClick={() => navigate(`${LIBRARY_PATH}/books/${sb.id}`)}
                >
                  {sb.thumb_image ? (
                    <img
                      className="bd-similar-card-img"
                      src={`${MEDIA_BASE_URL}/${sb.thumb_image}`}
                      alt={sb.title_cn || sb.title}
                      loading="lazy"
                    />
                  ) : (
                    <div
                      className="bd-similar-card-img"
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        background: 'rgba(0,0,0,0.03)',
                        color: 'var(--bd-text-tertiary)',
                        fontSize: '13px',
                      }}
                    >
                      {t('bookDetails.noCover')}
                    </div>
                  )}
                  <div className="bd-similar-card-body">
                    <h3 className="bd-similar-card-title">
                      {sb.title_cn || sb.title}
                    </h3>
                    <p className="bd-similar-card-author">
                      {sb.authors?.map((a) => a.name).join(', ') || t('bookDetails.unknownAuthor')}
                    </p>
                    {sb.shared_tags?.length > 0 && (
                      <div className="bd-similar-card-tags">
                        {sb.shared_tags.slice(0, 3).map((t) => (
                          <span key={t} className="bd-similar-card-tag">{t}</span>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </section>
        )}
      </div>
    </div>
  );
};

export default BookDetails;
