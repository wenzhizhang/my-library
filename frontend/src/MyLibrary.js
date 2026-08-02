// src/MyLibrary.js
import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './AuthContext';
import './i18n';
import Header from './components/Header';
import Books from './components/Books';
import Authors from './components/Authors';
import Publishers from './components/Publishers';
import Categories from './components/Categories';
import Bookshelves from './components/Bookshelves';
import BookCollections from './components/BookCollections';
import ReadingPlans from './components/ReadingPlans';
import Brands from './components/Brands';
import Wishlist from './components/Wishlist';
import Archived from './components/Archived';
import Series from './components/Series';
import BookDetails from './components/BookDetails';
import BookFormPage from './components/BookFormPage';
import AuthorDetails from './components/AuthorDetails';
import PublisherDetails from './components/PublisherDetails';
import CategoryDetails from './components/CategoryDetails';
import BrandDetails from './components/BrandDetails';
import BookshelfDetails from './components/BookshelfDetails';
import BookCollectionDetails from './components/BookCollectionDetails';
import ReadingPlanDetails from './components/ReadingPlanDetails';
import SeriesDetails from './components/SeriesDetails';
import Login from './components/Login';
import Register from './components/Register';
import StatsPage from './components/StatsPage';
import ExportPage from './components/ExportPage';
import './MyLibrary.css';

function MyLibrary() {
  console.log("MyLibrary component rendered");
  
  return (
    <AuthProvider>
      <div className="MyLibrary" style={{ minHeight: '100vh', backgroundColor: '#f5f5f7' }}>
        <Header isMyLibrary={true}/>
        <main className="mylibrary-main">
          <Routes>
            <Route path="/" element={<Navigate to="books" replace />} />
            <Route path="login" element={<Login />} />
            <Route path="wishlist" element={<Wishlist />} />
            <Route path="register" element={<Register />} />
            <Route path="books" element={<Books />} />
            <Route path="books/edit/:bookId" element={<BookFormPage />} />
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


            <Route path="book-collections" element={<BookCollections />} />
            <Route path="book-collections/:id" element={<BookCollectionDetails />} />
            <Route path="reading-plans" element={<ReadingPlans />} />
            <Route path="reading-plans/:id" element={<ReadingPlanDetails />} />
            <Route path="brands" element={<Brands />} />
            <Route path="brands/:id" element={<BrandDetails />} />

            <Route path="series" element={<Series />} />
            <Route path="series/:id" element={<SeriesDetails />} />
            <Route path="stats" element={<StatsPage />} />
            <Route path="archived" element={<Archived />} />
            <Route path="export" element={<ExportPage />} />
          </Routes>
        </main>
      </div>
    </AuthProvider>
  );
}

export default MyLibrary;
