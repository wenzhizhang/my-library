import { useCallback, useState } from 'react';
import axios from 'axios';
import { useTranslation } from 'react-i18next';

import { API_BASE_URL } from '../Config';

// The API schema caps `history` at 20 turns (40 messages). This is a payload
// boundary, not the server's rewrite window — the backend decides how many
// turns it actually feeds the rewriter.
const MAX_PAYLOAD_MESSAGES = 40;
// Mirrors backend RAG_TURN_CONTENT_LIMIT.
const MAX_TURN_CHARS = 4000;

// Keep in sync with backend/services/assistant_actions.py.
const ACTION_WORDS = /(录入|新增|添加|加入|收录|入库|建档|修改|更正|更新|编辑|补全|完善)/;
const FIELD_CHANGE = /(改成|改为|换成|设为|修改为|更新为)/;
const ISBN_RE = /(97[89][\d\s-]{9,17}\d|\b\d{9}[\dXx]\b)/;

function looksLikeAction(query, focusBookId) {
  if (ISBN_RE.test(query) && ACTION_WORDS.test(query)) return true;
  if (focusBookId && (ACTION_WORDS.test(query) || FIELD_CHANGE.test(query))) {
    return true;
  }
  if (!ACTION_WORDS.test(query)) return false;
  if (/《[^》]+》/.test(query)) return true;
  // Edit-by-title: "修改三体的出版社".
  return /(修改|更正|更新|编辑)/.test(query) || /这本书|该书/.test(query);
}

