// src/components/RagSearch.js
import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { LIBRARY_PATH } from '../config';
import { API_BASE_URL } from './Config';
import './RagSearch.css';

const DEFAULT_QUERY = '';

function RagSearch() {
  const [query, setQuery] = useState(DEFAULT_QUERY);
  const [alpha, setAlpha] = useState(0.5);
  const [results, setResults] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [status, setStatus] = useState({ indexed_count: 0, total_books: 0, model_loaded: false });
  const [hasSearched, setHasSearched] = useState(false);

  const inputRef = useRef(null);

  // Fetch index status on mount
  useEffect(() => {
    fetch(`${API_BASE_URL}/rag/status`)
      .then((r) => r.json())
      .then(setStatus)
      .catch(() => {});
  }, []);

  const doSearch = useCallback(async (q, a) => {
    if (!q || !q.trim()) return;
    setLoading(true);
    setError(null);
    setHasSearched(true);
    try {
      const res = await fetch(`${API_BASE_URL}/rag/search`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          query: q.trim(),
          top_k: 20,
          alpha: a,
        }),
      });
      if (!res.ok) throw new Error(`Server error: ${res.status}`);
      const data = await res.json();
      setResults(data.results || []);
      setTotal(data.total || 0);
    } catch (err) {
      setError(err.message);
      setResults([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, []);

  const handleSubmit = (e) => {
    if (e) e.preventDefault();
    doSearch(query, alpha);
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') handleSubmit();
  };

  const formatScore = (score) => {
    if (score >= 0.995) return '99';
    return Math.round(score * 100).toString();
  };

  const indexedPercent = status.total_books > 0
    ? Math.round((status.indexed_count / status.total_books) * 100)
    : 0;

  return (
    <section className="section light ragsearch-section">
      <div className="ragsearch-container">
        {/* Header */}
        <div className="ragsearch-header">
          <span className="ragsearch-emoji">🔍</span>
          <h1 className="ragsearch-title">Semantic Search</h1>
        </div>
        <p className="ragsearch-subtitle">
          Search your library with AI — understands meaning, not just keywords
        </p>

        {/* Search box */}
        <form className="ragsearch-box" onSubmit={handleSubmit}>
          <div className="ragsearch-input-row">
            <input
              ref={inputRef}
              className="ragsearch-input"
              type="text"
              placeholder="Try: 'science fiction about AI', '中国古代哲学', 'Python programming'"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={handleKeyDown}
              autoFocus
            />
            <button className="ragsearch-btn" type="submit" disabled={loading || !query.trim()}>
              {loading ? 'Searching…' : 'Search'}
            </button>
          </div>

          {/* Alpha slider */}
          <div className="ragsearch-slider-row">
            <span className="ragsearch-slider-label">Keywords</span>
            <input
              className="ragsearch-slider"
              type="range"
              min="0"
              max="1"
              step="0.1"
              value={alpha}
              onChange={(e) => setAlpha(parseFloat(e.target.value))}
            />
            <span className="ragsearch-slider-label">Meaning</span>
            <span className="ragsearch-slider-value">{Math.round(alpha * 100)}%</span>
          </div>
        </form>

        {/* Status bar */}
        <div className="ragsearch-status">
          <span className={`ragsearch-status-dot${status.model_loaded ? '' : ' off'}`} />
          <span>
            {status.model_loaded
              ? `AI model active · ${status.indexed_count} / ${status.total_books} books indexed`
              : `AI model offline · ${status.indexed_count} / ${status.total_books} indexed (${indexedPercent}%)`}
          </span>
          <span className="ragsearch-model-badge">bge-small-zh-v1.5</span>
        </div>

        {/* Error */}
        {error && <div className="ragsearch-error">⚠ {error}</div>}

        {/* Loading */}
        {loading && (
          <div className="ragsearch-loading">
            <div className="ragsearch-spinner" />
            <span>Analyzing your query…</span>
          </div>
        )}

        {/* Results */}
        {!loading && hasSearched && results.length > 0 && (
          <>
            <div className="ragsearch-status" style={{ marginTop: 8 }}>
              <span>{total} result{total !== 1 ? 's' : ''} for "{query}"</span>
            </div>
            <div className="ragsearch-results">
              {results.map((r) => (
                <Link
                  key={r.book_id}
                  to={`${LIBRARY_PATH}/books/${r.book_id}`}
                  className="ragsearch-result-card"
                >
                  <div className="ragsearch-result-score">
                    {formatScore(r.score)}
                    <span className="ragsearch-result-score-label">%</span>
                  </div>
                  <div className="ragsearch-result-body">
                    <p className="ragsearch-result-title">
                      {[r.title_cn, r.title].filter(Boolean).join(' · ')}
                    </p>
                    <p className="ragsearch-result-authors">
                      {r.authors && r.authors.length > 0
                        ? r.authors.join(', ')
                        : 'Unknown author'}
                    </p>
                  </div>
                </Link>
              ))}
            </div>
          </>
        )}

        {/* Empty state */}
        {!loading && hasSearched && results.length === 0 && !error && (
          <div className="ragsearch-empty">
            <div className="ragsearch-empty-icon">🔮</div>
            <p className="ragsearch-empty-text">No matches found</p>
            <p className="ragsearch-empty-hint">
              Try different keywords, or adjust the Meaning slider
            </p>
          </div>
        )}

        {/* Initial state */}
        {!loading && !hasSearched && (
          <div className="ragsearch-empty">
            <div className="ragsearch-empty-icon">🧠</div>
            <p className="ragsearch-empty-text">Ask anything about your books</p>
            <p className="ragsearch-empty-hint">
              Type a query above and press Enter. Semantic search finds relevant
              books even when your words don't exactly match the title.
            </p>
          </div>
        )}
      </div>
    </section>
  );
}

export default RagSearch;
