import React from 'react';
import { act, render, screen, fireEvent, waitFor } from '@testing-library/react';
import axios from 'axios';

import i18n from '../i18n';
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
  sources: [
    {
      book_id: 2300,
      title: '沙陀往事',
      title_cn: '沙陀往事',
      authors: ['樊文礼'],
      thumb_image: '/images/books/1.webp',
    },
  ],
  no_answer: false,
  has_more: false,
  more: [],
  reason: '',
};

const noAnswer = {
  query: '不存在的书',
  answer: '未在书库中找到相关书籍。',
  sources: [],
  no_answer: true,
  has_more: false,
  more: [],
  reason: '',
};

const morePage = {
  query: '还有么？',
  answer: '还有这些：',
  sources: [],
  no_answer: false,
  has_more: false,
  more: [
    {
      book_id: 2301,
      title: '沙陀往事（二版）',
      title_cn: '沙陀往事（二版）',
      authors: ['樊文礼'],
      thumb_image: '/images/books/2.webp',
    },
  ],
  reason: '',
};

function renderAssistant({
  isAuthenticated = true,
  isGuest = false,
  path = '/my-library/books',
} = {}) {
  jest
    .spyOn(require('../AuthContext'), 'useAuth')
    .mockReturnValue({ isAuthenticated, isGuest });
  return render(
    <MemoryRouter initialEntries={[path]}>
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

test('greets the user with a short capability intro', () => {
  openAssistant();

  expect(screen.getByTestId('assistant-greeting')).toHaveTextContent(
    i18n.t('assistant.greeting')
  );
  expect(screen.getByTestId('assistant-intro')).toHaveTextContent(
    i18n.t('assistant.intro')
  );
});

test('stays hidden on the login page even with a session', () => {
  renderAssistant({ isAuthenticated: true, path: '/my-library/login' });

  expect(screen.queryByTestId('assistant-trigger')).toBeNull();
});

test('wakes up after a guest login', () => {
  renderAssistant({ isAuthenticated: false, isGuest: true });

  expect(screen.getByTestId('assistant-trigger')).toBeInTheDocument();
});

test('sleeps after a minute idle and cycles', () => {
  jest.useFakeTimers();
  try {
    renderAssistant();
    const triggerImg = () =>
      screen.getByTestId('assistant-trigger').querySelector('img');

    expect(triggerImg()).toHaveAttribute('src', '/images/logo/fox_180p.gif');

    act(() => jest.advanceTimersByTime(61_000));
    expect(triggerImg()).toHaveAttribute(
      'src',
      '/images/logo/fox_sleep_180x240_256.webp'
    );
    expect(triggerImg()).toHaveClass('is-sleeping');

    act(() => jest.advanceTimersByTime(60_000));
    expect(triggerImg()).toHaveAttribute('src', '/images/logo/fox_180p.gif');
    expect(triggerImg()).not.toHaveClass('is-sleeping');

    act(() => jest.advanceTimersByTime(60_000));
    expect(triggerImg()).toHaveAttribute(
      'src',
      '/images/logo/fox_sleep_180x240_256.webp'
    );
  } finally {
    jest.useRealTimers();
  }
});

test('typing wakes the sleeping avatar', () => {
  jest.useFakeTimers();
  try {
    openAssistant();
    act(() => jest.advanceTimersByTime(61_000));
    expect(screen.getByTestId('assistant-avatar')).toHaveAttribute(
      'src',
      '/images/logo/fox_sleep_360x480_256.webp'
    );

    fireEvent.change(screen.getByTestId('assistant-input'), {
      target: { value: '你好' },
    });

    expect(screen.getByTestId('assistant-avatar')).toHaveAttribute(
      'src',
      '/images/logo/fox_360p.gif'
    );
  } finally {
    jest.useRealTimers();
  }
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
  const triggerImg = trigger.querySelector('img');
  expect(triggerImg).toHaveAttribute('src', '/images/logo/fox_180p.gif');
  expect(triggerImg.getAttribute('srcset')).toContain('/images/logo/fox_360p.gif 2x');
  const avatar = screen.getByTestId('assistant-avatar');
  expect(avatar).toHaveAttribute('src', '/images/logo/fox_360p.gif');
  expect(avatar.getAttribute('srcset')).toContain('/images/logo/fox_720p.gif 2x');
});

test('sends a question and renders the answer as a book row', async () => {
  openAssistant();

  await ask('沙陀往事讲了什么');

  expect(await screen.findByText('它讲沙陀人的历史。')).toBeInTheDocument();
  expect(axios.post).toHaveBeenCalledWith('/api/rag/answer', {
    query: '沙陀往事讲了什么',
    top_k: 5,
    history: [],
    seen_book_ids: [],
  });

  const row = screen.getByTestId('assistant-book');
  expect(row).toHaveTextContent('沙陀往事');
  expect(row).toHaveTextContent('樊文礼');
  expect(row).toHaveAttribute('href', '/my-library/books/2300');
  expect(row.querySelector('img')).toHaveAttribute(
    'src',
    '/api/media/images/books/1.webp'
  );
});

test('renders one row per book', async () => {
  axios.post.mockResolvedValueOnce({
    data: {
      ...answer,
      sources: [
        answer.sources[0],
        {
          book_id: 2301,
          title: '沙陀往事（二版）',
          title_cn: '沙陀往事（二版）',
          authors: [],
          thumb_image: '/images/books/2.webp',
        },
      ],
    },
  });
  openAssistant();

  await ask('沙陀往事讲了什么');

  await waitFor(() => expect(screen.getAllByTestId('assistant-book')).toHaveLength(2));
});

test('sends previous turns as history and the books already shown', async () => {
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
    seen_book_ids: [2300],
  });
});

