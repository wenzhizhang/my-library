import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import axios from 'axios';

import '../i18n';
import Assistant from '../components/Assistant';

// react-router-dom v7 ships a broken "main" field; map it to react-router.
jest.mock('react-router-dom', () => {
  const actual = jest.requireActual('react-router');
  return { ...actual, useNavigate: () => jest.fn() };
});

jest.mock('axios');

const { MemoryRouter } = jest.requireActual('react-router');

const answer = {
  query: '沙陀往事讲了什么',
  answer: '它讲沙陀人的历史。',
  sources: [{ book_id: 2300, title: '沙陀往事', title_cn: '沙陀往事', authors: [] }],
  no_answer: false,
};

const noAnswer = {
  query: '不存在的书',
  answer: '未在书库中找到相关书籍。',
  sources: [],
  no_answer: true,
  rewritten_query: '',
};

function renderAssistant({ isAuthenticated = true } = {}) {
  jest
    .spyOn(require('../AuthContext'), 'useAuth')
    .mockReturnValue({ isAuthenticated });
  return render(
    <MemoryRouter initialEntries={['/my-library/books']}>
      <Assistant />
    </MemoryRouter>
  );
}

function openAssistant(options) {
  renderAssistant(options);
  fireEvent.click(screen.getByTestId('assistant-trigger'));
  return screen.getByTestId('assistant-input');
}

async function ask(text) {
  const input = screen.getByTestId('assistant-input');
  fireEvent.change(input, { target: { value: text } });
  fireEvent.keyDown(input, { key: 'Enter' });
}

function lastPayload() {
  return axios.post.mock.calls.at(-1)[1];
}

beforeEach(() => {
  jest.clearAllMocks();
  axios.post.mockResolvedValue({ data: answer });
});

test('renders nothing for an anonymous visitor', () => {
  renderAssistant({ isAuthenticated: false });

  expect(screen.queryByTestId('assistant-trigger')).toBeNull();
});

test('opens the dialog from the floating trigger', () => {
  renderAssistant();

  expect(screen.getByTestId('assistant-panel')).toHaveAttribute('aria-hidden', 'true');

  fireEvent.click(screen.getByTestId('assistant-trigger'));

  expect(screen.getByTestId('assistant-panel')).toHaveAttribute('aria-hidden', 'false');
});

test('uses the mascot logo on the trigger and floating over the dialog', () => {
  openAssistant();

  const trigger = screen.getByTestId('assistant-trigger');
  expect(trigger.querySelector('img')).toHaveAttribute(
    'src',
    '/images/logo/assistant-320.png'
  );
  expect(screen.getByTestId('assistant-avatar')).toHaveAttribute(
    'src',
    '/images/logo/assistant-320.png'
  );
});

test('sends a question and renders the answer with its sources', async () => {
  openAssistant();

  await ask('沙陀往事讲了什么');

  expect(await screen.findByText('它讲沙陀人的历史。')).toBeInTheDocument();
  expect(axios.post).toHaveBeenCalledWith('/api/rag/answer', {
    query: '沙陀往事讲了什么',
    top_k: 5,
    history: [],
  });

  const source = screen.getByTestId('assistant-source');
  expect(source).toHaveTextContent('沙陀往事');
  expect(source).toHaveAttribute('href', '/my-library/books/2300');
});

test('sends previous turns as history', async () => {
  openAssistant();

  await ask('沙陀往事讲了什么');
  await screen.findByText('它讲沙陀人的历史。');
  await ask('作者是谁');

  await waitFor(() => expect(axios.post).toHaveBeenCalledTimes(2));
  expect(lastPayload()).toEqual({
    query: '作者是谁',
    top_k: 5,
    history: [
      { role: 'user', content: '沙陀往事讲了什么' },
      { role: 'assistant', content: '它讲沙陀人的历史。' },
    ],
  });
});

test('a not-found answer is localised and not replayed as context', async () => {
  axios.post.mockResolvedValueOnce({ data: noAnswer });
  openAssistant();

  await ask('不存在的书');
  expect(await screen.findByTestId('assistant-message-no-answer')).toBeInTheDocument();

  axios.post.mockResolvedValueOnce({ data: answer });
  await ask('换个问题');

  await waitFor(() => expect(axios.post).toHaveBeenCalledTimes(2));
  expect(lastPayload().history).toEqual([]);
});

test('an error bubble is not replayed as context', async () => {
  axios.post.mockRejectedValueOnce({ response: { status: 503 } });
  openAssistant();

  await ask('任意问题');
  expect(await screen.findByTestId('assistant-message-error')).toBeInTheDocument();

  axios.post.mockResolvedValueOnce({ data: answer });
  await ask('再试一次');

  await waitFor(() => expect(axios.post).toHaveBeenCalledTimes(2));
  expect(lastPayload().history).toEqual([]);
});

test('an example fills the input instead of sending it', () => {
  openAssistant();

  const example = screen.getAllByTestId('assistant-example')[0];
  fireEvent.click(example);

  expect(screen.getByTestId('assistant-input')).toHaveValue(example.textContent);
  expect(axios.post).not.toHaveBeenCalled();
});

test('shows the rewritten query when the backend rewrote a follow-up', async () => {
  axios.post.mockResolvedValueOnce({
    data: { ...answer, rewritten_query: '沙陀往事的作者是谁' },
  });
  openAssistant();

  await ask('作者是谁');

  const hint = await screen.findByTestId('assistant-rewritten');
  expect(hint).toHaveTextContent('沙陀往事的作者是谁');
});

test('new chat clears the conversation', async () => {
  openAssistant();
  await ask('沙陀往事讲了什么');
  await screen.findByText('它讲沙陀人的历史。');

  fireEvent.click(screen.getByTestId('assistant-new'));

  expect(screen.queryByText('它讲沙陀人的历史。')).toBeNull();
});
