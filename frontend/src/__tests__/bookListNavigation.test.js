import axios from 'axios';

import { openLastBookListPage } from '../components/bookListNavigation';

jest.mock('axios');

beforeEach(() => {
  jest.clearAllMocks();
  axios.get.mockResolvedValue({ data: { total_pages: 4 } });
});

test('opens the last page of the list that contains the new book', async () => {
  const navigate = jest.fn();

  await openLastBookListPage(navigate, { in_wish: true });

  expect(axios.get).toHaveBeenCalledWith(
    expect.stringContaining('/books/wishlist'),
    { params: { page: 1, limit: 20, sort_by: 'id' } }
  );
  expect(navigate).toHaveBeenCalledWith(
    expect.stringMatching(/\/my-library\/wishlist\?.*page=4/)
  );
});

test('archived books open the archived list', async () => {
  const navigate = jest.fn();

  await openLastBookListPage(navigate, { archived: true });

  expect(axios.get).toHaveBeenCalledWith(
    expect.stringContaining('/books/archived'),
    { params: { page: 1, limit: 20, sort_by: 'id' } }
  );
  expect(navigate).toHaveBeenCalledWith(
    expect.stringMatching(/\/my-library\/archived\?.*page=4/)
  );
});
