import React from 'react';
import { useTranslation } from 'react-i18next';

import './CandidateList.css';

/** Book choices when a title matches more than one library record. */
export default function CandidateList({ candidates = [], onSelect, className = '' }) {
  const { t } = useTranslation();
  if (candidates.length === 0) return null;
  return (
    <div
      className={`assistant-candidates${className ? ` ${className}` : ''}`}
      data-testid="assistant-candidates"
    >
      <p>{t('assistant.draftCandidates')}</p>
      {candidates.map((candidate) => (
        <button
          key={candidate.book_id}
          type="button"
          onClick={() => onSelect(candidate.book_id)}
        >
          {candidate.title_cn || candidate.title}
        </button>
      ))}
    </div>
  );
}
