import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import Header from './components/Header';
import Books from './components/Books';
import Authors from './components/Authors';
import Publishers from './components/Publishers';
import Categories from './components/Categories';
import Bookshelves from './components/Bookshelves';
import Brands from './components/Brands';
import Series from './components/Series';
import BookDetails from './components/BookDetails';
import AuthorDetails from './components/AuthorDetails';
import PublisherDetails from './components/PublisherDetails';
import CategoryDetails from './components/CategoryDetails';
import BookshelfDetails from './components/BookshelfDetails';
import BrandDetails from './components/BrandDetails';
import SeriesDetails from './components/SeriesDetails';
import './App.css';

function App() {
  return (
    <Router>
      <div className="App">
        <Header />
        <Routes>
          <Route path="/" element={<Books />} />
          <Route path="/books" element={<Books />} />
          <Route path="/authors" element={<Authors />} />
          <Route path="/publishers" element={<Publishers />} />
          <Route path="/categories" element={<Categories />} />
          <Route path="/bookshelves" element={<Bookshelves />} />
          <Route path="/brands" element={<Brands />} />
          <Route path="/series" element={<Series />} />
          <Route path="/books/:id" element={<BookDetails />} />
          <Route path="/authors/:id" element={<AuthorDetails />} />
          <Route path="/publishers/:id" element={<PublisherDetails />} />
          <Route path="/categories/:id" element={<CategoryDetails />} />
          <Route path="/bookshelves/:id" element={<BookshelfDetails />} />
          <Route path="/brands/:id" element={<BrandDetails />} />
          <Route path="/series/:id" element={<SeriesDetails />} />
        </Routes>
      </div>
    </Router>
  );
}

export default App;