test('a more reply appends the next page of books', async () => {
  axios.post
    .mockResolvedValueOnce({ data: answer })
    .mockResolvedValueOnce({ data: morePage });
  openAssistant();

  await ask('沙陀往事讲了什么');
  await screen.findByText('它讲沙陀人的历史。');
  await ask('还有么？');

  await waitFor(() => expect(screen.getAllByTestId('assistant-book')).toHaveLength(2));
  expect(lastPayload().query).toBe('还有么？');
  expect(lastPayload().seen_book_ids).toEqual([2300]);
});

test('a typed follow-up after a more page sends well-formed history', async () => {
  axios.post
    .mockResolvedValueOnce({ data: answer })
    .mockResolvedValueOnce({ data: morePage })
    .mockResolvedValueOnce({ data: answer });
  openAssistant();

  await ask('沙陀往事讲了什么');
  await screen.findByText('它讲沙陀人的历史。');
  await ask('还有么？');
  await waitFor(() => expect(screen.getAllByTestId('assistant-book')).toHaveLength(2));

  await ask('作者是谁');

  await waitFor(() => expect(axios.post).toHaveBeenCalledTimes(3));
  const history = lastPayload().history;
  expect(history.length).toBeGreaterThan(0);
  expect(history.every((turn) => turn.content)).toBe(true);
});

test('quoting a book focuses the next question on it', async () => {
  openAssistant();
  await ask('沙陀往事讲了什么');
  await screen.findByText('它讲沙陀人的历史。');

  const quoteButton = screen.getByTestId('assistant-quote-book');
  expect(quoteButton.querySelector('img')).toHaveAttribute(
    'src',
    '/images/icon/quote.gif'
  );
  fireEvent.click(quoteButton);

  const quote = screen.getByTestId('assistant-quote');
  expect(quote).toHaveTextContent('沙陀往事');

  await ask('这本书的作者是谁');

  await waitFor(() =>
    expect(screen.getAllByTestId('assistant-message-assistant')).toHaveLength(2)
  );
  expect(lastPayload().query).toBe('这本书的作者是谁');
  expect(lastPayload().focus_book_id).toBe(2300);
  expect(screen.queryByTestId('assistant-quote')).toBeNull();

  // The quoted answer is about one book, so the book list is redundant.
  const assistants = screen.getAllByTestId('assistant-message-assistant');
  const focused = assistants.at(-1);
  expect(focused.querySelector('[data-testid="assistant-book"]')).toBeNull();
  expect(screen.getAllByTestId('assistant-book')).toHaveLength(1);
});

test('a quoted author question still lists the works', async () => {
  axios.post
    .mockResolvedValueOnce({ data: answer })
    .mockResolvedValueOnce({
      data: {
        ...answer,
        scope: 'author',
        answer: '邓云乡还写过《红楼风俗谭》。',
        sources: [
          {
            book_id: 2301,
            title: '红楼风俗谭',
            title_cn: '红楼风俗谭',
            authors: ['邓云乡'],
            thumb_image: '',
          },
        ],
      },
    });
  openAssistant();

  await ask('沙陀往事讲了什么');
  await screen.findByText('它讲沙陀人的历史。');
  fireEvent.click(screen.getByTestId('assistant-quote-book'));
  await ask('该作者还有哪些书');

  await waitFor(() =>
    expect(screen.getAllByTestId('assistant-message-assistant')).toHaveLength(2)
  );
  const focused = screen.getAllByTestId('assistant-message-assistant').at(-1);
  expect(focused.querySelector('[data-testid="assistant-book"]')).not.toBeNull();
});

test('a quote can be removed before sending', async () => {
  openAssistant();
  await ask('沙陀往事讲了什么');
  await screen.findByText('它讲沙陀人的历史。');
  fireEvent.click(screen.getByTestId('assistant-quote-book'));

  fireEvent.click(screen.getByTestId('assistant-quote-remove'));
  await ask('这本书的作者是谁');

  await waitFor(() => expect(axios.post).toHaveBeenCalledTimes(2));
  expect(lastPayload()).not.toHaveProperty('focus_book_id');
});

test('has_more offers a button that asks for more', async () => {
  axios.post.mockResolvedValue({ data: { ...answer, has_more: true } });
  openAssistant();

  await ask('沙陀往事讲了什么');
  const moreButton = await screen.findByTestId('assistant-more');

  fireEvent.click(moreButton);

  await waitFor(() => expect(axios.post).toHaveBeenCalledTimes(2));
  expect(lastPayload().query).not.toBe('沙陀往事讲了什么');
});

test('says there are no more books when the pool is exhausted', async () => {
  axios.post.mockResolvedValueOnce({
    data: { ...noAnswer, reason: 'no_more' },
  });
  openAssistant();

  await ask('还有么？');

  const bubble = await screen.findByTestId('assistant-message-no-answer');
  expect(bubble).toHaveTextContent(/no more|沒有更多|没有更多/i);
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
