import React, { useEffect, useRef, useState } from 'react';

import { useAuth } from '../AuthContext';
import { useTranslation } from 'react-i18next';

import MessageList from './assistant/MessageList';
import useAssistantChat from './assistant/useAssistantChat';
import './Assistant.css';

function SparkleIcon() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M12 3.2l1.9 4.6a4 4 0 0 0 2.3 2.3l4.6 1.9-4.6 1.9a4 4 0 0 0-2.3 2.3L12 20.8l-1.9-4.6a4 4 0 0 0-2.3-2.3L3.2 12l4.6-1.9a4 4 0 0 0 2.3-2.3L12 3.2z"
        fill="currentColor"
      />
      <circle cx="19" cy="5" r="1.4" fill="currentColor" opacity="0.75" />
    </svg>
  );
}

export default function Assistant() {
  const { isAuthenticated } = useAuth();
  const { t } = useTranslation();
  const { messages, loading, send, reset } = useAssistantChat();
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState('');
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
    send(query);
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
        <SparkleIcon />
      </button>

      <section
        className={`assistant-panel${openClass}`}
        role="dialog"
        aria-label={t('assistant.title')}
        aria-hidden={!open}
        data-testid="assistant-panel"
      >
        <header className="assistant-header">
          <div className="assistant-title">
            <SparkleIcon />
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
        />

        <form
          className="assistant-input"
          onSubmit={(event) => {
            event.preventDefault();
            submit();
          }}
        >
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
      </section>
    </>
  );
}
