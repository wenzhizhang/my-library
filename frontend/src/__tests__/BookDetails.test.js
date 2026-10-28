import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import axios from 'axios';

import i18n from '../i18n';
import BookDetails from '../components/BookDetails';

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => {
  const actual = jest.requireActual('react-router');
  return { ...actual, useNavigate: () => mockNavigate, useParams: () => ({ id: '1' }) };
});

jest.mock('axios');

const baseBook = {
  id: 1,
  title: 'Test Book',
  title_cn: '测试书',
  isbn: '978-1',
  authors: [{ id: 1, name: 'A', name_cn: '甲' }],
  publisher: { id: 1, name: 'P', name_cn: '出版社' },
  category: { id: 1, name: 'C', name_cn: '分类' },
  tags: [],
  archived: false,
  in_wish: false,
  read_state: '',
  summary: 'S',
  introduction: 'I',
  catalog: '',
  thumb_image: null,
};

function renderBook(overrides = {}) {
  jest.spyOn(require('../AuthContext'), 'useAuth').mockReturnValue({
    isAuthenticated: true,
    user: { id: 1 },
  });
  axios.get.mockImplementation((url) => {
    if (String(url).includes('/similar')) {
      return Promise.resolve({ data: { similar_books: [] } });
    }
    return Promise.resolve({ data: { ...baseBook, ...overrides } });
  });
  axios.put.mockResolvedValue({ data: {} });
  return render(
    <MemoryRouter initialEntries={['/my-library/books/1']}>
      <Routes>
        <Route path="/my-library/books/:id" element={<BookDetails />} />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => {
  jest.clearAllMocks();
});

test('lists back, edit and archive in that order for a normal book', async () => {
  renderBook();

  const back = await screen.findByText(i18n.t('books.backToList'));
  const edit = screen.getByText(i18n.t('bookDetails.edit'));
  const archive = screen.getByText(i18n.t('bookDetails.archive'));

  expect(back).toBeInTheDocument();
  expect(edit).toBeInTheDocument();
  expect(archive).toBeInTheDocument();
  expect(screen.queryByText(i18n.t('bookDetails.unarchive'))).toBeNull();

  expect(back.compareDocumentPosition(edit) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  expect(edit.compareDocumentPosition(archive) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

  fireEvent.click(edit);
  expect(mockNavigate).toHaveBeenCalledWith('/my-library/books/edit/1');
});

test('holding the archive button writes the archive and confirms', async () => {
  renderBook();
  await screen.findByText(i18n.t('books.backToList'));
  const button = screen.getByTestId('hold-to-confirm');

  jest.useFakeTimers();
  try {
    fireEvent.pointerDown(button, { pointerType: 'mouse', button: 0 });
    act(() => {
      jest.advanceTimersByTime(1600);
    });
    await act(async () => {
      await Promise.resolve();
    });
    act(() => {
      jest.advanceTimersByTime(400);
    });
    await act(async () => {
      await Promise.resolve();
    });

    expect(axios.put).toHaveBeenCalledWith(
      expect.stringContaining('/api/books/1/archive')
    );
    expect(
      screen.getByText(`✓ ${i18n.t('bookDetails.archived')}`)
    ).toBeInTheDocument();
  } finally {
    jest.useRealTimers();
  }
});

test('archived book offers unarchive and restores the book', async () => {
  renderBook({ archived: true });

  const back = await screen.findByText(i18n.t('books.backToList'));
  const unarchive = screen.getByText(i18n.t('bookDetails.unarchive'));

  expect(screen.queryByText(i18n.t('bookDetails.edit'))).toBeNull();
  expect(screen.queryByText(i18n.t('bookDetails.archive'))).toBeNull();
  expect(back.compareDocumentPosition(unarchive) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

  fireEvent.click(unarchive);

  await waitFor(() =>
    expect(axios.put).toHaveBeenCalledWith(
      expect.stringContaining('/api/books/1/unarchive')
    )
  );
  await waitFor(() =>
    expect(screen.queryByText(i18n.t('bookDetails.unarchive'))).toBeNull()
  );
  expect(screen.getByText(i18n.t('bookDetails.edit'))).toBeInTheDocument();
});
