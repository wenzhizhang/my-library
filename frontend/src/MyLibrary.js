// src/MyLibrary.js
import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import Header from './components/Header';
import Books from './components/Books';
import Authors from './components/Authors';
import Publishers from './components/Publishers';
import Categories from './components/Categories';
import Bookshelves from './components/Bookshelves';
import Brands from './components/Brands';
import Series from './components/Series';
import BookDetails from './components/BookDetails';
import BookFormPage from './components/BookFormPage';
import AuthorDetails from './components/AuthorDetails';
import PublisherDetails from './components/PublisherDetails';
import CategoryDetails from './components/CategoryDetails';
import BookshelfDetails from './components/BookshelfDetails';
import BrandDetails from './components/BrandDetails';
import SeriesDetails from './components/SeriesDetails';
import './MyLibrary.css';

function MyLibrary() {
  console.log("MyLibrary component rendered");
  
  return (
    <div className="MyLibrary" style={{ minHeight: '100vh', backgroundColor: '#f5f5f7' }}>
      <Header isMyLibrary={true} /> {/* 传递isMyLibrary参数 */}
      <main className="mylibrary-main" style={{ padding: '80px 20px 20px', minHeight: 'calc(100vh - 80px)' }}>
        <Routes>
          {/* 相对路径，因为这是在/my-library/下的嵌套路由 */}
          <Route path="/" element={<Navigate to="books" replace />} />
          <Route path="books" element={<Books />} />
          <Route path="books/create" element={<BookFormPage />} />
          <Route path="books/:id" element={<BookDetails />} />
          
          <Route path="authors" element={<Authors />} />
          <Route path="authors/:id" element={<AuthorDetails />} />
          
          <Route path="publishers" element={<Publishers />} />
          <Route path="publishers/:id" element={<PublisherDetails />} />
          
          <Route path="categories" element={<Categories />} />
          <Route path="categories/:id" element={<CategoryDetails />} />
          
          <Route path="bookshelves" element={<Bookshelves />} />
          <Route path="bookshelves/:id" element={<BookshelfDetails />} />
          
          <Route path="brands" element={<Brands />} />
          <Route path="brands/:id" element={<BrandDetails />} />
          
          <Route path="series" element={<Series />} />
          <Route path="series/:id" element={<SeriesDetails />} />
        </Routes>
      </main>
    </div>
  );
}

export default MyLibrary;