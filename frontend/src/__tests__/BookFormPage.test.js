import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import axios from 'axios';
import BookFormPage from '../components/BookFormPage';

jest.mock('axios');
jest.mock('react-router-dom');

const { mockNavigate } = require('react-router-dom');

jest.mock('../AuthContext', () => ({
  useAuth: () => ({ isAuthenticated: false }),
}));

const todayISO = new Date().toISOString().split('T')[0];

const mockAuthors = [
  { id: 1, name: 'Author One', name_cn: null, nation: null, dynasty: null },
];

const mockPublishers = [
  { id: 1, name: 'Test Publisher' },
];

const mockBrands = [];
const mockSeries = [];
const mockCategories = [];
const mockBookshelves = [];

function setupAxiosMocks() {
  axios.get.mockImplementation((url) => {
    if (url.includes('/authors/')) {
      return Promise.resolve({ data: { authors: mockAuthors } });
    }
    if (url.includes('/publishers/')) {
      return Promise.resolve({ data: { publishers: mockPublishers } });
    }
    if (url.includes('/brands/')) {
      return Promise.resolve({ data: { brands: mockBrands } });
    }
    if (url.includes('/series/')) {
      return Promise.resolve({ data: { series: mockSeries } });
    }
    if (url.includes('/categories/')) {
      return Promise.resolve({ data: { categories: mockCategories } });
    }
    if (url.includes('/bookshelves/')) {
      return Promise.resolve({ data: { bookshelves: mockBookshelves } });
    }
    if (url.includes('/config/purchase-stores')) {
      return Promise.resolve({
        data: {
          purchase_stores: ['京东自营', '当当自营', '淘宝'],
        },
      });
    }
    return Promise.reject(new Error(`Unexpected URL: ${url}`));
  });
}

function renderForm() {
  return render(<BookFormPage />);
}

beforeEach(() => {
  jest.clearAllMocks();
  setupAxiosMocks();
});

afterEach(() => {
  jest.restoreAllMocks();
});

// ----------------------------------------------------------------
// 1. Form renders with default values
// ----------------------------------------------------------------
describe('default values', () => {
  test('renders heading and key form fields', async () => {
    renderForm();

    expect(screen.getByText('Add New Book')).toBeInTheDocument();

    // ISBN field
    expect(screen.getByPlaceholderText('978-7-...')).toBeInTheDocument();

    // Title fields
    expect(screen.getByPlaceholderText('书名')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Original Title')).toBeInTheDocument();
  });

  test('binding_type defaults to 精装', async () => {
    renderForm();
    const bindingInput = screen.getByDisplayValue('精装');
    expect(bindingInput).toBeInTheDocument();
  });

  test('language defaults to 中文', async () => {
    renderForm();
    const langInput = screen.getByDisplayValue('中文');
    expect(langInput).toBeInTheDocument();
  });

  test('compose_type defaults to 横排', async () => {
    renderForm();
    const composeInput = screen.getByDisplayValue('横排');
    expect(composeInput).toBeInTheDocument();
  });

  test('paper_type defaults to 胶版纸', async () => {
    renderForm();
    const paperInput = screen.getByDisplayValue('胶版纸');
    expect(paperInput).toBeInTheDocument();
  });

  test('read_state defaults to unread', async () => {
    renderForm();
    // read_state uses pill buttons — verify 'unread' button text exists
    const readButtons = screen.getAllByText('unread');
    expect(readButtons.length).toBeGreaterThan(0);
  });

  test('pages and book_count both default to 1', async () => {
    renderForm();
    const inputs = screen.getAllByDisplayValue(1);
    expect(inputs.length).toBe(2);
  });
});

// ----------------------------------------------------------------
// 2. Purchase date defaults to today
// ----------------------------------------------------------------
describe('purchase date', () => {
  test('defaults to today', async () => {
    renderForm();
    const dateInput = screen.getByDisplayValue(todayISO);
    expect(dateInput).toBeInTheDocument();
    expect(dateInput).toHaveAttribute('type', 'date');
  });
});

// ----------------------------------------------------------------
// 3. Thumbnail checkbox fills /books/{isbn}.png
// ----------------------------------------------------------------
describe('thumbnail checkbox', () => {
  test('checkbox not checked when thumb_image is empty', async () => {
    renderForm();
    const checkbox = screen.getByRole('checkbox', {
      name: /use default/i,
    });
    expect(checkbox).not.toBeChecked();
  });

  test('checking checkbox sets thumb_image to /books/{isbn}.png', async () => {
    renderForm();

    const isbnInput = screen.getByPlaceholderText('978-7-...');
    fireEvent.change(isbnInput, { target: { value: '9781234567890' } });

    const checkbox = screen.getByRole('checkbox', { name: /use default/i });
    fireEvent.click(checkbox);

    const thumbInput = screen.getByDisplayValue('/books/9781234567890.png');
    expect(thumbInput).toBeInTheDocument();
  });

  test('unchecking checkbox clears thumb_image when apiThumbUrl is empty', async () => {
    renderForm();

    const isbnInput = screen.getByPlaceholderText('978-7-...');
    fireEvent.change(isbnInput, { target: { value: '9781234567890' } });

    const checkbox = screen.getByRole('checkbox', { name: /use default/i });
    fireEvent.click(checkbox); // check
    fireEvent.click(checkbox); // uncheck

    const thumbInput = screen.getByPlaceholderText('https://...');
    expect(thumbInput).toHaveValue('');
  });
});

// ----------------------------------------------------------------
// 4. Submit button sends correct payload
// ----------------------------------------------------------------
describe('submit', () => {
  test('sends POST with correct payload on create', async () => {
    axios.post.mockResolvedValue({ data: { id: 1 } });
    renderForm();

    const isbnInput = screen.getByPlaceholderText('978-7-...');
    fireEvent.change(isbnInput, { target: { value: '9781234567890' } });

    const titleCnInput = screen.getByPlaceholderText('书名');
    fireEvent.change(titleCnInput, { target: { value: '测试书名' } });

    const submitButton = screen.getByRole('button', { name: /create book/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(axios.post).toHaveBeenCalledTimes(1);
    });

    const callUrl = axios.post.mock.calls[0][0];
    const callPayload = axios.post.mock.calls[0][1];

    expect(callUrl).toContain('/api/books/');
    expect(callPayload.isbn).toBe('9781234567890');
    expect(callPayload.title_cn).toBe('测试书名');
    // Null values should be stripped for create mode
    expect(callPayload.purchase_price).toBeUndefined();
    // Non-null defaults should be present
    expect(callPayload.binding_type).toBe('精装');
    expect(callPayload.language).toBe('中文');
    expect(callPayload.compose_type).toBe('横排');
    expect(callPayload.paper_type).toBe('胶版纸');
    expect(callPayload.pages).toBe(1);
    expect(callPayload.book_count).toBe(1);
  });

  test('navigates after success (1500ms setTimeout)', async () => {
    axios.post.mockResolvedValue({ data: { id: 1 } });
    renderForm();

    const isbnInput = screen.getByPlaceholderText('978-7-...');
    fireEvent.change(isbnInput, { target: { value: '9781234567890' } });

    const titleCnInput = screen.getByPlaceholderText('书名');
    fireEvent.change(titleCnInput, { target: { value: '测试书名' } });

    const submitButton = screen.getByRole('button', { name: /create book/i });
    fireEvent.click(submitButton);

    // Component uses setTimeout(1500) before navigating
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/my-library/books');
    }, { timeout: 3000 });
  });

  test('shows success message before navigation', async () => {
    axios.post.mockResolvedValue({ data: { id: 1 } });
    renderForm();

    const isbnInput = screen.getByPlaceholderText('978-7-...');
    fireEvent.change(isbnInput, { target: { value: '9781234567890' } });

    const titleCnInput = screen.getByPlaceholderText('书名');
    fireEvent.change(titleCnInput, { target: { value: '测试书名' } });

    const submitButton = screen.getByRole('button', { name: /create book/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText(/book created successfully/i)).toBeInTheDocument();
    });
  });
});

