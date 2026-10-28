import React from 'react';
import { useTranslation } from 'react-i18next';

import { LIBRARY_PATH } from '../../config';
import CandidateList from './CandidateList';
import { MEDIA_BASE_URL } from '../Config';
import './DraftCard.css';

const FIELD_LABELS = {
  title_cn: 'bookForm.titleCn',
  title: 'bookForm.title',
  isbn: 'bookForm.isbn',
  author_names: 'bookForm.authors',
  publisher_name: 'bookForm.publisher',
  publish_date: 'bookForm.publishDate',
  pages: 'bookForm.pages',
  price: 'bookForm.price',
  binding_type: 'bookForm.binding',
  translator: 'bookForm.translator',
  language: 'bookForm.language',
  summary: 'bookForm.summary',
  introduction: 'bookForm.introduction',
  catalog: 'bookForm.catalog',
  thumb_image: 'bookForm.thumbImage',
  douban_score: 'bookForm.doubanScore',
  tags: 'bookForm.tags',
  purchase_price: 'bookForm.purchasePrice',
  purchase_date: 'bookForm.purchaseDate',
  purchase_store: 'bookForm.purchaseStore',
  read_state: 'bookForm.readState',
  bookshelf_name: 'bookForm.bookshelf',
  category_path: 'bookForm.category',
  brand_name: 'bookForm.brand',
  book_series_name: 'bookForm.series',
  paper_type: 'bookForm.paper',
  compose_type: 'bookForm.compose',
  book_count: 'bookForm.bookCount',
  edition: 'bookForm.edition',
  printing_info: 'bookForm.printing',
  printed_number: 'bookForm.printedNumber',
  registered: 'bookForm.registered',
  in_wish: 'bookForm.wishlist',
  link: 'bookForm.link',
};

function coverUrl(path) {
  if (!path) return '';
  if (path.startsWith('http') || path.startsWith('data:')) return path;
  return `${MEDIA_BASE_URL}/${path.replace(/^\/+/, '')}`;
}

