import { useCallback, useState } from 'react';
import axios from 'axios';
import { useTranslation } from 'react-i18next';

import { API_BASE_URL } from '../Config';

// The API schema caps `history` at 20 turns (40 messages). This is a payload
// boundary, not the server's rewrite window — the backend decides how many
// turns it actually feeds the rewriter.
const MAX_PAYLOAD_MESSAGES = 40;

export default function useAssistantChat() {
  const { t } = useTranslation();
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(false);

  const send = useCallback(
    async (question) => {
      const query = (question || '').trim();
      if (!query || loading) return;

      // Failed and "not found" turns carry no content worth remembering; the
      // question they answered is dropped too, so the rewriter never sees a
      // dangling user turn.
      const history = [];
      for (const message of messages) {
        if (message.role === 'user') {
          history.push({ role: 'user', content: message.content });
        } else if (!message.error && !message.noAnswer) {
          history.push({ role: 'assistant', content: message.content });
        } else {
          history.pop();
        }
      }
      history.splice(0, Math.max(0, history.length - MAX_PAYLOAD_MESSAGES));

      setMessages((current) => [...current, { role: 'user', content: query }]);
      setLoading(true);
      try {
        const { data } = await axios.post(`${API_BASE_URL}/rag/answer`, {
          query,
          top_k: 5,
          history,
        });
        setMessages((current) => [
          ...current,
          {
            role: 'assistant',
            content: data.no_answer ? t('assistant.noAnswer') : data.answer,
            sources: data.sources?.length ? data.sources : null,
            noAnswer: Boolean(data.no_answer),
            rewritten: data.rewritten_query || '',
          },
        ]);
      } catch (error) {
        const status = error?.response?.status;
        setMessages((current) => [
          ...current,
          {
            role: 'assistant',
            content:
              status === 503
                ? t('assistant.errorUnavailable')
                : t('assistant.errorNetwork'),
            error: true,
          },
        ]);
      } finally {
        setLoading(false);
      }
    },
    [loading, messages, t]
  );

  const reset = useCallback(() => setMessages([]), []);

  return { messages, loading, send, reset };
}
