import React, { useEffect, useRef, useState } from 'react';

import { useAuth } from '../AuthContext';
import { useTranslation } from 'react-i18next';

import MessageList from './assistant/MessageList';
import useAssistantChat from './assistant/useAssistantChat';
import './Assistant.css';

// 320px version of the source artwork: the avatar renders at 60px, so this
// stays sharp on 2x/3x screens without shipping the 1.6 MB original.
const ASSISTANT_LOGO = '/images/logo/assistant-320.png';

export default function Assistant() {
  const { isAuthenticated } = useAuth();
  const { t } = useTranslation();
  const { messages, loading, send, reset } = useAssistantChat();
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState('');
  const [quotedBook, setQuotedBook] = useState(null);
  const inputRef = useRef(null);
  const listRef = useRef(null);

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (event) => {
      if (event.key === 'Escape') setOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  useEffect(() => {
    const list = listRef.current;
    if (list) list.scrollTop = list.scrollHeight;
  }, [messages, loading]);

  if (!isAuthenticated) return null;

  const submit = (question) => {
    const query = (question ?? input).trim();
    if (!query || loading) return;
    setInput('');
    send(query, quotedBook ? { focusBookId: quotedBook.book_id } : undefined);
    setQuotedBook(null);
  };

  const quoteBook = (book) => {
    setQuotedBook(book);
    inputRef.current?.focus();
  };

  const fillExample = (example) => {
    setInput(example);
    inputRef.current?.focus();
  };

  const newChat = () => {
    reset();
    setInput('');
    inputRef.current?.focus();
  };

  const openClass = open ? ' is-open' : '';
  const onKeyDown = (event) => {
    // Enter sends, Shift+Enter breaks the line, and an IME composition Enter
    // must not send a half-typed Chinese question.
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      submit();
    }
  };

  return (
    <>
      <button
        type="button"
        className={`assistant-trigger${openClass}`}
        aria-label={t('assistant.open')}
        aria-expanded={open}
        onClick={() => setOpen(true)}
        data-testid="assistant-trigger"
      >
        <img
          className="assistant-trigger-img"
          src={ASSISTANT_LOGO}
          alt=""
          aria-hidden="true"
        />
      </button>

      <section
        className={`assistant-panel${openClass}`}
        role="dialog"
        aria-label={t('assistant.title')}
        aria-hidden={!open}
        data-testid="assistant-panel"
      >
        <img
          className="assistant-avatar"
          src={ASSISTANT_LOGO}
          alt=""
          aria-hidden="true"
          data-testid="assistant-avatar"
        />

        <div className="assistant-body">
          <header className="assistant-header">
            <div className="assistant-title">
              <span>{t('assistant.title')}</span>
            </div>
            <div className="assistant-actions">
              <button
                type="button"
                className="assistant-action"
                onClick={newChat}
                data-testid="assistant-new"
              >
                {t('assistant.newChat')}
              </button>
              <button
                type="button"
                className="assistant-action assistant-close"
                onClick={() => setOpen(false)}
                aria-label={t('assistant.close')}
                data-testid="assistant-close"
              >
                ×
              </button>
            </div>
          </header>

          <MessageList
            messages={messages}
            loading={loading}
            listRef={listRef}
            onNavigate={() => setOpen(false)}
            onExample={fillExample}
            onMore={() => submit(t('assistant.moreRequest'))}
            onQuote={quoteBook}
          />

          <form
            className="assistant-input"
            onSubmit={(event) => {
              event.preventDefault();
              submit();
            }}
          >
            {quotedBook && (
              <div className="assistant-quote" data-testid="assistant-quote">
                <span className="assistant-quote-label">
                  {t('assistant.quoted')}
                </span>
                <span className="assistant-quote-title">
                  {quotedBook.title_cn || quotedBook.title}
                </span>
                <button
                  type="button"
                  className="assistant-quote-remove"
                  onClick={() => setQuotedBook(null)}
                  aria-label={t('assistant.removeQuote')}
                  data-testid="assistant-quote-remove"
                >
                  ×
                </button>
              </div>
            )}
            <textarea
              ref={inputRef}
              rows={2}
              value={input}
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={onKeyDown}
              placeholder={t('assistant.placeholder')}
              aria-label={t('assistant.placeholder')}
              data-testid="assistant-input"
            />
            <button
              type="submit"
              className="assistant-send"
              disabled={!input.trim() || loading}
              aria-label={t('assistant.send')}
              data-testid="assistant-send"
            >
              ↑
            </button>
          </form>
          <p className="assistant-disclaimer">{t('assistant.disclaimer')}</p>
        </div>
      </section>
    </>
  );
}
