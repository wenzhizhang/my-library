import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { API_BASE_URL } from './Config';

const Projects = () => {
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    fetchProjects();
  }, []);

  const fetchProjects = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get(
        `${window.location.origin}${API_BASE_URL}/applications/?limit=50&sort_by=sort_order`
      );
      setProjects(response.data.applications || []);
    } catch (err) {
      console.error('Error fetching projects:', err);
      setError('Failed to load projects');
    }
    setLoading(false);
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
        <p style={{ color: 'rgba(0,0,0,0.48)', fontSize: '17px' }}>Loading...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh', flexDirection: 'column', gap: '20px' }}>
        <p style={{ color: '#ff3b30', fontSize: '17px' }}>{error}</p>
        <button
          onClick={fetchProjects}
          style={{
            padding: '8px 20px',
            borderRadius: '980px',
            border: '1px solid #0071e3',
            background: 'transparent',
            color: '#0071e3',
            fontSize: '14px',
            cursor: 'pointer',
          }}
        >
          Retry
        </button>
      </div>
    );
  }

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
          My Projects
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
          Applications and experiments I've built
        </p>
      </div>

      {/* Action Bar */}
      <div
        style={{
          maxWidth: '980px',
          margin: '0 auto',
          padding: '20px 20px 0',
          display: 'flex',
          justifyContent: 'flex-end',
        }}
      >
        <button
          onClick={() => navigate('/projects/create')}
          style={{
            padding: '8px 22px',
            borderRadius: '980px',
            border: 'none',
            background: '#0071e3',
            color: '#fff',
            fontSize: '14px',
            fontWeight: 500,
            cursor: 'pointer',
            fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
          }}
        >
          + Add New Application
        </button>
      </div>

      {/* Project Grid */}
      <div
        style={{
          maxWidth: '980px',
          margin: '0 auto',
          padding: '20px',
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
          gap: '20px',
        }}
      >
        {projects.length === 0 && (
          <div style={{ gridColumn: '1 / -1', textAlign: 'center', padding: '60px 20px' }}>
            <p style={{ color: 'rgba(0,0,0,0.48)', fontSize: '17px' }}>
              No applications registered yet.
            </p>
            <p style={{ color: 'rgba(0,0,0,0.48)', fontSize: '14px', marginTop: '8px' }}>
              Click "Add New Application" to register your first project.
            </p>
          </div>
        )}

        {projects.map((project) => (
          <div
            key={project.id}
            style={{
              background: '#fff',
              borderRadius: '12px',
              padding: '24px',
              boxShadow: 'rgba(0, 0, 0, 0.08) 0px 2px 12px',
              cursor: project.url ? 'pointer' : 'default',
              transition: 'transform 0.2s, box-shadow 0.2s',
            }}
            onClick={() => project.url && window.location.assign(project.url)}
            onMouseEnter={(e) => {
              if (project.url) {
                e.currentTarget.style.transform = 'translateY(-2px)';
                e.currentTarget.style.boxShadow = 'rgba(0, 0, 0, 0.12) 0px 4px 20px';
              }
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = 'none';
              e.currentTarget.style.boxShadow = 'rgba(0, 0, 0, 0.08) 0px 2px 12px';
            }}
          >
            {/* Icon */}
            {project.icon_url && (
              <div style={{ marginBottom: '16px' }}>
                <img
                  src={project.icon_url}
                  alt={project.name}
                  style={{
                    width: '48px',
                    height: '48px',
                    borderRadius: '10px',
                    objectFit: 'cover',
                  }}
                />
              </div>
            )}

            {/* Name */}
            <h3
              style={{
                fontSize: '20px',
                fontWeight: 600,
                lineHeight: 1.2,
                color: '#1d1d1f',
                margin: '0 0 8px',
                fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Display", sans-serif',
              }}
            >
              {project.name}
            </h3>

            {/* Description */}
            {project.description && (
              <p
                style={{
                  fontSize: '14px',
                  lineHeight: 1.4,
                  color: 'rgba(0,0,0,0.64)',
                  margin: 0,
                  fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
                }}
              >
                {project.description.length > 120
                  ? project.description.substring(0, 120) + '...'
                  : project.description}
              </p>
            )}

            {/* URL hint */}
            {project.url && (
              <p
                style={{
                  fontSize: '12px',
                  color: '#0071e3',
                  margin: '12px 0 0',
                  fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
                }}
              >
                Open →
              </p>
            )}
          </div>
        ))}
      </div>

      {/* Back Button */}
      <div
        style={{
          maxWidth: '980px',
          margin: '0 auto',
          padding: '20px 20px 40px',
          textAlign: 'center',
        }}
      >
        <button
          onClick={() => navigate('/')}
          style={{
            padding: '8px 20px',
            borderRadius: '980px',
            border: '1px solid rgba(0,0,0,0.2)',
            background: 'transparent',
            color: '#1d1d1f',
            fontSize: '14px',
            cursor: 'pointer',
            fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
          }}
        >
          ← Back to Home
        </button>
      </div>
    </div>
  );
};

export default Projects;
