import React from 'react';
import { Link } from 'react-router-dom';
import './Header.css';

const Header = () => {
  return (
    <nav className="nav">
      <div className="nav-container">
        <Link to="/" className="nav-logo">My Library</Link>
        <div className="nav-links">
          <Link to="/books" className="nav-link">Books</Link>
          <Link to="/authors" className="nav-link">Authors</Link>
          <Link to="/publishers" className="nav-link">Publishers</Link>
          <Link to="/categories" className="nav-link">Categories</Link>
          <Link to="/bookshelves" className="nav-link">Bookshelves</Link>
          <Link to="/brands" className="nav-link">Brands</Link>
          <Link to="/series" className="nav-link">Series</Link>
        </div>
      </div>
    </nav>
  );
};

export default Header;
