import axios from 'axios';

import { API_BASE_URL, LIBRARY_PATH } from '../config';

const LIST_BY_KIND = {
  archived: { path: '/archived', endpoint: '/books/archived' },
  wishlist: { path: '/wishlist', endpoint: '/books/wishlist' },
  books: { path: '/books', endpoint: '/books/' },
};

/** Open the last ID-sorted page of the list that contains the new book. */
export async function openLastBookListPage(navigate, book) {
  const kind = book?.archived ? 'archived' : book?.in_wish ? 'wishlist' : 'books';
  const target = LIST_BY_KIND[kind];

  let totalPages = 1;
  try {
    const { data } = await axios.get(
      `${window.location.origin}${API_BASE_URL}${target.endpoint}`,
      { params: { page: 1, limit: 20, sort_by: 'id' } }
    );
    totalPages = data.total_pages || 1;
  } catch (error) {
    console.error('Error locating the last book list page:', error);
  }

  const params = new URLSearchParams({
    sort_by: 'id',
    page: String(totalPages),
    limit: '20',
    refresh: String(Date.now()),
  });
  navigate(`${LIBRARY_PATH}${target.path}?${params.toString()}`);
}
