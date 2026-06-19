import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import axios from 'axios';
import Books from '../components/Books';

// react-router-dom v7 has a broken "main" field (points to non-existent dist/main.js).
// Map it to the working react-router package and provide a mock navigate.
jest.mock('react-router-dom', () => {
  const actual = jest.requireActual('react-router');
  const mockNavigate = jest.fn();
  return { ...actual, useNavigate: () => mockNavigate, mockNavigate };
});

jest.mock('axios');

const mockBooks = [
  { id: 1, title: 'Book One', title_cn: null, isbn: '978-1', authors: ['Author A'], thumb_image: null },
  { id: 2, title: 'Book Two', title_cn: null, isbn: '978-2', authors: ['Author B', 'Author C'], thumb_image: null },
];

const defaultResponse = { data: { books: mockBooks, total_pages: 5, total_books: 42 } };

function renderWithProviders(ui, { isAuthenticated = false, initialEntries } = {}) {
  const mockAuth = {
    isAuthenticated,
    user: isAuthenticated ? { id: 1, username: 'test' } : null,
    token: isAuthenticated ? 'fake-token' : null,
    login: jest.fn(),
    logout: jest.fn(),
  };
  jest.spyOn(require('../AuthContext'), 'useAuth').mockReturnValue(mockAuth);
  // Use real react-router MemoryRouter for proper useSearchParams
  const { MemoryRouter } = jest.requireActual('react-router');
  const routerOpts = initialEntries ? { initialEntries } : {};
  return render(<MemoryRouter {...routerOpts}>{ui}</MemoryRouter>);
}

beforeEach(() => {
  jest.clearAllMocks();
  sessionStorage.clear();
  axios.get.mockResolvedValue(defaultResponse);
});

afterEach(() => {
  jest.restoreAllMocks();
});

test('renders loading state initially', () => {
  axios.get.mockImplementationOnce(() => new Promise(() => {}));
  renderWithProviders(<Books />);
  expect(screen.getByText('Loading...')).toBeInTheDocument();
});

test('renders books list after fetch', async () => {
  renderWithProviders(<Books />);
  await waitFor(() => {
    expect(screen.getByText('Book One')).toBeInTheDocument();
  });
  expect(screen.getByText('Book Two')).toBeInTheDocument();
  expect(screen.getByText(/Page 1 \/ 5 \(42\)/)).toBeInTheDocument();
});

test('search input changes and triggers search', async () => {
  renderWithProviders(<Books />);
  await waitFor(() => expect(screen.getByText('Book One')).toBeInTheDocument());

  const searchInput = screen.getByPlaceholderText('Search title, title_cn, ISBN\u2026');
  fireEvent.change(searchInput, { target: { value: 'test' } });
  fireEvent.click(screen.getByText('Search'));

  await waitFor(() => {
    const calls = axios.get.mock.calls;
    const lastCall = calls[calls.length - 1];
    expect(lastCall[1].params.q).toBe('test');
  });
});

test('q param in URL triggers filtered fetch', async () => {
  renderWithProviders(<Books />, { initialEntries: ['/my-library/books?q=prefiltered'] });

  await waitFor(() => {
    expect(axios.get).toHaveBeenCalled();
  });

  const params = axios.get.mock.calls[0][1].params;
  expect(params.q).toBe('prefiltered');
});

test('sort dropdown changes sort order', async () => {
  renderWithProviders(<Books />);
  await waitFor(() => expect(screen.getByText('Book One')).toBeInTheDocument());

  const sortSelect = screen.getByDisplayValue('Title');
  fireEvent.change(sortSelect, { target: { value: 'id' } });

  await waitFor(() => {
    const calls = axios.get.mock.calls;
    const lastCall = calls[calls.length - 1];
    expect(lastCall[1].params.sort_by).toBe('id');
  });
});

test('page buttons change page', async () => {
  renderWithProviders(<Books />);
  await waitFor(() => expect(screen.getByText('Book One')).toBeInTheDocument());

  fireEvent.click(screen.getByText('2'));

  await waitFor(() => {
    const calls = axios.get.mock.calls;
    const lastCall = calls[calls.length - 1];
    expect(lastCall[1].params.page).toBe(2);
  });
});

test('advanced toggle shows/hides extra filters', async () => {
  renderWithProviders(<Books />);
  await waitFor(() => expect(screen.getByText('Book One')).toBeInTheDocument());

  expect(screen.queryByPlaceholderText('Search ISBN\u2026')).not.toBeInTheDocument();

  fireEvent.click(screen.getByText('Advanced'));
  expect(screen.getByPlaceholderText('Search ISBN\u2026')).toBeInTheDocument();
  expect(screen.getByPlaceholderText('Search author\u2026')).toBeInTheDocument();
  expect(screen.getByPlaceholderText('Search publisher\u2026')).toBeInTheDocument();
  expect(screen.getByPlaceholderText('Search tag\u2026')).toBeInTheDocument();
  expect(screen.getByPlaceholderText('YYYY')).toBeInTheDocument();
  expect(screen.getByPlaceholderText('MM')).toBeInTheDocument();

  fireEvent.click(screen.getByText('Advanced'));
  expect(screen.queryByPlaceholderText('Search ISBN\u2026')).not.toBeInTheDocument();
});

test('purchase year filter passed to API', async () => {
  renderWithProviders(<Books />);
  await waitFor(() => expect(screen.getByText('Book One')).toBeInTheDocument());

  fireEvent.click(screen.getByText('Advanced'));

  const yearInput = screen.getByPlaceholderText('YYYY');
  fireEvent.change(yearInput, { target: { value: '2023' } });
  fireEvent.click(screen.getByText('Search'));

  await waitFor(() => {
    const calls = axios.get.mock.calls;
    const lastCall = calls[calls.length - 1];
    expect(lastCall[1].params.purchase_year).toBe('2023');
  });
});
