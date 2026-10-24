import React from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

import { LIBRARY_PATH } from '../../config';

function messageTestId(message) {
  if (message.error) return 'assistant-message-error';
  if (message.noAnswer) return 'assistant-message-no-answer';
  return `assistant-message-${message.role}`;
}

export default function MessageList({
  messages,
  loading,
  listRef,
  onNavigate,
  onExample,
}) {
  const { t } = useTranslation();

  return (
    <div className="assistant-messages" ref={listRef}>
      {messages.length === 0 && (
        <div className="assistant-empty">
          <p>{t('assistant.empty')}</p>
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
          <div className="assistant-bubble">{message.content}</div>
          {message.rewritten && (
            <p className="assistant-rewritten" data-testid="assistant-rewritten">
              {t('assistant.rewritten', { query: message.rewritten })}
            </p>
          )}
          {message.sources && (
            <div className="assistant-sources">
              <span className="assistant-sources-label">
                {t('assistant.sources')}
              </span>
              {message.sources.map((source) => (
                <Link
                  key={source.book_id}
                  className="assistant-source"
                  to={`${LIBRARY_PATH}/books/${source.book_id}`}
                  onClick={onNavigate}
                  data-testid="assistant-source"
                >
                  {source.title_cn || source.title || `#${source.book_id}`}
                </Link>
              ))}
            </div>
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
