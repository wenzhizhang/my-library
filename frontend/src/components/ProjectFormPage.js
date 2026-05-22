import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { API_BASE_URL } from './Config';

const ProjectFormPage = () => {
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    url: '',
    icon_url: '',
    sort_order: 0,
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: name === 'sort_order' ? parseInt(value) || 0 : value,
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.name.trim()) {
      setError('Application name is required');
      return;
    }

    setSubmitting(true);
    setError(null);

    try {
      await axios.post(`${window.location.origin}${API_BASE_URL}/applications/`, formData);
      navigate('/projects');
    } catch (err) {
      console.error('Error creating application:', err);
      setError('Failed to register application. Please try again.');
    }
    setSubmitting(false);
  };

  return (
    <div style={{ backgroundColor: '#f5f5f7', minHeight: '100vh' }}>
      {/* Header */}
      <div
        style={{
          padding: '60px 20px 20px',
          textAlign: 'center',
        }}
      >
        <h1
          style={{
            color: '#1d1d1f',
            fontSize: '48px',
            fontWeight: 600,
            lineHeight: 1.1,
            letterSpacing: '-0.28px',
            margin: 0,
            fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Display", sans-serif',
          }}
        >
          Register New Application
        </h1>
        <p
          style={{
            color: 'rgba(0,0,0,0.56)',
            fontSize: '20px',
            fontWeight: 400,
            lineHeight: 1.33,
            marginTop: '8px',
            fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
          }}
        >
          Add a new project to your portfolio
        </p>
      </div>

      {/* Form */}
      <div
        style={{
          maxWidth: '600px',
          margin: '-30px auto 40px',
          background: '#fff',
          borderRadius: '16px',
          padding: '40px',
          boxShadow: 'rgba(0, 0, 0, 0.08) 0px 2px 12px',
        }}
      >
        {error && (
          <div
            style={{
              background: '#fef2f2',
              color: '#dc2626',
              padding: '12px 16px',
              borderRadius: '8px',
              marginBottom: '20px',
              fontSize: '14px',
              fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
            }}
          >
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          {/* Name */}
          <div style={{ marginBottom: '24px' }}>
            <label
              htmlFor="name"
              style={{
                display: 'block',
                fontSize: '14px',
                fontWeight: 500,
                color: '#1d1d1f',
                marginBottom: '6px',
                fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
              }}
            >
              Application Name <span style={{ color: '#ff3b30' }}>*</span>
            </label>
            <input
              type="text"
              id="name"
              name="name"
              value={formData.name}
              onChange={handleChange}
              placeholder="e.g. My Library"
              style={{
                width: '100%',
                padding: '10px 14px',
                borderRadius: '8px',
                border: '1px solid #d2d2d7',
                fontSize: '16px',
                lineHeight: 1.4,
                color: '#1d1d1f',
                outline: 'none',
                boxSizing: 'border-box',
                fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
              }}
              onFocus={(e) => (e.target.style.borderColor = '#0071e3')}
              onBlur={(e) => (e.target.style.borderColor = '#d2d2d7')}
            />
          </div>

          {/* Description */}
          <div style={{ marginBottom: '24px' }}>
            <label
              htmlFor="description"
              style={{
                display: 'block',
                fontSize: '14px',
                fontWeight: 500,
                color: '#1d1d1f',
                marginBottom: '6px',
                fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
              }}
            >
              Description
            </label>
            <textarea
              id="description"
              name="description"
              value={formData.description}
              onChange={handleChange}
              placeholder="A brief description of your application..."
              rows={3}
              style={{
                width: '100%',
                padding: '10px 14px',
                borderRadius: '8px',
                border: '1px solid #d2d2d7',
                fontSize: '16px',
                lineHeight: 1.4,
                color: '#1d1d1f',
                outline: 'none',
                resize: 'vertical',
                boxSizing: 'border-box',
                fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
              }}
              onFocus={(e) => (e.target.style.borderColor = '#0071e3')}
              onBlur={(e) => (e.target.style.borderColor = '#d2d2d7')}
            />
          </div>

          {/* URL */}
          <div style={{ marginBottom: '24px' }}>
            <label
              htmlFor="url"
              style={{
                display: 'block',
                fontSize: '14px',
                fontWeight: 500,
                color: '#1d1d1f',
                marginBottom: '6px',
                fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
              }}
            >
              URL
            </label>
            <input
              type="text"
              id="url"
              name="url"
              value={formData.url}
              onChange={handleChange}
              placeholder="e.g. /my-library or https://example.com"
              style={{
                width: '100%',
                padding: '10px 14px',
                borderRadius: '8px',
                border: '1px solid #d2d2d7',
                fontSize: '16px',
                lineHeight: 1.4,
                color: '#1d1d1f',
                outline: 'none',
                boxSizing: 'border-box',
                fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
              }}
              onFocus={(e) => (e.target.style.borderColor = '#0071e3')}
              onBlur={(e) => (e.target.style.borderColor = '#d2d2d7')}
            />
          </div>

          {/* Icon URL */}
          <div style={{ marginBottom: '24px' }}>
            <label
              htmlFor="icon_url"
              style={{
                display: 'block',
                fontSize: '14px',
                fontWeight: 500,
                color: '#1d1d1f',
                marginBottom: '6px',
                fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
              }}
            >
              Icon URL
            </label>
            <input
              type="text"
              id="icon_url"
              name="icon_url"
              value={formData.icon_url}
              onChange={handleChange}
              placeholder="URL to application icon (optional)"
              style={{
                width: '100%',
                padding: '10px 14px',
                borderRadius: '8px',
                border: '1px solid #d2d2d7',
                fontSize: '16px',
                lineHeight: 1.4,
                color: '#1d1d1f',
                outline: 'none',
                boxSizing: 'border-box',
                fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
              }}
              onFocus={(e) => (e.target.style.borderColor = '#0071e3')}
              onBlur={(e) => (e.target.style.borderColor = '#d2d2d7')}
            />
          </div>

          {/* Sort Order */}
          <div style={{ marginBottom: '32px' }}>
            <label
              htmlFor="sort_order"
              style={{
                display: 'block',
                fontSize: '14px',
                fontWeight: 500,
                color: '#1d1d1f',
                marginBottom: '6px',
                fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
              }}
            >
              Sort Order
            </label>
            <input
              type="number"
              id="sort_order"
              name="sort_order"
              value={formData.sort_order}
              onChange={handleChange}
              placeholder="0"
              min="0"
              style={{
                width: '100px',
                padding: '10px 14px',
                borderRadius: '8px',
                border: '1px solid #d2d2d7',
                fontSize: '16px',
                lineHeight: 1.4,
                color: '#1d1d1f',
                outline: 'none',
                boxSizing: 'border-box',
                fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
              }}
              onFocus={(e) => (e.target.style.borderColor = '#0071e3')}
              onBlur={(e) => (e.target.style.borderColor = '#d2d2d7')}
            />
          </div>

          {/* Buttons */}
          <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
            <button
              type="button"
              onClick={() => navigate('/projects')}
              style={{
                padding: '10px 22px',
                borderRadius: '980px',
                border: '1px solid #d2d2d7',
                background: 'transparent',
                color: '#1d1d1f',
                fontSize: '14px',
                fontWeight: 500,
                cursor: 'pointer',
                fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
              }}
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              style={{
                padding: '10px 22px',
                borderRadius: '980px',
                border: 'none',
                background: submitting ? '#6ba5d9' : '#0071e3',
                color: '#fff',
                fontSize: '14px',
                fontWeight: 500,
                cursor: submitting ? 'not-allowed' : 'pointer',
                fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
              }}
            >
              {submitting ? 'Registering...' : 'Register Application'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default ProjectFormPage;
