import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import axios from 'axios';
import Authors from '../components/Authors';

jest.mock('axios');
jest.mock('react-router-dom');

const mockNavigate = require('react-router-dom').mockNavigate;

const mockAuthors = [
  { id: 1, name: '东野圭吾', name_cn: '东野圭吾', nation: '日本', dynasty: '', intro: 'Japanese mystery writer', photo: '', weight: 12 },
  { id: 2, name: '刘慈欣', name_cn: '刘慈欣', nation: '中国', dynasty: '当代', intro: 'Science fiction author', photo: '', weight: 5 },
];

function renderWithProviders(ui, { isAuthenticated = false } = {}) {
  const mockAuth = {
    isAuthenticated,
    user: isAuthenticated ? { id: 1, username: 'test' } : null,
    token: isAuthenticated ? 'fake-token' : null,
    login: jest.fn(),
    logout: jest.fn(),
  };
  jest.spyOn(require('../AuthContext'), 'useAuth').mockReturnValue(mockAuth);
  return render(ui);
}

beforeEach(() => {
  jest.clearAllMocks();
  mockNavigate.mockClear();
  sessionStorage.clear();
});

afterEach(() => {
  jest.restoreAllMocks();
});

test('renders loading then authors list', async () => {
  axios.get.mockResolvedValue({
    data: { authors: mockAuthors, total_pages: 1, total_authors: 2 },
  });

  renderWithProviders(<Authors />, { isAuthenticated: false });

  // Loading indicator appears immediately
  expect(screen.getByText('Loading…')).toBeInTheDocument();

  // Wait for cards to appear — author names are in paragraphs together with nation/dynasty
  await waitFor(() => {
    expect(screen.getByText(/东野圭吾/)).toBeInTheDocument();
  });
  // Verify API call
  expect(axios.get).toHaveBeenCalledWith(
    'http://localhost/api/authors/',
    { params: { page: 1, limit: 20, sort_by: 'weight' } }
  );
});

test('search triggers API call with q param', async () => {
  axios.get.mockResolvedValue({
    data: { authors: [mockAuthors[0]], total_pages: 1, total_authors: 1 },
  });

  renderWithProviders(<Authors />, { isAuthenticated: false });

  // Wait for initial load to finish
  await waitFor(() => {
    expect(screen.getByText(/东野圭吾/)).toBeInTheDocument();
  });

  // Clear the mock call count from initial load
  axios.get.mockClear();

  // Type search query and submit
  const searchInput = screen.getByPlaceholderText('Search authors…');
  fireEvent.change(searchInput, { target: { value: '东野' } });
  fireEvent.click(screen.getByText('Search'));

  // Verify API call with q param
  await waitFor(() => {
    expect(axios.get).toHaveBeenCalledWith(
      'http://localhost/api/authors/',
      { params: { page: 1, limit: 20, sort_by: 'weight', q: '东野' } }
    );
  });
});

test('create button shows when authenticated', async () => {
  axios.get.mockResolvedValue({
    data: { authors: [], total_pages: 1, total_authors: 0 },
  });

  renderWithProviders(<Authors />, { isAuthenticated: true });

  await waitFor(() => {
    expect(screen.getByText('+ Create Author')).toBeInTheDocument();
  });
});

test('create button hidden when not authenticated', async () => {
  axios.get.mockResolvedValue({
    data: { authors: [], total_pages: 1, total_authors: 0 },
  });

  renderWithProviders(<Authors />, { isAuthenticated: false });

  await waitFor(() => {
    expect(screen.queryByText('+ Create Author')).not.toBeInTheDocument();
  });
});

test('view button navigates to author detail', async () => {
  axios.get.mockResolvedValue({
    data: { authors: mockAuthors, total_pages: 1, total_authors: 2 },
  });

  renderWithProviders(<Authors />, { isAuthenticated: false });

  await waitFor(() => {
    expect(screen.getByText(/东野圭吾/)).toBeInTheDocument();
  });

  const viewButtons = screen.getAllByText('View');
  fireEvent.click(viewButtons[0]);

  expect(mockNavigate).toHaveBeenCalledWith('1');
});

test('empty list shows no cards', async () => {
  axios.get.mockResolvedValue({
    data: { authors: [], total_pages: 1, total_authors: 0 },
  });

  renderWithProviders(<Authors />, { isAuthenticated: false });

  await waitFor(() => {
    expect(screen.queryByRole('button', { name: 'View' })).not.toBeInTheDocument();
  });
});
