import React from 'react';
import { useNavigate } from 'react-router-dom';
import SphereView from './SphereView';
import { MEDIA_BASE_URL } from './Config';
import { LIBRARY_PATH } from '../config';

// Sphere view bound to Book items: cover thumb, title, authors · ISBN, detail nav.
export default function BookSphere({ books }) {
  const navigate = useNavigate();
  return (
    <SphereView
      items={books}
      getThumb={(b) => (b.thumb_image ? `${MEDIA_BASE_URL}/${b.thumb_image}` : null)}
      getTitle={(b) => b.title_cn || b.title}
      getSubtitle={(b) => [
        b.authors && b.authors.length ? b.authors.join(', ') : null,
        b.isbn ? `ISBN ${b.isbn}` : null,
      ].filter(Boolean).join(' · ')}
      onSelect={(b) => navigate(`${LIBRARY_PATH}/books/${b.id}`)}
    />
  );
}