export default function DraftCard({
  draft,
  candidates = [],
  committing = false,
  onChange,
  onCommit,
  onCancel,
  onChooseCandidate,
  onManual,
  onFillMissing,
}) {
  const { t } = useTranslation();
  if (!draft) return null;

  const fields = draft.fields || {};
  const missing = new Set(draft.missing || []);
  const sources = draft.sources || {};
  const existing = draft.existing;
  const isUpdate = draft.action === 'update_book';
  const current = draft.current || {};
  const title =
    fields.title_cn || fields.title || current.title_cn || current.title || '';
  const canCommit = isUpdate ? Object.keys(fields).length > 0 : Boolean(title);
  const labelFor = (name) => t(FIELD_LABELS[name] || name);

  const field = (name, label, { type = 'text', value } = {}) => (
    <label className="assistant-draft-field">
      <span className="assistant-draft-label">
        {label}
        {missing.has(name) && <em className="assistant-draft-required"> *</em>}
      </span>
      <input
        className={isUpdate && name in fields ? 'is-changed' : undefined}
        type={type}
        value={value ?? (fields[name] ?? (isUpdate ? current[name] ?? '' : ''))}
        onChange={(event) => onChange(name, event.target.value)}
        disabled={committing}
        data-testid={`draft-field-${name}`}
      />
      {sources[name] && (
        <i className={`assistant-draft-source is-${sources[name]}`}>
          {t(`assistant.source_${sources[name]}`, { defaultValue: sources[name] })}
        </i>
      )}
    </label>
  );

  return (
    <section className="assistant-draft" data-testid="assistant-draft">
      <header className="assistant-draft-header">
        <div>
          <strong>{isUpdate ? t('assistant.draftUpdateTitle') : t('assistant.draftTitle')}</strong>
          <span className="assistant-draft-isbn">{fields.isbn || draft.isbn}</span>
        </div>
        <button
          type="button"
          className="assistant-draft-close"
          onClick={onCancel}
          aria-label={t('assistant.draftCancel')}
          data-testid="draft-cancel"
        >
          ×
        </button>
      </header>

      <div className="assistant-draft-body">
        <div className="assistant-draft-cover">
          {fields.thumb_image ? (
            <img
              src={coverUrl(fields.thumb_image)}
              alt={title}
              onError={(event) => {
                event.currentTarget.style.visibility = 'hidden';
              }}
            />
          ) : (
            <div className="assistant-draft-cover-empty">📖</div>
          )}
          {sources.thumb_image && (
            <span className="assistant-draft-cover-source">
              {t(`assistant.source_${sources.thumb_image}`, { defaultValue: sources.thumb_image })}
            </span>
          )}
        </div>

        <div className="assistant-draft-fields">
          {field('title_cn', t('bookForm.titleCn'))}
          {field('author_names', t('bookForm.authors'), {
            value: fields.author_names
              ? fields.author_names.join('、')
              : isUpdate
                ? (current.author_names || []).join('、')
                : '',
          })}
          {field('publisher_name', t('bookForm.publisher'))}
          {field('publish_date', t('bookForm.publishDate'))}
          {field('pages', t('bookForm.pages'), { type: 'number' })}
          {field('price', t('bookForm.price'), { type: 'number' })}
          {field('binding_type', t('bookForm.binding'))}
          {field('isbn', t('bookForm.isbn'))}
        </div>
      </div>

      <details className="assistant-draft-more">
        <summary>{t('assistant.draftMoreFields')}</summary>
        <div className="assistant-draft-fields">
          {field('purchase_price', t('bookForm.purchasePrice'), { type: 'number' })}
          {field('purchase_date', t('bookForm.purchaseDate'))}
          {field('purchase_store', t('bookForm.purchaseStore'))}
          {field('read_state', t('bookForm.readState'))}
          {field('edition', t('bookForm.edition'))}
          {field('printing_info', t('bookForm.printing'))}
          {field('printed_number', t('bookForm.printedNumber'), { type: 'number' })}
          {field('book_count', t('bookForm.bookCount'), { type: 'number' })}
          {field('paper_type', t('bookForm.paper'))}
          {field('compose_type', t('bookForm.compose'))}
          {field('bookshelf_name', t('bookForm.bookshelf'))}
          {field('category_path', t('bookForm.category'))}
          {field('brand_name', t('bookForm.brand'))}
          {field('book_series_name', t('bookForm.series'))}
          {field('link', t('bookForm.link'))}
        </div>
      </details>

      {isUpdate && draft.diff?.length > 0 && (
        <div className="assistant-draft-diff" data-testid="draft-diff">
          <p>{t('assistant.draftDiff')}</p>
          <ul>
            {draft.diff.map((row) => (
              <li key={row.field}>
                <span>{labelFor(row.field)}</span>
                <s>{row.current === '' || row.current == null ? '—' : row.current}</s>
                <b>{row.value === '' || row.value == null ? '—' : row.value}</b>
              </li>
            ))}
          </ul>
        </div>
      )}

      {fields.summary && (
        <p className="assistant-draft-summary">{fields.summary}</p>
      )}

      {missing.size > 0 && (
        <p className="assistant-draft-missing">
          {t('assistant.draftMissing')}: {Array.from(missing).join(' / ')}
        </p>
      )}

      {draft.conflicts && Object.keys(draft.conflicts).length > 0 && (
        <p className="assistant-draft-conflict">
          {t('assistant.draftConflict')}: {Object.keys(draft.conflicts).join(' / ')}
        </p>
      )}

      {draft.message && (
        <p className="assistant-draft-hint">{draft.message}</p>
      )}

      {candidates.length > 0 && (
        <CandidateList
          candidates={candidates}
          onSelect={onChooseCandidate}
          className="assistant-draft-candidates"
        />
      )}

      {existing ? (
        <div className="assistant-draft-existing">
          <p>
            {t('assistant.draftExisting')}：
            <strong>{existing.title_cn || existing.title}</strong>
            {existing.book_id && (
              <a
                className="assistant-draft-link"
                href={`${LIBRARY_PATH}/books/${existing.book_id}`}
                target="_blank"
                rel="noreferrer"
              >
                {t('assistant.draftViewExisting')}
              </a>
            )}
          </p>
          <div className="assistant-draft-actions">
            <button
              type="button"
              className="is-primary"
              onClick={() => onCommit('update')}
              disabled={committing}
              data-testid="draft-update"
            >
              {t('assistant.draftUpdateExisting')}
            </button>
            <button
              type="button"
              onClick={() => onCommit('add')}
              disabled={committing}
              data-testid="draft-add"
            >
              {t('assistant.draftAddAnother')}
            </button>
            <button
              type="button"
              onClick={() => onCommit('abandon')}
              disabled={committing}
              data-testid="draft-abandon"
            >
              {t('assistant.draftAbandon')}
            </button>
          </div>
        </div>
      ) : (
        <div className="assistant-draft-actions">
          <button
            type="button"
            className="is-primary"
            onClick={() => onCommit()}
            disabled={committing || !canCommit}
            data-testid="draft-confirm"
          >
            {committing
              ? t('assistant.draftSaving')
              : isUpdate
                ? t('assistant.draftUpdateConfirm')
                : t('assistant.draftConfirm')}
          </button>
          <button type="button" onClick={onCancel} disabled={committing} data-testid="draft-cancel-2">
            {t('assistant.draftCancel')}
          </button>
          {isUpdate && onFillMissing && (
            <button
              type="button"
              onClick={onFillMissing}
              disabled={committing}
              data-testid="draft-fill"
            >
              {t('assistant.draftFillMissing')}
            </button>
          )}
          {(draft.lookup_failed || !title) && onManual && (
            <button type="button" onClick={onManual} data-testid="draft-manual">
              {t('assistant.draftManual')}
            </button>
          )}
        </div>
      )}
    </section>
  );
}
