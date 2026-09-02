import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import './Books.css';
import { API_BASE_URL } from './Config';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../AuthContext';
import SearchableSelect from './SearchableSelect';
import PageLayout from './PageLayout';

const Categories = () => {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const page = parseInt(searchParams.get('page')) || 1;
  const limit = parseInt(searchParams.get('limit')) || 20;
  const sortBy = searchParams.get('sort_by') || 'weight';

  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(1);
  const [totalCategories, setTotalCategories] = useState(0);

  const [searchQuery, setSearchQuery] = useState(searchParams.get('q') || '');
  const [submittedQuery, setSubmittedQuery] = useState(searchParams.get('q') || '');

  const [allCategories, setAllCategories] = useState([]);
  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState(null);
  const [formData, setFormData] = useState({
    name: '',
    intro: '',
    parent: '',
  });
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(null);

  const fetchCategories = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, limit, sort_by: sortBy };
      if (submittedQuery) {
        params.q = submittedQuery;
      }
      const response = await axios.get(`${window.location.origin}${API_BASE_URL}/categories/`, { params });
      const data = response.data;
      setCategories(data.categories || []);
      setTotalPages(data.total_pages || 1);
      setTotalCategories(data.total_categories || 0);
    } catch (error) {
      console.error('Error fetching categories:', error);
    }
    setLoading(false);
  }, [page, limit, sortBy, submittedQuery]);

  useEffect(() => {
    fetchCategories();
  }, [fetchCategories]);

  // Sync submittedQuery from URL on mount
  useEffect(() => {
    const q = searchParams.get('q') || '';
    if (q !== submittedQuery) {
      setSubmittedQuery(q);
      setSearchQuery(q);
    }
  }, []); // eslint-disable-line

  // Fetch all categories (for parent dropdown)
  useEffect(() => {
    const fetchAll = async () => {
      try {
        const res = await axios.get(`${window.location.origin}${API_BASE_URL}/categories/?limit=1000`);
        setAllCategories(res.data.categories || []);
      } catch (e) { /* ignore */ }
    };
    fetchAll();
  }, []);

  const setPageParam = (p) => {
    const next = new URLSearchParams(searchParams);
    next.set('page', String(p));
    setSearchParams(next, { replace: true });
  };
  const setSortByParam = (s) => {
    const next = new URLSearchParams(searchParams);
    next.set('sort_by', s);
    next.set('page', '1');
    setSearchParams(next, { replace: true });
  };
  const setLimitParam = (l) => {
    const next = new URLSearchParams(searchParams);
    next.set('limit', String(l));
    next.set('page', '1');
    setSearchParams(next, { replace: true });
  };

  const formatCategoryLabel = (cat) => {
    return cat.path || cat.name;
  };

  // ── Modal helpers ──────────────────────────────────────────

  const openCreate = () => {
    setEditingCategory(null);
    setFormData({ name: '', intro: '', parent: '' });
    setModalOpen(true);
  };

  const openEdit = (category) => {
    setEditingCategory(category);
    setFormData({
      name: category.name || '',
      intro: category.intro || '',
      parent: category.parent ?? '',
    });
    setModalOpen(true);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!formData.name.trim()) return;
    setSaving(true);

    // Strip empty strings so Pydantic validators don't reject them
    const payload = {};
    for (const [k, v] of Object.entries(formData)) {
      if (v !== '' && v !== null && v !== undefined) {
        payload[k] = v;
      }
    }

    try {
      if (editingCategory) {
        await axios.put(
          `${window.location.origin}${API_BASE_URL}/categories/${editingCategory.id}`,
          payload
        );
      } else {
        await axios.post(
          `${window.location.origin}${API_BASE_URL}/categories/`,
          payload
        );
      }
      setModalOpen(false);
      fetchCategories();
    } catch (err) {
      const msg = err.response?.data?.detail || 'Save failed';
      alert(msg);
    }
    setSaving(false);
  };

  const handleDelete = async (categoryId) => {
    try {
      await axios.delete(`${window.location.origin}${API_BASE_URL}/categories/${categoryId}`);
      setConfirmDelete(null);
      fetchCategories();
    } catch (err) {
      const msg = err.response?.data?.detail || 'Delete failed';
      alert(msg);
    }
  };

  const handleSearch = () => {
    const q = searchQuery.trim();
    setSubmittedQuery(q);
    const next = new URLSearchParams(searchParams);
    if (q) next.set('q', q); else next.delete('q');
    next.set('page', '1');
    setSearchParams(next, { replace: true });
  };

  const sortOptions = [
    { value: 'weight', label: t('common.bookCount') },
    { value: 'id', label: 'ID' },
    { value: 'name', label: t('common.name') },
  ];

  const listColumns = [t('common.name'), 'Path', 'Actions'];

  const renderItem = (category, viewMode) => {
    if (viewMode === 'list') {
      return (
        <tr key={category.id} onClick={() => navigate(`${category.id}`)}>
          <td className="list-cell-primary">{category.name}</td>
          <td className="list-cell-secondary">
            {category.path || ''}{category.depth != null ? <span style={{ marginLeft: 8 }}>({category.depth})</span> : null}
          </td>
          {isAuthenticated && (
            <td style={{ width: 80, textAlign: 'right' }}>
              <button className="btn-pill-link" onClick={(e) => { e.stopPropagation(); openEdit(category); }}
                style={{ fontSize: 12, padding: '4px 8px' }}>
                {t('common.edit')}
              </button>
              <button className="btn-pill-link" onClick={(e) => { e.stopPropagation(); setConfirmDelete(category); }}
                style={{ fontSize: 12, padding: '4px 8px', color: '#ff3b30' }}>
                {t('common.delete')}
              </button>
            </td>
          )}
        </tr>
      );
    }
    return (
      <div key={category.id} className="card">
        <h3 className="card-title">{category.name}</h3>
        {category.path && <small style={{ display: 'block', color: '#86868b', fontSize: 11 }}>{category.path}</small>}
        {category.depth != null && <small style={{ display: 'block', color: '#86868b', fontSize: 12 }}>Depth: {category.depth}</small>}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          <button className="btn-pill-link" onClick={() => navigate(`${category.id}`)}>{t('common.view')}</button>
          {isAuthenticated && (
            <>
              <button className="btn-pill-link" onClick={() => openEdit(category)}>{t('common.edit')}</button>
              <button className="btn-pill-link"
                onClick={() => setConfirmDelete(category)}
                style={{ color: '#ff3b30' }}>{t('common.delete')}</button>
            </>
          )}
        </div>
      </div>
    );
  };

  if (loading) return <div className="loading">Loading...</div>;

  return (
    <>
      <PageLayout
        title={t('categories.title')}
        createButton={
          isAuthenticated ? (
            <button className="btn-pill-link" onClick={openCreate} style={{ marginBottom: 20 }}>
              + Create Category
            </button>
          ) : null
        }
        searchValue={searchQuery}
        onSearchChange={setSearchQuery}
        onSearch={handleSearch}
        searchPlaceholder={t('common.search')}
        sortBy={sortBy}
        sortOptions={sortOptions}
        onSort={setSortByParam}
        limit={limit}
        onLimitChange={setLimitParam}
        page={page}
        totalPages={totalPages}
        totalItems={totalCategories}
        onPageChange={setPageParam}
        layoutKey="categories"
        items={categories}
        renderItem={renderItem}
        listColumns={listColumns}
      />

      {/* ── Create / Edit Modal ─────────────────────────────── */}
      {modalOpen && (
        <div style={{
          position: 'fixed', inset: 0, zIndex: 10000,
          background: 'rgba(0,0,0,0.4)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
        }} onClick={() => setModalOpen(false)}>
          <div style={{
            background: '#fff',
            width: Math.min(560, window.innerWidth - 32),
            padding: '24px 28px',
            borderRadius: 20,
            maxHeight: '90vh', overflow: 'auto',
            boxShadow: '0 25px 50px rgba(0,0,0,0.25)',
          }} onClick={(e) => e.stopPropagation()}>
            <h2 style={{ margin: '0 0 20px', fontSize: 22, fontWeight: 600 }}>
              {editingCategory ? t('categories.edit') : t('categories.create')}
            </h2>
            <form onSubmit={handleSave}>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 600, color: '#1d1d1f' }}>
                  {t('common.name')} <span style={{ color: '#ff3b30' }}>*</span>
                </label>
                <input type="text" value={formData.name} required
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  style={{
                    width: '100%', padding: '8px 12px', borderRadius: 8, border: '1px solid #d2d2d7',
                    fontSize: 14, outline: 'none',
                  }} placeholder="e.g. Fiction" />
              </div>
              <SearchableSelect
                label="Parent Category"
                value={formData.parent ? parseInt(formData.parent) : null}
                onChange={(v) => setFormData({ ...formData, parent: v || '' })}
                options={allCategories
                  .filter(c => !editingCategory || c.id !== editingCategory.id)
                  .map(c => ({ id: c.id, name: formatCategoryLabel(c) }))}
                placeholder="Search parent category..."
              />
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 600, color: '#1d1d1f' }}>
                  {t('common.introduction')}
                </label>
                <textarea value={formData.intro} rows={4}
                  onChange={(e) => setFormData({ ...formData, intro: e.target.value })}
                  style={{
                    width: '100%', padding: '8px 12px', borderRadius: 8, border: '1px solid #d2d2d7',
                    fontSize: 14, outline: 'none', resize: 'vertical',
                  }} placeholder="Category description..." />
              </div>
              <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
                <button type="button" className="btn-pill-link"
                  onClick={() => setModalOpen(false)}>{t('common.cancel')}</button>
                <button type="submit" className="btn-pill-link" disabled={saving}
                  style={saving ? { opacity: 0.6 } : {}}>
                  {saving ? t('common.saving') : t('common.save')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Delete Confirmation ─────────────────────────────── */}
      {confirmDelete && (
        <div style={{
          position: 'fixed', inset: 0, zIndex: 10001,
          background: 'rgba(0,0,0,0.4)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
        }} onClick={() => setConfirmDelete(null)}>
          <div style={{
            background: '#fff',
            width: Math.min(560, window.innerWidth - 32),
            padding: '24px 28px',
            borderRadius: 20,
            boxShadow: '0 25px 50px rgba(0,0,0,0.25)',
            textAlign: 'center',
          }} onClick={(e) => e.stopPropagation()}>
            <h3 style={{ margin: '0 0 8px', fontSize: 18 }}>
              Delete "{confirmDelete.name}"?
            </h3>
            <p style={{ color: '#86868b', margin: '0 0 20px', fontSize: 15 }}>
              {t('common.cannotUndo')}
            </p>
            <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
              <button className="btn-pill-link" onClick={() => setConfirmDelete(null)}>{t('common.cancel')}</button>
              <button className="btn-pill-link" onClick={() => handleDelete(confirmDelete.id)}
                style={{ color: '#ff3b30' }}>{t('common.deleteConfirm')}</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default Categories;
