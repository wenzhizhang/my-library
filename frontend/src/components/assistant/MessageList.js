import React from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

import { LIBRARY_PATH } from '../../config';
import { MEDIA_BASE_URL } from '../Config';

const QUOTE_ICON = '/images/icon/quote.gif';
const AUTHOR_SCOPE = 'author';

function showsBookList(message) {
  // A quoted single-book answer is about one book: the list is redundant and
  // hidden. A quoted author answer expanded to their works, so it stays.
  return (
    message.books?.length > 0 &&
    (!message.focused || message.scope === AUTHOR_SCOPE)
  );
}

function messageTestId(message) {
  if (message.error) return 'assistant-message-error';
  if (message.noAnswer) return 'assistant-message-no-answer';
  return `assistant-message-${message.role}`;
}

function thumbUrl(path) {
  return `${MEDIA_BASE_URL}/${String(path).replace(/^\/+/, '')}`;
}

function BookRow({ book, onNavigate, onQuote }) {
  const { t } = useTranslation();

  return (
    <li className="assistant-book-row">
      <Link
        className="assistant-book"
        to={`${LIBRARY_PATH}/books/${book.book_id}`}
        onClick={onNavigate}
        data-testid="assistant-book"
      >
        {book.thumb_image ? (
          <img
            className="assistant-book-thumb"
            src={thumbUrl(book.thumb_image)}
            alt=""
            loading="lazy"
          />
        ) : (
          <span
            className="assistant-book-thumb assistant-book-thumb-empty"
            aria-hidden="true"
          />
        )}
        <span className="assistant-book-info">
          <span className="assistant-book-title">
            {book.title_cn || book.title || `#${book.book_id}`}
          </span>
          {book.authors?.length > 0 && (
            <span className="assistant-book-authors">
              {book.authors.join(' / ')}
            </span>
          )}
        </span>
        <span className="assistant-book-arrow" aria-hidden="true">
          ›
        </span>
      </Link>
      <button
        type="button"
        className="assistant-quote-button"
        onClick={() => onQuote(book)}
        aria-label={t('assistant.quote')}
        title={t('assistant.quote')}
        data-testid="assistant-quote-book"
      >
        <img
          className="assistant-quote-icon"
          src={QUOTE_ICON}
          alt=""
          aria-hidden="true"
        />
      </button>
    </li>
  );
}

export default function MessageList({
  messages,
  loading,
  listRef,
  onNavigate,
  onExample,
  onMore,
  onQuote,
}) {
  const { t } = useTranslation();

  return (
    <div className="assistant-messages" ref={listRef}>
      {messages.length === 0 && (
        <div className="assistant-empty">
          <p className="assistant-greeting" data-testid="assistant-greeting">
            {t('assistant.greeting')}
          </p>
          <p className="assistant-intro" data-testid="assistant-intro">
            {t('assistant.intro')}
          </p>
          <p className="assistant-empty-label">{t('assistant.empty')}</p>
          {[1, 2, 3].map((index) => {
            const example = t(`assistant.example${index}`);
            return (
              <button
                type="button"
                key={index}
                className="assistant-example"
                onClick={() => onExample(example)}
                data-testid="assistant-example"
              >
                {example}
              </button>
            );
          })}
        </div>
      )}

      {messages.map((message, index) => (
        <div
          key={index}
          className={`assistant-message assistant-${message.role}${
            message.error ? ' is-error' : ''
          }${message.noAnswer ? ' is-empty-answer' : ''}`}
          data-testid={messageTestId(message)}
        >
          {message.content && !message.isMore && (
            <div className="assistant-bubble">{message.content}</div>
          )}

          {message.rewritten && (
            <p className="assistant-rewritten" data-testid="assistant-rewritten">
              {t('assistant.rewritten', { query: message.rewritten })}
            </p>
          )}

          {message.statsTable?.rows?.length > 0 && (
            <div className="assistant-stats">
              <table>
                <thead>
                  <tr>
                    {message.statsTable.columns.map((column) => (
                      <th key={column}>{column}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {message.statsTable.rows.map((row, index) => (
                    <tr key={index}>
                      {row.map((value, cell) => (
                        <td key={cell}>{value}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {showsBookList(message) && (
            <div className="assistant-books">
              <span className="assistant-books-label">
                {message.isMore ? t('assistant.moreBooks') : t('assistant.books')}
              </span>
              <ul>
                {message.books.map((book) => (
                  <BookRow
                    key={book.book_id}
                    book={book}
                    onNavigate={onNavigate}
                    onQuote={onQuote}
                  />
                ))}
              </ul>
            </div>
          )}

          {message.hasMore && (
            <button
              type="button"
              className="assistant-more"
              onClick={onMore}
              data-testid="assistant-more"
            >
              {t('assistant.moreCta')}
            </button>
          )}
        </div>
      ))}

      {loading && (
        <div
          className="assistant-message assistant-assistant"
          data-testid="assistant-loading"
        >
          <div
            className="assistant-bubble assistant-typing"
            aria-label={t('assistant.thinking')}
          >
            <span />
            <span />
            <span />
          </div>
        </div>
      )}
    </div>
  );
}
