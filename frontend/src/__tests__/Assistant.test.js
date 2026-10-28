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

test('shows for anonymous visitors on public pages', () => {
  renderAssistant({ isAuthenticated: false });

  expect(screen.getByTestId('assistant-trigger')).toBeInTheDocument();
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

test('creates a book draft and commits it', async () => {
  const draft = {
    action: 'create_book',
    book_id: null,
    isbn: '9787020002207',
    fields: {
      isbn: '9787020002207',
      title_cn: '红楼梦',
      title: '红楼梦',
      author_names: ['曹雪芹'],
      publisher_name: '人民文学出版社',
      publish_date: '1982-03-01',
      pages: 771,
      price: 59.7,
      thumb_image: '',
    },
    sources: { title_cn: 'douban', price: 'dangdang' },
    missing: ['translator'],
    conflicts: {},
    existing: null,
    updated_at: null,
    current: {},
    message: '',
  };
  axios.post.mockImplementation((url) => {
    if (String(url).endsWith('/assistant/actions')) {
      return Promise.resolve({
        data: { intent: 'create_book', draft, candidates: [], message: '' },
      });
    }
    if (String(url).endsWith('/assistant/commit')) {
      return Promise.resolve({ data: { status: 'created', book_id: 42 } });
    }
    return Promise.resolve({ data: answer });
  });

  openAssistant();
  await ask('录入 9787020002207');

  expect(await screen.findByTestId('assistant-draft')).toBeInTheDocument();
  fireEvent.change(screen.getByTestId('draft-field-publisher_name'), {
    target: { value: '中华书局' },
  });
  fireEvent.click(screen.getByTestId('draft-confirm'));

  await waitFor(() =>
    expect(
      axios.post.mock.calls.some(([url]) =>
        String(url).endsWith('/assistant/commit')
      )
    ).toBe(true)
  );
  const commit = axios.post.mock.calls.find(([url]) =>
    String(url).endsWith('/assistant/commit')
  );
  expect(commit[1].draft.fields.publisher_name).toBe('中华书局');
  expect(
    await screen.findByText(i18n.t('assistant.bookCreated', { title: '红楼梦' }))
  ).toBeInTheDocument();
});

test('treats “加入书库” plus an ISBN as an action', async () => {
  const draft = {
    action: 'create_book',
    book_id: null,
    isbn: '9787020002207',
    fields: {
      isbn: '9787020002207',
      title_cn: '红楼梦',
      title: '红楼梦',
    },
    sources: {},
    missing: [],
    conflicts: {},
    existing: null,
    updated_at: null,
    current: {},
    message: '',
  };
  axios.post.mockImplementation((url) => {
    if (String(url).endsWith('/assistant/actions')) {
      return Promise.resolve({
        data: { intent: 'create_book', draft, candidates: [], message: '' },
      });
    }
    return Promise.resolve({ data: answer });
  });

  openAssistant();
  await ask('把这本书加入书库：9787020002207');

  await waitFor(() =>
    expect(
      axios.post.mock.calls.some(([url]) =>
        String(url).endsWith('/assistant/actions')
      )
    ).toBe(true)
  );
  expect(await screen.findByTestId('assistant-draft')).toBeInTheDocument();
});

test('rebuilds a stale edit draft after a 409', async () => {
  const updateDraft = {
    action: 'update_book',
    book_id: 7,
    isbn: '9787020002207',
    fields: { publisher_name: '中华书局' },
    sources: { publisher_name: 'user' },
    missing: [],
    conflicts: {},
    existing: null,
    updated_at: '2026-01-01T00:00:00',
    current: { title_cn: '三体', title: '三体' },
    diff: [{ field: 'publisher_name', current: '旧社', value: '中华书局' }],
    message: '',
  };
  const refreshed = {
    ...updateDraft,
    updated_at: '2026-02-02T00:00:00',
    current: { title_cn: '三体（新版）', title: '三体（新版）' },
  };
  axios.post.mockImplementation((url, body) => {
    if (String(url).endsWith('/assistant/actions')) {
      if (body?.action === 'refresh_draft') {
        return Promise.resolve({
          data: { intent: 'update_book', draft: refreshed, candidates: [] },
        });
      }
      return Promise.resolve({
        data: { intent: 'update_book', draft: updateDraft, candidates: [] },
      });
    }
    if (String(url).endsWith('/assistant/commit')) {
      return Promise.reject({
        response: { status: 409, data: { detail: 'Book changed' } },
      });
    }
    return Promise.resolve({ data: answer });
  });

  openAssistant();
  await ask('修改《三体》的出版社');
  await screen.findByTestId('draft-confirm');
  fireEvent.click(screen.getByTestId('draft-confirm'));

  await waitFor(() =>
    expect(
      axios.post.mock.calls.some(
        ([url, body]) =>
          String(url).endsWith('/assistant/actions') &&
          body?.action === 'refresh_draft'
      )
    ).toBe(true)
  );
  expect(
    await screen.findByText(i18n.t('assistant.draftRefreshed'))
  ).toBeInTheDocument();
  expect(await screen.findByDisplayValue('三体（新版）')).toBeInTheDocument();
});

test('fills an update draft from metadata', async () => {
  const updateDraft = {
    action: 'update_book',
    book_id: 7,
    isbn: '9787020002207',
    fields: { publisher_name: '中华书局' },
    sources: { publisher_name: 'user' },
    missing: [],
    conflicts: {},
    existing: null,
    updated_at: '2026-01-01T00:00:00',
    current: {
      title_cn: '三体',
      title: '三体',
      publisher_name: '人民文学出版社',
    },
    diff: [
      {
        field: 'publisher_name',
        current: '人民文学出版社',
        value: '中华书局',
      },
    ],
    message: '',
  };
  const filledDraft = {
    ...updateDraft,
    fields: { price: 59.0 },
    sources: { price: 'dangdang' },
    diff: [{ field: 'price', current: '', value: 59.0 }],
    message: '将补全 1 个字段，确认后写入。',
  };
  axios.post.mockImplementation((url, body) => {
    if (String(url).endsWith('/assistant/actions')) {
      if (body?.action === 'fill_missing') {
        return Promise.resolve({
          data: { intent: 'update_book', draft: filledDraft, candidates: [] },
        });
      }
      return Promise.resolve({
        data: { intent: 'update_book', draft: updateDraft, candidates: [] },
      });
    }
    return Promise.resolve({ data: answer });
  });

  openAssistant();
  await ask('修改《三体》的出版社');
  await screen.findByTestId('assistant-draft');
  fireEvent.click(screen.getByTestId('draft-fill'));

  await waitFor(() =>
    expect(
      axios.post.mock.calls.some(
        ([url, body]) =>
          String(url).endsWith('/assistant/actions') &&
          body?.action === 'fill_missing'
      )
    ).toBe(true)
  );
  expect(await screen.findByTestId('draft-field-price')).toHaveValue(59);
});

test('leaves page-scoped count questions to the page context', async () => {
  openAssistant({ path: '/my-library/authors/7' });

  await ask('这个作者有多少本书');

  await waitFor(() => expect(axios.post).toHaveBeenCalled());
  expect(
    axios.post.mock.calls.some(([url]) => String(url).endsWith('/rag/answer'))
  ).toBe(true);
});

test('explains that anonymous users cannot write', async () => {
  openAssistant({ isAuthenticated: false });

  await ask('录入 9787020002207');

  expect(
    await screen.findByText(i18n.t('assistant.actionLoginRequired'))
  ).toBeInTheDocument();
  expect(axios.post).not.toHaveBeenCalled();
});

test('answers a statistics intent with a table', async () => {
  axios.post.mockResolvedValue({
    data: {
      ...answer,
      answer: '书库共 3 本书。',
      sources: [],
      stats: {
        table: {
          columns: ['项目', '数值'],
          rows: [['藏书', 3]],
        },
      },
    },
  });

  openAssistant();
  await ask('我的藏书有多少本');

  expect(await screen.findByText('书库共 3 本书。')).toBeInTheDocument();
  expect(screen.getByText('藏书')).toBeInTheDocument();
  expect(screen.getByText('3')).toBeInTheDocument();
  expect(
    axios.post.mock.calls.some(([url]) => String(url).endsWith('/rag/answer'))
  ).toBe(true);
});

test('lists statistics books with the normal quote actions', async () => {
  axios.post.mockResolvedValue({
    data: {
      ...answer,
      answer: '2010-09 买了 1 本书。',
      sources: [
        {
          book_id: 2300,
          title: '沙陀往事',
          title_cn: '沙陀往事',
          authors: ['樊文礼'],
          thumb_image: '/images/books/1.webp',
        },
      ],
      stats: { table: { columns: [], rows: [] } },
    },
  });

  openAssistant();
  await ask('上个月买了什么书');

  expect(await screen.findByTestId('assistant-book')).toBeInTheDocument();
  expect(screen.getByTestId('assistant-quote-book')).toBeInTheDocument();
});

test('truncates long turns so history stays within the API limit', async () => {
  const longAnswer = 'x'.repeat(5000);
  let call = 0;
  axios.post.mockImplementation(() => {
    call += 1;
    return Promise.resolve({
      data: call === 1 ? { ...answer, answer: longAnswer } : answer,
    });
  });

  openAssistant();
  await ask('第一个问题');
  await waitFor(() => expect(axios.post).toHaveBeenCalledTimes(1));
  await ask('第二个问题');
  await waitFor(() => expect(axios.post).toHaveBeenCalledTimes(2));

  const history = axios.post.mock.calls[1][1].history;
  const assistant = history.find((turn) => turn.role === 'assistant');
  expect(assistant.content.length).toBe(4000);
});

test('knows which detail page the user is on', async () => {
  openAssistant({ path: '/my-library/authors/7' });

  expect(screen.getByTestId('assistant-context')).toHaveTextContent(
    i18n.t('assistant.page_author')
  );

  await ask('介绍一下这个作者');

  await waitFor(() =>
    expect(lastPayload().page_context).toEqual({ kind: 'author', id: 7 })
  );

  // After the first turn the conversation, not the page, drives answers.
  await ask('红楼梦的作者是谁？');
  await waitFor(() => expect(axios.post).toHaveBeenCalledTimes(2));
  expect(lastPayload().page_context).toBeUndefined();
  expect(screen.getByTestId('assistant-context')).toHaveTextContent(
    i18n.t('assistant.pageAwareUsed', { type: i18n.t('assistant.page_author') })
  );

  // A new chat gives the page context another first turn.
  fireEvent.click(screen.getByTestId('assistant-new'));
  await ask('介绍一下这个作者');
  await waitFor(() => expect(axios.post).toHaveBeenCalledTimes(3));
  expect(lastPayload().page_context).toEqual({ kind: 'author', id: 7 });
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
