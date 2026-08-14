import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import axios from 'axios';
import Settings from '../components/Settings';
import { BackgroundProvider } from '../BackgroundContext';
import '../i18n';

jest.mock('react-router-dom', () => {
  const actual = jest.requireActual('react-router');
  const mockNavigate = jest.fn();
  return { ...actual, useNavigate: () => mockNavigate, mockNavigate };
});

jest.mock('axios');

const mockBackgrounds = [
  { id: 'bg2', name: 'Background 2', url: 'https://cdn.example.com/bg2.jpg' },
  { id: 'bg3', name: 'Background 3', url: 'https://cdn.example.com/bg3.jpg' },
  { id: 'bg4', name: 'Background 4', url: 'https://cdn.example.com/bg4.jpg' },
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
  const { MemoryRouter } = jest.requireActual('react-router');
  return render(
    <MemoryRouter>
      <BackgroundProvider>{ui}</BackgroundProvider>
    </MemoryRouter>
  );
}

beforeEach(() => {
  jest.clearAllMocks();
  axios.get.mockImplementation((url) => {
    if (url === '/api/backgrounds') {
      return Promise.resolve({ data: { default_id: 'bg4', backgrounds: mockBackgrounds } });
    }
    if (url === '/api/backgrounds/me') {
      return Promise.resolve({ data: { background_id: null } });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });
  axios.put.mockResolvedValue({ data: {} });
});

afterEach(() => {
  jest.restoreAllMocks();
});

test('renders background options from the API', async () => {
  renderWithProviders(<Settings />, { isAuthenticated: true });
  await waitFor(() => {
    expect(screen.getByText('Background 2')).toBeInTheDocument();
  });
  expect(screen.getByText('Background 3')).toBeInTheDocument();
  expect(screen.getByText('Background 4')).toBeInTheDocument();
});

test('default background is marked active when user has no selection', async () => {
  renderWithProviders(<Settings />, { isAuthenticated: true });
  await waitFor(() => {
    const active = screen.getByRole('button', { name: 'Background 4' });
    expect(active).toHaveAttribute('aria-pressed', 'true');
  });
});

test('user selection marks the chosen card and saves it', async () => {
  axios.get.mockImplementation((url) => {
    if (url === '/api/backgrounds') {
      return Promise.resolve({ data: { default_id: 'bg4', backgrounds: mockBackgrounds } });
    }
    if (url === '/api/backgrounds/me') {
      return Promise.resolve({ data: { background_id: 'bg3' } });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });
  renderWithProviders(<Settings />, { isAuthenticated: true });
  await waitFor(() => {
    expect(screen.getByRole('button', { name: 'Background 3' })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
  });

  fireEvent.click(screen.getByRole('button', { name: 'Background 2' }));
  await waitFor(() => {
    expect(axios.put).toHaveBeenCalledWith('/api/backgrounds/me', { background_id: 'bg2' });
  });
  await waitFor(() => {
    expect(screen.getByRole('button', { name: 'Background 2' })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
  });
});

test('guests see a hint and cannot pick (no PUT)', async () => {
  renderWithProviders(<Settings />, { isAuthenticated: false });
  await waitFor(() => {
    expect(screen.getByText('Background 2')).toBeInTheDocument();
  });
  expect(screen.getByText(/Guests use the default background/)).toBeInTheDocument();

  const card = screen.getByRole('button', { name: 'Background 2' });
  expect(card).toBeDisabled();
  fireEvent.click(card);
  expect(axios.put).not.toHaveBeenCalled();
});

test('save failure reverts the selection and shows an error', async () => {
  axios.put.mockRejectedValueOnce({ response: { status: 500 } });
  renderWithProviders(<Settings />, { isAuthenticated: true });
  await waitFor(() => {
    expect(screen.getByRole('button', { name: 'Background 4' })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
  });

  fireEvent.click(screen.getByRole('button', { name: 'Background 2' }));
  await waitFor(() => {
    expect(screen.getByText(/Failed to save/)).toBeInTheDocument();
  });
  expect(screen.getByRole('button', { name: 'Background 4' })).toHaveAttribute(
    'aria-pressed',
    'true'
  );
});
