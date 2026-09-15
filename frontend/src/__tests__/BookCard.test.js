import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import BookCard from '../components/BookCard';

// react-router-dom v7 has a broken "main" field (points to non-existent dist/main.js).
// Map it to the working react-router package and capture navigate calls.
jest.mock('react-router-dom', () => {
  const actual = jest.requireActual('react-router');
  const mockNavigate = jest.fn();
  return { ...actual, useNavigate: () => mockNavigate, mockNavigate };
});

const { mockNavigate } = require('react-router-dom');

const book = {
  id: 42,
  title: 'Test Book',
  title_cn: '测试书',
  isbn: '978-1',
  authors: ['Author A'],
  thumb_image: null,
};

beforeEach(() => {
  jest.clearAllMocks();
  jest.spyOn(require('../AuthContext'), 'useAuth').mockReturnValue({ isAuthenticated: true });
});

afterEach(() => {
  jest.restoreAllMocks();
});

test('edit hands the current page URL to the form so it can return there', () => {
  window.history.pushState({}, '', '/my-library/bookshelves/7?page=2&sort_by=created_at');
  render(<BookCard book={book} />);

  fireEvent.click(screen.getByText('Edit'));

  expect(mockNavigate).toHaveBeenCalledWith('/my-library/books/edit/42', {
    state: { from: '/my-library/bookshelves/7?page=2&sort_by=created_at' },
  });
});
