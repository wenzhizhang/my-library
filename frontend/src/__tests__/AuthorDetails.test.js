import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import axios from 'axios';
import AuthorDetails from '../components/AuthorDetails';
import '../i18n';

// react-router-dom v7 has a broken "main" field; map to react-router and stub hooks.
// useSearchParams is stateful so page/query changes driven through the URL still re-render.
let mockInitialSearch = ''; // seeded by tests to simulate a restored URL on mount
jest.mock('react-router-dom', () => {
  const actual = jest.requireActual('react-router');
  const React = require('react');
  const mockNavigate = jest.fn();
  const useSearchParams = () => {
    const [sp, setSp] = React.useState(new URLSearchParams(mockInitialSearch));
    const setSearchParams = (next) => setSp(new URLSearchParams(next));
    return [sp, setSearchParams];
  };
  return { ...actual, useNavigate: () => mockNavigate, useParams: () => ({ id: '1' }), useSearchParams };
});

jest.mock('axios');

const mockAuthor = {
  id: 1,
  name: 'Test Author',
  name_cn: '',
  nation: '',
  dynasty: '',
  intro: 'Bio',
  photo: '',
};

const mockBooks = [
  { id: 1, title: 'Book One', isbn: '978-1', authors: ['A'], publisher: { name: 'P' }, category: { name: 'C' }, thumb_image: null },
  { id: 2, title: 'Book Two', isbn: '978-2', authors: ['B'], publisher: { name: 'P2' }, category: { name: 'C2' }, thumb_image: null },
];

beforeEach(() => {
  jest.clearAllMocks();
  mockInitialSearch = '';
  sessionStorage.clear();
  axios.get.mockImplementation((url) => {
    if (String(url).includes('/authors/1/books')) {
      return Promise.resolve({ data: { books: mockBooks, total_pages: 1, total_books: 2 } });
    }
    if (String(url).includes('/authors/nations')) return Promise.resolve({ data: { nations: [] } });
    if (String(url).includes('/authors/dynasties')) return Promise.resolve({ data: { dynasties: [] } });
    return Promise.resolve({ data: mockAuthor });
  });
});

afterEach(() => {
  jest.restoreAllMocks();
});

test('detail page book list uses the shared toolbar', async () => {
  jest.spyOn(require('../AuthContext'), 'useAuth').mockReturnValue({
    isAuthenticated: false,
    user: null,
    token: null,
    login: jest.fn(),
    logout: jest.fn(),
  });
  render(<AuthorDetails />);

  await screen.findByText('Book One');

  // Same search bar as BookList: search input, sort, per-page, cols, view toggle
  expect(screen.getByPlaceholderText('Search title, title_cn, ISBN\u2026')).toBeInTheDocument();
  expect(screen.getByDisplayValue('Title')).toBeInTheDocument();
  expect(screen.getByText('Series')).toBeInTheDocument();
  expect(screen.getByDisplayValue('10')).toBeInTheDocument();
  expect(screen.getByTitle('Switch to list view')).toBeInTheDocument();
  // Page info mirrors BookList format
  expect(screen.getByText(/Page 1 of 1 total\(2\)/)).toBeInTheDocument();
});

test('detail page restores book page from URL params on mount', async () => {
  jest.spyOn(require('../AuthContext'), 'useAuth').mockReturnValue({
    isAuthenticated: false,
    user: null,
    token: null,
    login: jest.fn(),
    logout: jest.fn(),
  });
  mockInitialSearch = '?page=2';
  render(<AuthorDetails />);

  await screen.findByText('Book One');

  const bookCalls = axios.get.mock.calls.filter((c) => String(c[0]).includes('/authors/1/books'));
  expect(bookCalls[0][1].params.page).toBe(2);
});

test('detail page search submits q to the books endpoint', async () => {
  jest.spyOn(require('../AuthContext'), 'useAuth').mockReturnValue({
    isAuthenticated: false,
    user: null,
    token: null,
    login: jest.fn(),
    logout: jest.fn(),
  });
  const { fireEvent } = require('@testing-library/react');
  render(<AuthorDetails />);
  await screen.findByText('Book One');

  const input = screen.getByPlaceholderText('Search title, title_cn, ISBN\u2026');
  fireEvent.change(input, { target: { value: 'Gatsby' } });
  fireEvent.click(screen.getByText('Search'));

  await waitFor(() => {
    const calls = axios.get.mock.calls;
    const bookCalls = calls.filter((c) => String(c[0]).includes('/authors/1/books'));
    const last = bookCalls[bookCalls.length - 1];
    expect(last[1].params.q).toBe('Gatsby');
  });
});
