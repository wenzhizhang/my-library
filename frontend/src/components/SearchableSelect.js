import React, { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';

const COLORS = {
  pureBlack: '#000000',
  lightGray: '#f5f5f7',
  nearBlack: '#1d1d1f',
  appleBlue: '#0071e3',
  linkBlue: '#0066cc',
  brightBlue: '#2997ff',
  white: '#ffffff',
  black80: 'rgba(0, 0, 0, 0.8)',
  black48: 'rgba(0, 0, 0, 0.48)',
  darkSurface1: '#272729',
  darkSurface2: '#262628',
  darkSurface3: '#28282a',
  buttonActive: '#ededf2',
  buttonDefaultLight: '#fafafc',
  overlay: 'rgba(210, 210, 215, 0.64)',
  white32: 'rgba(255, 255, 255, 0.32)',
  cardShadow: 'rgba(0, 0, 0, 0.22) 3px 5px 30px 0px',
};

const TYPOGRAPHY = {
  body: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
    fontSize: '17px',
    fontWeight: 400,
    lineHeight: 1.47,
    letterSpacing: '-0.374px',
  },
  bodyEmphasis: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
    fontSize: '17px',
    fontWeight: 600,
    lineHeight: 1.24,
    letterSpacing: '-0.374px',
  },
  caption: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
    fontSize: '14px',
    fontWeight: 400,
    lineHeight: 1.29,
    letterSpacing: '-0.224px',
  },
};

function SearchableSelect({
  label,
  value,
  onChange,
  options,
  placeholder = 'Search and select...',
  required = false,
  dark = false,
  error,
  addNewLink,
  addNewLabel = '+ New',
  onAddNew,
  keepSearchOnSelect = false,
  renderOption = null,
}) {
  const [search, setSearch] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const wrapperRef = useRef(null);
  const dropdownRef = useRef(null);
  const timerRef = useRef(null);

  useEffect(() => {
    if (!isOpen) return;
    const close = (e) => {
      if (dropdownRef.current && dropdownRef.current.contains(e.target)) return;
      setIsOpen(false);
    };
    window.addEventListener('scroll', close, true);
    return () => window.removeEventListener('scroll', close, true);
  }, [isOpen]);

  useEffect(() => {
    return () => {
      clearTimeout(timerRef.current);
    };
  }, []);

  const filteredOptions = options.filter(opt =>
    (opt.name || '').toLowerCase().includes(search.toLowerCase())
  );

  const selectedOption = options.find(opt => opt.id === value);

  const dropdownPos = (() => {
    if (!isOpen || !wrapperRef.current) return null;
    const rect = wrapperRef.current.getBoundingClientRect();
    return {
      top: rect.bottom + window.scrollY,
      left: rect.left + window.scrollX,
      width: rect.width,
    };
  })();

  const labelStyle = {
    fontFamily: TYPOGRAPHY.bodyEmphasis.fontFamily,
    fontSize: TYPOGRAPHY.bodyEmphasis.fontSize,
    fontWeight: TYPOGRAPHY.bodyEmphasis.fontWeight,
    lineHeight: TYPOGRAPHY.bodyEmphasis.lineHeight,
    letterSpacing: TYPOGRAPHY.bodyEmphasis.letterSpacing,
    color: dark ? COLORS.white : COLORS.nearBlack,
    marginBottom: '8px',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  };

  const inputStyle = {
    fontFamily: TYPOGRAPHY.body.fontFamily,
    fontSize: TYPOGRAPHY.body.fontSize,
    fontWeight: TYPOGRAPHY.body.fontWeight,
    color: dark ? COLORS.white : COLORS.nearBlack,
    background: dark ? 'rgba(39, 39, 41, 0.6)' : 'rgba(255, 255, 255, 0.55)',
    border: 'none',
    borderRadius: '8px',
    padding: '12px 16px',
    width: '100%',
    outline: 'none',
    boxSizing: 'border-box',
    cursor: 'pointer',
  };

  const dropdownStyle = {
    position: 'absolute',
    top: 0,
    left: 0,
    maxHeight: '200px',
    overflowY: 'auto',
    background: dark ? 'rgba(39, 39, 41, 0.95)' : 'rgba(255, 255, 255, 0.95)',
    backdropFilter: 'blur(8px)',
    borderRadius: '0 0 8px 8px',
    boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
    zIndex: 9999,
  };

  const optionStyle = (isSelected) => ({
    padding: '10px 16px',
    cursor: 'pointer',
    fontFamily: TYPOGRAPHY.body.fontFamily,
    fontSize: TYPOGRAPHY.body.fontSize,
    color: dark ? COLORS.white : COLORS.nearBlack,
    background: isSelected ? (dark ? 'rgba(0,113,227,0.3)' : 'rgba(0,113,227,0.1)') : 'transparent',
  });
  const dropdownPortal = isOpen && filteredOptions.length > 0 && dropdownPos && createPortal(
    <div ref={dropdownRef} style={{
      ...dropdownStyle,
      top: dropdownPos.top,
      left: dropdownPos.left,
      width: dropdownPos.width,
    }} onMouseDown={(e) => e.preventDefault()}>
      {filteredOptions.map(opt => (
        <div
          key={opt.id}
          style={optionStyle(opt.id === value)}
          onClick={() => {
            onChange(opt.id);
            if (!keepSearchOnSelect) {
              setSearch('');
              setIsOpen(false);
            }
          }}
        >
          {renderOption ? renderOption(opt) : opt.name}
        </div>
      ))}
    </div>,
    document.body
  );

  return (
    <div ref={wrapperRef} style={{ marginBottom: '24px', width: '100%' }}>
      <label style={labelStyle}>
        <span>
          {label}
          {required && <span style={{ color: COLORS.appleBlue, marginLeft: '4px' }}>*</span>}
        </span>
        {(addNewLink || onAddNew) && (
          addNewLink ? (
            <a
              href={addNewLink}
              target="_blank"
              rel="noopener noreferrer"
              onClick={(e) => e.stopPropagation()}
              style={{
                fontSize: '12px',
                color: COLORS.appleBlue,
                textDecoration: 'none',
                cursor: 'pointer',
              }}
            >
              {addNewLabel}
            </a>
          ) : (
            <button
              type="button"
              onClick={(e) => { e.preventDefault(); e.stopPropagation(); onAddNew(); }}
              style={{
                fontSize: '12px',
                color: COLORS.appleBlue,
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                padding: 0,
              }}
            >
              {addNewLabel}
            </button>
          )
        )}
      </label>
      <input
        type="text"
        value={isOpen ? search : (selectedOption ? selectedOption.name : '')}
        onChange={(e) => {
          setSearch(e.target.value);
          setIsOpen(true);
        }}
        onFocus={() => {
          if (!keepSearchOnSelect) setSearch('');
          setIsOpen(true);
        }}
        onBlur={() => {
          timerRef.current = setTimeout(() => setIsOpen(false), 150);
        }}
        placeholder={placeholder}
        style={inputStyle}
      />
      {error && (
        <div style={{
          fontFamily: TYPOGRAPHY.caption.fontFamily,
          fontSize: TYPOGRAPHY.caption.fontSize,
          color: '#ff3b30',
          marginTop: '6px',
          letterSpacing: TYPOGRAPHY.caption.letterSpacing,
        }}>
          {error}
        </div>
      )}
      {dropdownPortal}
    </div>
  );
}

export default SearchableSelect;
