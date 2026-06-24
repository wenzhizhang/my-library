import React, { useState, useEffect, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import axios from "axios";
import { useTranslation } from 'react-i18next';
import "./Books.css";
import BookCard from './BookCard';
import { API_BASE_URL } from './Config';
import SearchableSelect from './SearchableSelect';

const ReadingPlanDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [plan, setPlan] = useState(null);
  const [loading, setLoading] = useState(true);
  const [allBooks, setAllBooks] = useState([]);
  const [pendingBooks, setPendingBooks] = useState([]);
  const pendingIdsRef = useRef(new Set());
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState("");
  const [removingSelected, setRemovingSelected] = useState(false);
  const [manageMode, setManageMode] = useState(false);
  const [selectedIds, setSelectedIds] = useState(new Set());

  useEffect(() => {
    fetchPlan();
  }, [id]);

  useEffect(() => {
    let cancelled = false;
    const loadBooks = async () => {
      try {
        const response = await axios.get(
          `${window.location.origin}${API_BASE_URL}/books/titles`
        );
        if (cancelled) return;
        const allTitles = response.data || [];
        const existingIds = new Set([
          ...(plan?.books || []).map(b => b.id),
          ...pendingIdsRef.current,
        ]);
        if (cancelled) return;
        setAllBooks(allTitles.filter(b => !existingIds.has(b.id)));
      } catch (err) {
        if (!cancelled) {
          console.error('Error fetching book titles:', err);
          setAllBooks([]);
        }
      }
    };
    if (plan) loadBooks();
    return () => { cancelled = true; };
  }, [plan?.id, plan?.books?.length]);

  useEffect(() => {
    pendingIdsRef.current = new Set(pendingBooks.map(b => b.id));
  }, [pendingBooks]);

  const fetchPlan = async () => {
    setLoading(true);
    try {
      const response = await axios.get(
        `${window.location.origin}${API_BASE_URL}/reading-plans/${id}`
      );
      setPlan(response.data);
    } catch (err) {
      console.error("Error fetching reading plan:", err);
      setPlan(null);
    }
    setLoading(false);
  };

  const handleSelectBook = (bookId) => {
    if (bookId == null) return;
    const book = allBooks.find(b => b.id === bookId);
    if (!book) return;
    setPendingBooks(prev => [...prev, book]);
    setAllBooks(prev => prev.filter(b => b.id !== bookId));
  };

  const handleRemovePending = (bookId) => {
    const removed = pendingBooks.find(b => b.id === bookId);
    setPendingBooks(prev => prev.filter(b => b.id !== bookId));
    if (removed) {
      setAllBooks(prev => [...prev, removed].sort((a, b) => a.name.localeCompare(b.name)));
    }
  };

  const handleAddAll = async () => {
    if (pendingBooks.length === 0) return;
    setAdding(true);
    setError("");
    try {
      const response = await axios.post(
        `${window.location.origin}${API_BASE_URL}/reading-plans/${id}/books/batch`,
        { book_ids: pendingBooks.map(b => b.id) }
      );
      setPlan(response.data);
      setPendingBooks([]);
    } catch (err) {
      const msg = err.response?.data?.detail || "Failed to add books";
      setError(msg);
    }
    setAdding(false);
  };

  const handleCheckChange = (bookId, checked) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (checked) next.add(bookId);
      else next.delete(bookId);
      return next;
    });
  };

  const handleRemoveSelected = async () => {
    if (selectedIds.size === 0 || removingSelected) return;
    setRemovingSelected(true);
    setError("");
    const ids = [...selectedIds];
    let firstError = null;
    for (const bookId of ids) {
      try {
        const response = await axios.delete(
          `${window.location.origin}${API_BASE_URL}/reading-plans/${id}/books/${bookId}`
        );
        setPlan(response.data);
      } catch (err) {
        if (!firstError) {
          firstError = err.response?.data?.detail || "Failed to remove book";
        }
      }
    }
    setSelectedIds(new Set());
    setRemovingSelected(false);
    if (firstError) setError(firstError);
  };


  if (loading) {
    return <div className="loading">Loading...</div>;
  }

  if (!plan) {
    return <div className="error">Reading Plan not found</div>;
  }

  const chipStyle = {
    display: "inline-flex",
    alignItems: "center",
    gap: "4px",
    padding: "4px 10px",
    background: "#0071e3",
    color: "#fff",
    borderRadius: "980px",
    fontSize: "14px",
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
  };

  const chipRemoveStyle = {
    cursor: "pointer",
    fontSize: "14px",
    lineHeight: 1,
    marginLeft: "2px",
    opacity: 0.7,
  };

  return (
    <section className="section light">
      <div className="container">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <h1 className="section-heading">{plan.name}</h1>
          <div style={{ display: 'flex', gap: 8 }}>
            <button
              className="btn-pill-link"
              onClick={() => navigate('/my-library/reading-plans')}
            >
              {t('readingPlans.backToList')}
            </button>
            {(plan.books && plan.books.length > 0) && (
              <button
                className="btn-pill-link"
                onClick={() => { setManageMode(!manageMode); setSelectedIds(new Set()); }}
              >
                {manageMode ? t('readingPlans.done') : t('readingPlans.manage')}
              </button>
            )}
          </div>
        </div>

        <div className="details-content">
          <h2>{t('bookDetails.basicInfo')}</h2>
          <p>
            <strong>{t('common.name')}:</strong> {plan.name}
          </p>
          {plan.intro && (
            <p>
              <strong>{t('common.introduction')}:</strong> {plan.intro}
            </p>
          )}
          {(plan.start_date || plan.end_date) && (
            <p>
              <strong>{t('readingPlans.period')}:</strong> {plan.start_date || '—'} — {plan.end_date || '—'}
            </p>
          )}
          {plan.total_books !== undefined && (
            <p>
              <strong>{t('readingPlans.books')}:</strong> {plan.total_books}
            </p>
          )}
          {plan.progress !== undefined && plan.progress !== null && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 4 }}>
              <strong style={{ flexShrink: 0 }}>{t('readingPlans.progress')}:</strong>
              <div style={{
                flex: 1, height: 8, borderRadius: 4, background: '#e0e0e0',
                overflow: 'hidden', maxWidth: 300,
              }}>
                <div style={{
                  height: '100%', width: `${Math.min(plan.progress, 100)}%`,
                  background: '#34c759', borderRadius: 4,
                  transition: 'width 0.3s ease',
                }} />
              </div>
              <span style={{ fontSize: 14, fontWeight: 600, flexShrink: 0 }}>{plan.progress}%</span>
            </div>
          )}
        </div>

        <div className="details-content" style={{ marginTop: "1rem" }}>
          <h2>{t('readingPlans.addBooks')}</h2>
          {error && <p style={{ color: "#ff3b30" }}>{error}</p>}

          <div style={{ display: "flex", gap: "0.5rem", alignItems: "flex-end" }}>
            <div style={{ flex: 1 }}>
              <SearchableSelect
                label={t('readingPlans.searchBook')}
                value={null}
                onChange={handleSelectBook}
                options={allBooks}
                placeholder={t('readingPlans.searchPlaceholder')}
                keepSearchOnSelect={true}
              />
            </div>
            <button
              className="btn-primary-blue"
              onClick={handleAddAll}
              disabled={adding || pendingBooks.length === 0}
              style={{ marginBottom: "24px" }}
            >
              {adding ? t('readingPlans.adding') : t('readingPlans.addAll', { count: pendingBooks.length })}
            </button>
          </div>

          {pendingBooks.length > 0 && (
            <div style={{ display: "flex", flexWrap: "wrap", gap: "6px", marginBottom: "16px" }}>
              {pendingBooks.map(book => (
                <span key={book.id} style={chipStyle}>
                  {book.name}
                  <span
                    style={chipRemoveStyle}
                    onClick={() => handleRemovePending(book.id)}
                    title={t('readingPlans.remove')}
                  >
                    ×
                  </span>
                </span>
              ))}
            </div>
          )}
        </div>

        <div style={{ marginTop: "2rem" }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h2>{t('readingPlans.booksInPlan')}</h2>
            {manageMode && selectedIds.size > 0 && (
              <button
                className="btn-pill-link"
                onClick={handleRemoveSelected}
                disabled={removingSelected}
                style={{ color: '#ff3b30', opacity: removingSelected ? 0.6 : 1 }}
              >
                {removingSelected ? t('readingPlans.removing') : `${t('readingPlans.removeSelected')} (${selectedIds.size})`}
              </button>
            )}
          </div>
          <div className="grid">
            {plan.books && plan.books.length > 0 ? (
              plan.books.map((book) => (
                <BookCard
                  key={book.id}
                  book={book}
                  protectLevel={1}
                  showCheckbox={manageMode}
                  checked={selectedIds.has(book.id)}
                  onCheckChange={handleCheckChange}
                />
              ))
            ) : (
              <p>{t('readingPlans.noBooks')}</p>
            )}
          </div>
        </div>
      </div>
    </section>
  );
};

export default ReadingPlanDetails;