export default function useAssistantChat(
  pageContext = null,
  onContextUsed = null,
  canWrite = true
) {
  const { t } = useTranslation();
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(false);
  const [draft, setDraft] = useState(null);
  const [candidates, setCandidates] = useState([]);
  const [committing, setCommitting] = useState(false);

  const append = useCallback((...items) => {
    setMessages((current) => [...current, ...items]);
  }, []);

  const send = useCallback(
    async (question, { focusBookId } = {}) => {
      const query = (question || '').trim();
      if (!query || loading) return;

      const actionLike = Boolean(draft) || looksLikeAction(query, focusBookId);
      if (actionLike && !canWrite) {
        append({ role: 'user', content: query, action: true });
        append({
          role: 'assistant',
          content: t('assistant.actionLoginRequired'),
          action: true,
        });
        return;
      }

      // Page context is a first-turn nudge only: once the conversation starts,
      // history takes over. The parent clears it after this call.
      onContextUsed?.();

      if (actionLike) {
        setLoading(true);
        try {
          const { data } = await axios.post(`${API_BASE_URL}/assistant/actions`, {
            message: query,
            draft: draft || null,
            ...(focusBookId ? { focus_book_id: focusBookId } : {}),
            ...(pageContext ? { page_context: pageContext } : {}),
          });
          if (data.intent !== 'none') {
            append({ role: 'user', content: query, action: true });
            if (data.intent === 'revise_draft') {
              append({
                role: 'assistant',
                content: t('assistant.draftUpdated'),
                action: true,
              });
            } else if (data.message) {
              append({
                role: 'assistant',
                content: data.message,
                action: true,
              });
            }
            setDraft(data.draft || null);
            setCandidates(data.candidates || []);
            return;
          }
        } catch (error) {
          append({
            role: 'assistant',
            content:
              error?.response?.status === 401
                ? t('assistant.actionLoginRequired')
                : t('assistant.draftFailed'),
            error: error?.response?.status !== 401,
            action: true,
          });
          return;
        } finally {
          setLoading(false);
        }
      }

      // Failed and "not found" turns carry no content worth remembering; the
      // question they answered is dropped too, so the rewriter never sees a
      // dangling user turn.
      const history = [];
      for (const message of messages) {
        if (message.action) continue;
        if (message.role === 'user') {
          history.push({
            role: 'user',
            content: (message.content || '').slice(0, MAX_TURN_CHARS),
          });
        } else if (!message.error && !message.noAnswer && message.content) {
          history.push({
            role: 'assistant',
            content: message.content.slice(0, MAX_TURN_CHARS),
          });
        } else {
          history.pop();
        }
      }
      history.splice(0, Math.max(0, history.length - MAX_PAYLOAD_MESSAGES));

      // Books already rendered, so "还有么" can continue after the last page.
      const seenBookIds = Array.from(
        new Set(
          messages.flatMap((message) =>
            (message.books || []).map((book) => book.book_id)
          )
        )
      );

      append({ role: 'user', content: query });
      setLoading(true);
      try {
        const { data } = await axios.post(`${API_BASE_URL}/rag/answer`, {
          query,
          top_k: 5,
          history,
          seen_book_ids: seenBookIds,
          ...(focusBookId ? { focus_book_id: focusBookId } : {}),
          ...(pageContext ? { page_context: pageContext } : {}),
        });
        const isMore = Boolean(data.more?.length);
        append({
          role: 'assistant',
          content: data.no_answer
            ? data.reason === 'no_more'
              ? t('assistant.noMore')
              : t('assistant.noAnswer')
            : data.answer,
          books: isMore ? data.more : data.sources,
          isMore,
          focused: Boolean(focusBookId) || data.scope === 'book',
          scope: data.scope || '',
          hasMore: Boolean(data.has_more),
          noAnswer: Boolean(data.no_answer),
          rewritten: data.rewritten_query || '',
          statsTable: data.stats?.table || null,
        });
      } catch (error) {
        const status = error?.response?.status;
        append({
          role: 'assistant',
          content:
            status === 503
              ? t('assistant.errorUnavailable')
              : t('assistant.errorNetwork'),
          error: true,
        });
      } finally {
        setLoading(false);
      }
    },
    [append, canWrite, draft, loading, messages, onContextUsed, pageContext, t]
  );

  const updateDraftField = useCallback((field, value) => {
    setDraft((current) => {
      if (!current) return current;
      const fields = { ...current.fields };
      let next = value;
      if (field === 'author_names') {
        next = String(value)
          .split(/[、,，/]/)
          .map((part) => part.trim())
          .filter(Boolean);
      }
      if (value === '' || (Array.isArray(next) && next.length === 0)) {
        delete fields[field];
      } else {
        fields[field] = next;
      }
      const missing = (current.missing || []).filter((name) => name !== field);
      if (!(field in fields)) missing.push(field);
      const base = current.current || {};
      const diff = Object.entries(fields).map(([name, value]) => ({
        field: name,
        current: base[name] ?? '',
        value: Array.isArray(value) ? value.join('、') : value,
      }));
      return {
        ...current,
        fields,
        missing: Array.from(new Set(missing)),
        diff,
      };
    });
  }, []);

  const commitDraft = useCallback(
    async (duplicateChoice) => {
      if (!draft || committing) return;
      setCommitting(true);
      const title =
        draft.fields?.title_cn ||
        draft.current?.title_cn ||
        draft.fields?.title ||
        draft.current?.title ||
        '';
      try {
        const { data } = await axios.post(`${API_BASE_URL}/assistant/commit`, {
          draft,
          duplicate_choice: duplicateChoice || null,
        });
        setDraft(null);
        setCandidates([]);
        if (data.status === 'abandoned') {
          append({ role: 'assistant', content: t('assistant.draftAbandoned') });
        } else {
          append({
            role: 'assistant',
            content: t(
              data.status === 'updated'
                ? 'assistant.bookUpdated'
                : 'assistant.bookCreated',
              { title }
            ),
          });
        }
      } catch (error) {
        if (error?.response?.status === 409 && draft.book_id) {
          try {
            const { data } = await axios.post(
              `${API_BASE_URL}/assistant/actions`,
              {
                action: 'refresh_draft',
                draft,
                focus_book_id: draft.book_id,
              }
            );
            setDraft(data.draft || null);
            append({
              role: 'assistant',
              content: t('assistant.draftRefreshed'),
              action: true,
            });
            return;
          } catch (refreshError) {
            // Fall through to the normal error message.
          }
        }
        if (error?.response?.status === 401) {
          append({
            role: 'assistant',
            content: t('assistant.actionLoginRequired'),
            action: true,
          });
          return;
        }
        const detail = error?.response?.data?.detail;
        const text =
          typeof detail === 'string'
            ? detail
            : detail?.message || t('assistant.draftFailed');
        append({ role: 'assistant', content: text, error: true });
      } finally {
        setCommitting(false);
      }
    },
    [append, committing, draft, t]
  );

  const fillMissing = useCallback(async () => {
    const bookId = draft?.book_id;
    if (!bookId || loading) return;
    setLoading(true);
    try {
      const { data } = await axios.post(`${API_BASE_URL}/assistant/actions`, {
        action: 'fill_missing',
        focus_book_id: bookId,
      });
      setDraft(data.draft || null);
      setCandidates(data.candidates || []);
      if (data.draft?.message) {
        append({
          role: 'assistant',
          content: data.draft.message,
          action: true,
        });
      }
    } catch (error) {
      append({
        role: 'assistant',
        content: t('assistant.draftFailed'),
        error: true,
      });
    } finally {
      setLoading(false);
    }
  }, [append, draft?.book_id, loading, t]);

  const cancelDraft = useCallback(() => {
    setDraft(null);
    setCandidates([]);
  }, []);

  const chooseCandidate = useCallback(
    async (bookId) => {
      setLoading(true);
      try {
        const { data } = await axios.post(`${API_BASE_URL}/assistant/actions`, {
          action: 'edit_book',
          focus_book_id: bookId,
        });
        setDraft(data.draft || null);
        setCandidates(data.candidates || []);
        if (data.draft?.message) {
          append({
            role: 'assistant',
            content: data.draft.message,
            action: true,
          });
        }
      } catch (error) {
        append({
          role: 'assistant',
          content: t('assistant.draftFailed'),
          error: true,
        });
      } finally {
        setLoading(false);
      }
    },
    [append, t]
  );

  const reset = useCallback(() => {
    setMessages([]);
    setDraft(null);
    setCandidates([]);
  }, []);

  return {
    messages,
    loading,
    draft,
    candidates,
    committing,
    send,
    commitDraft,
    cancelDraft,
    updateDraftField,
    chooseCandidate,
    fillMissing,
    reset,
  };
}