// ----------------------------------------------------------------
// 5. ISBN field validation (required)
// ----------------------------------------------------------------
describe('ISBN validation', () => {
  test('shows error when ISBN is empty and title is filled', async () => {
    renderForm();

    const titleInput = screen.getByPlaceholderText('Original Title');
    fireEvent.change(titleInput, { target: { value: 'Some English Title' } });

    const submitButton = screen.getByRole('button', { name: /create book/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText('ISBN is required')).toBeInTheDocument();
    });
  });

  test('does not submit when ISBN is empty', async () => {
    renderForm();

    const titleCnInput = screen.getByPlaceholderText('书名');
    fireEvent.change(titleCnInput, { target: { value: '测试书名' } });

    const submitButton = screen.getByRole('button', { name: /create book/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText('ISBN is required')).toBeInTheDocument();
    });

    expect(axios.post).not.toHaveBeenCalled();
  });

  test('clears ISBN error when user types in ISBN field', async () => {
    renderForm();

    const titleInput = screen.getByPlaceholderText('Original Title');
    fireEvent.change(titleInput, { target: { value: 'Some Title' } });
    const submitButton = screen.getByRole('button', { name: /create book/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText('ISBN is required')).toBeInTheDocument();
    });

    const isbnInput = screen.getByPlaceholderText('978-7-...');
    fireEvent.change(isbnInput, { target: { value: '978' } });

    expect(screen.queryByText('ISBN is required')).not.toBeInTheDocument();
  });
});

// ----------------------------------------------------------------
// 6. Title validation (at least one of title/title_cn required)
// ----------------------------------------------------------------
describe('title validation', () => {
  test('does not submit when both titles are empty', async () => {
    renderForm();

    const isbnInput = screen.getByPlaceholderText('978-7-...');
    fireEvent.change(isbnInput, { target: { value: '9781234567890' } });

    const submitButton = screen.getByRole('button', { name: /create book/i });
    fireEvent.click(submitButton);

    // Give validation time to run
    await new Promise((r) => setTimeout(r, 200));

    expect(axios.post).not.toHaveBeenCalled();
  });

  test('passes validation with only title_cn filled', async () => {
    axios.post.mockResolvedValue({ data: { id: 1 } });
    renderForm();

    const isbnInput = screen.getByPlaceholderText('978-7-...');
    fireEvent.change(isbnInput, { target: { value: '9781234567890' } });

    const titleCnInput = screen.getByPlaceholderText('书名');
    fireEvent.change(titleCnInput, { target: { value: '中文书名' } });

    const submitButton = screen.getByRole('button', { name: /create book/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(axios.post).toHaveBeenCalledTimes(1);
    });
  });

  test('passes validation with only title (original) filled', async () => {
    axios.post.mockResolvedValue({ data: { id: 1 } });
    renderForm();

    const isbnInput = screen.getByPlaceholderText('978-7-...');
    fireEvent.change(isbnInput, { target: { value: '9781234567890' } });

    const titleInput = screen.getByPlaceholderText('Original Title');
    fireEvent.change(titleInput, { target: { value: 'English Title' } });

    const submitButton = screen.getByRole('button', { name: /create book/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(axios.post).toHaveBeenCalledTimes(1);
    });
  });
});
