import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';

import i18n from '../i18n';
import DraftCard from '../components/assistant/DraftCard';

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
  conflicts: { publisher_name: { douban: '人民文学出版社', dangdang: '人民文学' } },
  existing: null,
  updated_at: null,
  current: {},
  message: '',
};

function renderCard(overrides = {}) {
  const handlers = {
    onChange: jest.fn(),
    onCommit: jest.fn(),
    onCancel: jest.fn(),
    onChooseCandidate: jest.fn(),
    onFillMissing: jest.fn(),
  };
  render(<DraftCard draft={{ ...draft, ...overrides }} {...handlers} />);
  return handlers;
}

test('renders fields with sources and missing markers', () => {
  renderCard();

  expect(screen.getByTestId('assistant-draft')).toBeInTheDocument();
  expect(screen.getByTestId('draft-field-title_cn')).toHaveValue('红楼梦');
  expect(screen.getByTestId('draft-field-publisher_name')).toHaveValue('人民文学出版社');
  expect(screen.getByText(i18n.t('assistant.source_douban'))).toBeInTheDocument();
  expect(screen.getByText(/translator/)).toBeInTheDocument();
  expect(
    screen.getByText(new RegExp(i18n.t('assistant.draftConflict')))
  ).toBeInTheDocument();
});

test('edits a field and confirms the draft', () => {
  const { onChange, onCommit } = renderCard();

  fireEvent.change(screen.getByTestId('draft-field-publisher_name'), {
    target: { value: '中华书局' },
  });
  expect(onChange).toHaveBeenCalledWith('publisher_name', '中华书局');

  fireEvent.click(screen.getByTestId('draft-confirm'));
  expect(onCommit).toHaveBeenCalledWith();
});

test('existing ISBN offers update, add and abandon', () => {
  const { onCommit } = renderCard({
    existing: { book_id: 7, title_cn: '已有的书' },
  });

  expect(screen.getByText(/已有的书/)).toBeInTheDocument();
  expect(
    screen.getByText(i18n.t('assistant.draftViewExisting')).closest('a')
  ).toHaveAttribute('href', '/my-library/books/7');
  fireEvent.click(screen.getByTestId('draft-update'));
  fireEvent.click(screen.getByTestId('draft-add'));
  fireEvent.click(screen.getByTestId('draft-abandon'));

  expect(onCommit.mock.calls).toEqual([['update'], ['add'], ['abandon']]);
});

test('update draft shows current values and enables confirm', () => {
  const { onCommit, onFillMissing } = renderCard({
    action: 'update_book',
    fields: { publisher_name: '中华书局' },
    sources: { publisher_name: 'user' },
    current: {
      title_cn: '旧书名',
      title: '旧书名',
      author_names: ['曹雪芹'],
      publisher_name: '人民文学出版社',
    },
    diff: [
      {
        field: 'publisher_name',
        current: '人民文学出版社',
        value: '中华书局',
      },
    ],
  });

  expect(screen.getByTestId('draft-field-title_cn')).toHaveValue('旧书名');
  expect(screen.getByTestId('draft-field-publisher_name')).toHaveValue('中华书局');
  expect(screen.getByTestId('draft-field-publisher_name')).toHaveClass('is-changed');
  expect(screen.getByTestId('draft-diff')).toHaveTextContent('中华书局');
  fireEvent.click(screen.getByTestId('draft-fill'));
  expect(onFillMissing).toHaveBeenCalled();
  fireEvent.click(screen.getByTestId('draft-confirm'));
  expect(onCommit).toHaveBeenCalledWith();
});

test('candidate list selects a book', () => {
  const onChooseCandidate = jest.fn();
  render(
    <DraftCard
      draft={draft}
      candidates={[{ book_id: 7, title_cn: '红楼梦' }]}
      onChange={jest.fn()}
      onCommit={jest.fn()}
      onCancel={jest.fn()}
      onChooseCandidate={onChooseCandidate}
    />
  );

  fireEvent.click(screen.getByRole('button', { name: '红楼梦' }));
  expect(onChooseCandidate).toHaveBeenCalledWith(7);
});
