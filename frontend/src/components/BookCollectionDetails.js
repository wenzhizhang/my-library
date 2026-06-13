import React, { useState, useEffect, useRef } from "react";
import { useParams } from "react-router-dom";
import axios from "axios";
import "./Books.css";
import BookCard from './BookCard';
import { API_BASE_URL } from './Config';
import SearchableSelect from './SearchableSelect';

const BookCollectionDetails = () => {
  const { id } = useParams();
  const [collection, setCollection] = useState(null);
  const [loading, setLoading] = useState(true);
  const [allBooks, setAllBooks] = useState([]);
  const [pendingBooks, setPendingBooks] = useState([]);
  const pendingIdsRef = useRef(new Set());
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState("");
  const [removingBookId, setRemovingBookId] = useState(null);

  useEffect(() => {
    fetchCollection();
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
          ...(collection?.books || []).map(b => b.id),
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
    if (collection) loadBooks();
    return () => { cancelled = true; };
  }, [collection?.id, collection?.books?.length]);

  // Keep ref in sync so loadBooks never reads stale pending IDs
  useEffect(() => {
    pendingIdsRef.current = new Set(pendingBooks.map(b => b.id));
  }, [pendingBooks]);
  const fetchCollection = async () => {
    setLoading(true);
    try {
      const response = await axios.get(
        `${window.location.origin}${API_BASE_URL}/book-collections/${id}`
      );
      setCollection(response.data);
    } catch (err) {
      console.error("Error fetching book collection:", err);
      setCollection(null);
    }
    setLoading(false);
  };

  const handleSelectBook = (bookId) => {
    if (bookId == null) return;
    const book = allBooks.find(b => b.id === bookId);
    if (!book) return;
    setPendingBooks(prev => [...prev, book]);
    // Remove from available options
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
        `${window.location.origin}${API_BASE_URL}/book-collections/${id}/books/batch`,
        { book_ids: pendingBooks.map(b => b.id) }
      );
      setCollection(response.data);
      setPendingBooks([]);
    } catch (err) {
      const msg = err.response?.data?.detail || "Failed to add books";
      setError(msg);
    }
    setAdding(false);
  };

  const handleRemoveBook = async (bookId) => {
    setRemovingBookId(bookId);
    setError("");
    try {
      const response = await axios.delete(
        `${window.location.origin}${API_BASE_URL}/book-collections/${id}/books/${bookId}`
      );
      setCollection(response.data);
    } catch (err) {
      const msg = err.response?.data?.detail || "Failed to remove book";
      setError(msg);
    } finally {
      setRemovingBookId(null);
    }
  };

  if (loading) {
    return <div className="loading">Loading...</div>;
  }

  if (!collection) {
    return <div className="error">Book Collection not found</div>;
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
        <h1 className="section-heading">{collection.name}</h1>
        <button
          className="btn-primary-blue"
          onClick={() => window.history.back()}
        >
          Back to Collections
        </button>

        <div className="details-content">
          <h2>Collection Information</h2>
          <p>
            <strong>Name:</strong> {collection.name}
          </p>
          {collection.intro && (
            <p>
              <strong>Description:</strong> {collection.intro}
            </p>
          )}
          {collection.total_books !== undefined && (
            <p>
              <strong>Books:</strong> {collection.total_books}
            </p>
          )}
        </div>

        <div className="details-content" style={{ marginTop: "1rem" }}>
          <h2>Add Books to Collection</h2>
          {error && <p style={{ color: "#ff3b30" }}>{error}</p>}

          <div style={{ display: "flex", gap: "0.5rem", alignItems: "flex-end" }}>
            <div style={{ flex: 1 }}>
              <SearchableSelect
                label="Search Book"
                value={null}
                onChange={handleSelectBook}
                options={allBooks}
                placeholder="Search and select books..."
                keepSearchOnSelect={true}
              />
            </div>
            <button
              className="btn-primary-blue"
              onClick={handleAddAll}
              disabled={adding || pendingBooks.length === 0}
              style={{ marginBottom: "24px" }}
            >
              {adding ? "Adding..." : `Add All (${pendingBooks.length})`}
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
                    title="Remove"
                  >
                    ×
                  </span>
                </span>
              ))}
            </div>
          )}
        </div>

        <div style={{ marginTop: "2rem" }}>
          <h2>Books in Collection</h2>
          <div className="grid">
            {collection.books && collection.books.length > 0 ? (
              collection.books.map((book) => (
                <div key={book.id} style={{ position: "relative" }}>
                  <BookCard book={book} />
                  <button
                    className="btn-pill-link"
                    onClick={() => handleRemoveBook(book.id)}
                    disabled={removingBookId === book.id}
                    style={{ marginTop: "0.5rem", color: "#ff3b30" }}
                  >
                    {removingBookId === book.id ? "Removing..." : "Remove"}
                  </button>
                </div>
              ))
            ) : (
              <p>No books in this collection yet.</p>
            )}
          </div>
        </div>
      </div>
    </section>
  );
};

export default BookCollectionDetails;
