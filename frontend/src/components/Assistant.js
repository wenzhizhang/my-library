import React, { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';

import { useAuth } from '../AuthContext';
import { useTranslation } from 'react-i18next';

import { isAuthPath } from '../authRoutes';
import MessageList from './assistant/MessageList';
import useAssistantChat from './assistant/useAssistantChat';
import useSleepingFox from '../hooks/useSleepingFox';
import './Assistant.css';

// Animated fox in three sizes. The trigger is the smallest element, the
// dialog avatar is the largest, and every <img> gets a 2x source.
const FOX_1X = '/images/logo/fox_180p.gif';
const FOX_2X = '/images/logo/fox_360p.gif';
const FOX_3X = '/images/logo/fox_720p.gif';
const FOX_SLEEP_1X = '/images/logo/fox_sleep_180x240_256.webp';
const FOX_SLEEP_2X = '/images/logo/fox_sleep_360x480_256.webp';
const FOX_SLEEP_3X = '/images/logo/fox_sleep_720x960_256.webp';

export default function Assistant() {
  const { isAuthenticated, isGuest } = useAuth();
  const location = useLocation();
  const { t } = useTranslation();
  const { messages, loading, send, reset } = useAssistantChat();
  const { sleeping, touch } = useSleepingFox();
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

  const onAuthPage = isAuthPath(location.pathname);
  if (onAuthPage || (!isAuthenticated && !isGuest)) return null;

  const submit = (question) => {
    const query = (question ?? input).trim();
    if (!query || loading) return;
    touch();
    setInput('');
    send(query, quotedBook ? { focusBookId: quotedBook.book_id } : undefined);
    setQuotedBook(null);
  };

  const quoteBook = (book) => {
    touch();
    setQuotedBook(book);
    inputRef.current?.focus();
  };

  const fillExample = (example) => {
    touch();
    setInput(example);
    inputRef.current?.focus();
  };

  const newChat = () => {
    touch();
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
        onClick={() => {
          touch();
          setOpen(true);
        }}
        data-testid="assistant-trigger"
      >
        <img
          className={`assistant-trigger-img${sleeping ? ' is-sleeping' : ''}`}
          src={sleeping ? FOX_SLEEP_1X : FOX_1X}
          srcSet={
            sleeping
              ? `${FOX_SLEEP_1X} 1x, ${FOX_SLEEP_2X} 2x`
              : `${FOX_1X} 1x, ${FOX_2X} 2x`
          }
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
          className={`assistant-avatar${sleeping ? ' is-sleeping' : ''}`}
          src={sleeping ? FOX_SLEEP_2X : FOX_2X}
          srcSet={
            sleeping
              ? `${FOX_SLEEP_2X} 1x, ${FOX_SLEEP_3X} 2x`
              : `${FOX_2X} 1x, ${FOX_3X} 2x`
          }
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
              onChange={(event) => {
                touch();
                setInput(event.target.value);
              }}
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
