import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import './hover.css';
import BookCard from './BookCard';
import { API_BASE_URL } from './Config';

const Books = () => {
  const [books, setBooks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [limit, setLimit] = useState(10);
  const [sortBy, setSortBy] = useState('title');
  const [totalPages, setTotalPages] = useState(1);
  const [totalBooks, setTotalBooks] = useState(0);
  const [goToPage, setGoToPage] = useState('');
  const [showAdvanced, setShowAdvanced] = useState(false);

  // 👉 输入状态（不会立即触发搜索）
  const [inputParams, setInputParams] = useState({
    isbn: '',
    title: '',
    author: '',
    publisher: ''
  });

  // 👉 真正用于请求的参数（点击 Search 才更新）
  const [searchParams, setSearchParams] = useState({
    isbn: '',
    title: '',
    author: '',
    publisher: ''
  });

  const navigate = useNavigate();
  const location = useLocation();

  // URL -> state
  useEffect(() => {
    const params = new URLSearchParams(location.search);

    const init = {
      isbn: params.get('isbn') || '',
      title: params.get('title') || '',
      author: params.get('author') || '',
      publisher: params.get('publisher') || ''
    };

    setPage(parseInt(params.get('page')) || 1);
    setLimit(parseInt(params.get('limit')) || 10);
    setSortBy(params.get('sort_by') || 'title');

    setInputParams(init);
    setSearchParams(init);
  }, []);

  // state -> URL
  useEffect(() => {
    const params = new URLSearchParams({
      page,
      limit,
      sort_by: sortBy,
      ...Object.fromEntries(Object.entries(searchParams).filter(([_, v]) => v))
    });

    navigate(`?${params.toString()}`, { replace: true });
  }, [page, limit, sortBy, searchParams]);

  // fetch only when searchParams change
  useEffect(() => {
    fetchBooks();
  }, [page, limit, sortBy, searchParams]);

  const fetchBooks = async () => {
    setLoading(true);
    try {
      const params = {
        page,
        limit,
        sort_by: sortBy,
        ...Object.fromEntries(Object.entries(searchParams).filter(([_, v]) => v))
      };

      const response = await axios.get(`${API_BASE_URL}/books/`, { params });
      const data = response.data;

      setBooks(data.books || []);
      setTotalPages(data.total_pages || 1);
      setTotalBooks(data.total_books || 0);
    } catch (error) {
      console.error('Error fetching books:', error);
    }
    setLoading(false);
  };

  // 输入变化（不触发搜索）
  const handleInputChange = (field, value) => {
    setInputParams(prev => ({ ...prev, [field]: value }));
  };

  // 点击搜索才触发
  const handleSearch = () => {
    setSearchParams(inputParams);
    setPage(1);
  };

  const handleSortChange = (val) => {
    setSortBy(val);
    setPage(1);
  };

  const handleLimitChange = (val) => {
    setLimit(parseInt(val));
    setPage(1);
  };

  const handleGoToPage = () => {
    const pageNum = Math.min(Math.max(parseInt(goToPage) || 1, 1), totalPages);
    setPage(pageNum);
    setGoToPage('');
  };

  const renderPagination = () => {
    const pages = [];
    const startPage = Math.max(1, page - 2);
    const endPage = Math.min(totalPages, page + 2);

    for (let i = startPage; i <= endPage; i++) {
      pages.push(
        <button key={i} className={`btn-pill-link ${i === page ? 'active' : ''}`} onClick={() => setPage(i)}>
          {i}
        </button>
      );
    }

    return (
      <div className="pagination">
        <div className="pagination-links">
          {page > 1 && (
            <>
              <button className="btn-pill-link" onClick={() => setPage(1)}>First</button>
              <button className="btn-pill-link" onClick={() => setPage(page - 1)}>Previous</button>
            </>
          )}

          {pages}

          {page < totalPages && (
            <>
              <button className="btn-pill-link" onClick={() => setPage(page + 1)}>Next</button>
              <button className="btn-pill-link" onClick={() => setPage(totalPages)}>Last</button>
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
          />
          <button className="btn-pill-link" onClick={handleGoToPage}>Go</button>
        </div>
      </div>
    );
  };

  if (loading) return <div className="loading">Loading...</div>;

  return (
    <section className="section light">
      <div className="container">
        <h1 className="section-heading">Books</h1>

        <button className="btn-primary-blue" onClick={() => navigate('/books/create')}>
          Add New Book
        </button>

        <div className="controls">
          <div className="control-group search-group">
            <input
              placeholder="Search ISBN"
              value={inputParams.isbn}
              onChange={(e) => handleInputChange('isbn', e.target.value)}
            />

            <button className="btn-pill-link" onClick={handleSearch}>
              Search
            </button>

            <button className="btn-pill-link" onClick={() => setShowAdvanced(!showAdvanced)}>
              Advanced
            </button>
          </div>

          {showAdvanced && (
            <div className="control-group search-group advanced">
              <input placeholder="Title" value={inputParams.title} onChange={(e) => handleInputChange('title', e.target.value)} />
              <input placeholder="Author" value={inputParams.author} onChange={(e) => handleInputChange('author', e.target.value)} />
              <input placeholder="Publisher" value={inputParams.publisher} onChange={(e) => handleInputChange('publisher', e.target.value)} />
            </div>
          )}

          <div className="control-group">
            <select value={sortBy} onChange={(e) => handleSortChange(e.target.value)}>
              <option value="id">ID</option>
              <option value="title">Title</option>
              <option value="created_at">Date Added</option>
            </select>

            <select value={limit} onChange={(e) => handleLimitChange(e.target.value)}>
              <option value="5">5</option>
              <option value="10">10</option>
              <option value="20">20</option>
              <option value="50">50</option>
            </select>
          </div>

          <div className="page-info">
            Page {page} / {totalPages} ({totalBooks})
          </div>
        </div>

        <div className="grid">
          {books.map(book => (
            <BookCard key={book.id} book={book} />
          ))}
        </div>

        {renderPagination()}
      </div>
    </section>
  );
};

export default Books;
