import React, { useState, useEffect } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
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

  const [book, setBook] = useState(null);
  const [similarBooks, setSimilarBooks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

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
          <h2 className="bd-error-title">未找到该书籍</h2>
          <p className="bd-error-message">
            该书可能已被移除，或链接地址有误。
          </p>
          <button
            className="bd-back"
            onClick={() => navigate(`${LIBRARY_PATH}/books`)}
          >
            <span className="bd-back-arrow">←</span> 返回书库
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
          <span className="bd-back-arrow">←</span> 返回书库
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
                <span className="bd-meta-chip-value">{book.pages}</span> 页
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
                {book.read_state || '未设置'}
              </span>
            </div>
            {book.isbn && (
              <div className="bd-meta-chip">
                <span className="bd-meta-chip-icon">🔖</span>
                ISBN <span className="bd-meta-chip-value">{book.isbn}</span>
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
                暂无封面
              </div>
            )}
          </aside>

          {/* Info Cards */}
          <div className="bd-info">

            {/* Basic Info */}
            <InfoCard icon="📋" title="基本信息">
              <InfoItem label="原名" value={book.title} full />
              {book.title_cn && book.title !== book.title_cn && (
                <InfoItem label="中文名" value={book.title_cn} full />
              )}
              <InfoItem label="ISBN" value={book.isbn} />
              <InfoItem label="正文语言" value={book.language} />
              <InfoItem
                label="作者"
                value={
                  book.authors?.length > 0 ? (
                    <LinkList items={book.authors} basePath="authors" />
                  ) : null
                }
                full
              />
              <InfoItem label="译者/编辑" value={book.translator} full />
              <InfoItem
                label="出版社"
                value={<EntityLink entity={book.publisher} basePath="publishers" />}
              />
              <InfoItem label="出版日期" value={fmtDate(book.publish_date)} />
            </InfoCard>

            {/* Physical Details */}
            {(book.binding_type || book.paper_type || book.pages || book.book_count || book.compose_type) && (
              <InfoCard icon="📖" title="装帧信息">
                <InfoItem label="装帧" value={book.binding_type} />
                <InfoItem label="正文用纸" value={book.paper_type} />
                <InfoItem label="页数" value={book.pages} />
                <InfoItem label="册数" value={book.book_count} />
                <InfoItem label="排版" value={book.compose_type} />
              </InfoCard>
            )}

            {/* Purchase Info */}
            <InfoCard icon="🛒" title="购买信息">
              <InfoItem
                label="定价"
                value={book.price ? `¥ ${Number(book.price).toFixed(2)}` : null}
                className="price"
              />
              {book.price && book.purchase_price && book.price > 0 && (
                <InfoItem
                  label="折扣"
                  value={`${(book.purchase_price / book.price * 100).toFixed(1)}%`}
                  className={book.purchase_price / book.price < 0.5 ? 'highlight' : ''}
                />
              )}
              <InfoItem
                label="购入价"
                value={`¥ ${Number(book.purchase_price).toFixed(2)}`}
              />
              <InfoItem label="购入日期" value={fmtDate(book.purchase_date)} />
              <InfoItem label="来源" value={book.purchase_store} />
            </InfoCard>

            {/* Publishing Info */}
            {(book.brand || book.book_series || book.category || book.bookshelf || book.edition || book.printing_info) && (
              <InfoCard icon="🏷️" title="出版信息">
                <InfoItem
                  label="出品方"
                  value={<EntityLink entity={book.brand} basePath="brands" />}
                />
                <InfoItem
                  label="书系"
                  value={<EntityLink entity={book.book_series} basePath="series" />}
                />
                <InfoItem
                  label="分类"
                  value={
                    book.category ? (
                      <Link to={`${LIBRARY_PATH}/categories/${book.category.id}`}>
                        {book.category.path || book.category.name}
                      </Link>
                    ) : null
                  }
                />
                <InfoItem
                  label="所在书架"
                  value={<EntityLink entity={book.bookshelf} basePath="bookshelves" />}
                />
                <InfoItem label="版次" value={book.edition} />
                <InfoItem label="印次" value={book.printing_info} />
                <InfoItem label="印数" value={book.printed_number} />
              </InfoCard>
            )}

            {/* Status */}
            <InfoCard icon="📌" title="状态">
              <InfoItem
                label="阅读状态"
                value={
                  <span className="bd-info-value status">
                    <span className={`bd-status-dot ${readStateClass(book.read_state)}`} />
                    {book.read_state || '未设置'}
                  </span>
                }
              />
              <InfoItem
                label="已登记"
                value={book.registered ? '是' : '否'}
              />
              <InfoItem
                label="心愿单"
                value={book.in_wish ? '是' : '否'}
              />
            </InfoCard>

            {/* Tags */}
            {book.tags && book.tags.length > 0 && (
              <InfoCard icon="🔖" title="标签">
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
              <InfoCard icon="📝" title="内容">
                {book.introduction && (
                  <InfoItem label="内容简介" value={book.introduction} full />
                )}
                {book.summary && (
                  <InfoItem label="内容概述" value={book.summary} full />
                )}
                {book.catalog && (
                  <InfoItem
                    label="目录"
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
              <h2 className="bd-similar-title">相似书籍</h2>
              <span className="bd-similar-hint">基于共同标签推荐</span>
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
                      暂无封面
                    </div>
                  )}
                  <div className="bd-similar-card-body">
                    <h3 className="bd-similar-card-title">
                      {sb.title_cn || sb.title}
                    </h3>
                    <p className="bd-similar-card-author">
                      {sb.authors?.map((a) => a.name).join(', ') || '未知作者'}
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
