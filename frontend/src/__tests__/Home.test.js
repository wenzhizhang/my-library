import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import axios from 'axios';
import Home from '../components/Home';

// react-router-dom v7 has a broken "main" field (points to non-existent dist/main.js).
// Map it to the working react-router package.
jest.mock('react-router-dom', () => jest.requireActual('react-router'));

jest.mock('axios');

const statsResponse = {
  data: {
    overview: {
      total_books: 128,
      total_authors: 74,
      total_publishers: 31,
      total_categories: 12,
    },
  },
};

function renderHome() {
  const { MemoryRouter } = jest.requireActual('react-router');
  return render(
    <MemoryRouter>
      <Home />
    </MemoryRouter>
  );
}

// Pin the count-up off so assertions read the settled value instead of racing rAF.
beforeEach(() => {
  jest.clearAllMocks();
  window.matchMedia = jest.fn().mockReturnValue({ matches: true, addListener() {}, removeListener() {} });
  axios.get.mockResolvedValue(statsResponse);
});

afterEach(() => {
  jest.restoreAllMocks();
});

test('renders a turning sheet per headline stat with API counts', async () => {
  const { container } = renderHome();

  expect(container.querySelectorAll('.page--turn')).toHaveLength(4);

  await waitFor(() => expect(screen.getAllByText('128').length).toBeGreaterThan(0));
  expect(screen.getAllByText('74').length).toBeGreaterThan(0);
  expect(screen.getAllByText('31').length).toBeGreaterThan(0);
  expect(screen.getAllByText('12').length).toBeGreaterThan(0);
});

test('each sheet carries both faces so the turn never reveals bare background', () => {
  const { container } = renderHome();
  container.querySelectorAll('.page--turn').forEach((page) => {
    expect(page.querySelector('.page__face--front')).toBeInTheDocument();
    expect(page.querySelector('.page__face--back')).toBeInTheDocument();
  });
});

test('falls back to zeros when the stats request fails', async () => {
  axios.get.mockRejectedValue(new Error('offline'));
  const { container } = renderHome();

  await waitFor(() => expect(axios.get).toHaveBeenCalled());
  expect(container.querySelectorAll('.page--turn')).toHaveLength(4);
  expect(screen.getAllByText('0').length).toBeGreaterThan(0);
});