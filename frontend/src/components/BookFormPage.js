import React, { useState, useCallback, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { createPortal } from 'react-dom';
import './Books.css';
import { API_BASE_URL } from './Config';
import { LIBRARY_PATH } from '../config';
import SearchableSelect from './SearchableSelect';
import { useAuth } from '../AuthContext';

// ============================================================
// Apple Design System - Book Creation/Update Page (JavaScript)
// ============================================================

// --- Predefined Option Lists ---

const BINDING_TYPES = ['精装', '平装', '盒装', '线装'];
const PAPER_TYPES = ['铜版纸', '胶版纸', '宣纸', '轻型纸', '纯质纸', '书写纸', '雅致纸', '特种纸'];
const LANGUAGES = ['中文', '繁体中文', '英文', '中文/英文', '日文'];
const COMPOSE_TYPES = ['横排', '竖排'];
const PURCHASE_STORES = [
  '京东自营', '京东文轩', '京东博库', '壹书堂', '广西师范大学出版社',
  '摩点众筹', '当当自营', '孔夫子旧书网', '淘宝', '拼多多',
  '抖音', '微店', '小红书', '西西弗书店', '中图网',
  '快团团', '齐鲁书社', '浙江古籍出版社', '纸上声音',
];

const NATIONS = [
  "中国", "俄罗斯", "前苏联", "希腊", "美国", "英国", "法国", "德国",
  "古巴", "西班牙", "古罗马", "加拿大", "爱尔兰", "澳大利亚", "瑞士",
  "阿根廷", "哥伦比亚", "奥地利", "挪威", "瑞典", "意大利", "比利时",
  "墨西哥", "荷兰", "巴西", "波兰", "伊朗", "波斯", "智利", "南非",
  "马来西亚", "捷克", "毛里求斯", "丹麦", "葡萄牙", "黎巴嫩", "冰岛",
  "以色列", "日本", "无",
];

const DYNASTIES = [
  "上古", "夏", "商", "西周", "东周", "春秋", "战国",
  "秦", "西汉", "东汉", "魏", "蜀", "吴", "西晋", "东晋",
  "南北朝", "隋", "唐", "五代", "北宋", "南宋",
  "元", "明", "清", "民国", "现代", "当代",
];

// --- Design Tokens ---

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
  fontDisplay: '-apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Icons", "Helvetica Neue", Helvetica, Arial, sans-serif',
  fontText: '-apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Icons", "Helvetica Neue", Helvetica, Arial, sans-serif',
  hero: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Display", sans-serif',
    fontSize: '56px',
    fontWeight: 600,
    lineHeight: 1.07,
    letterSpacing: '-0.28px',
  },
  sectionHeading: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Display", sans-serif',
    fontSize: '40px',
    fontWeight: 600,
    lineHeight: 1.10,
    letterSpacing: 'normal',
  },
  tileHeading: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Display", sans-serif',
    fontSize: '28px',
    fontWeight: 400,
    lineHeight: 1.14,
    letterSpacing: '0.196px',
  },
  cardTitle: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Display", sans-serif',
    fontSize: '21px',
    fontWeight: 700,
    lineHeight: 1.19,
    letterSpacing: '0.231px',
  },
  subHeading: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Display", sans-serif',
    fontSize: '21px',
    fontWeight: 400,
    lineHeight: 1.19,
    letterSpacing: '0.231px',
  },
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
  button: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
    fontSize: '17px',
    fontWeight: 400,
    lineHeight: 2.41,
    letterSpacing: 'normal',
  },
  link: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
    fontSize: '14px',
    fontWeight: 400,
    lineHeight: 1.43,
    letterSpacing: '-0.224px',
  },
  caption: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
    fontSize: '14px',
    fontWeight: 400,
    lineHeight: 1.29,
    letterSpacing: '-0.224px',
  },
  micro: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif',
    fontSize: '12px',
    fontWeight: 400,
    lineHeight: 1.33,
    letterSpacing: '-0.12px',
  },
};

// --- Components ---

function Navigation() {
  const navStyle = {
    position: 'sticky',
    top: 0,
    zIndex: 100,
    height: '48px',
    background: 'rgba(0, 0, 0, 0.8)',
    backdropFilter: 'saturate(180%) blur(20px)',
    WebkitBackdropFilter: 'saturate(180%) blur(20px)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '0 24px',
    fontFamily: TYPOGRAPHY.fontText,
    fontSize: '12px',
    fontWeight: 400,
    color: COLORS.white,
    letterSpacing: '-0.12px',
  };



  return (
    <nav style={navStyle}>
      <div style={{ fontWeight: 600, fontSize: '17px', letterSpacing: '-0.374px' }}>
        📚 Library
      </div>
      <div style={{ display: 'flex', gap: '32px', alignItems: 'center' }}>
        <span style={{ cursor: 'pointer', opacity: 0.8, transition: 'opacity 0.2s' }} 
              onMouseEnter={(e) => (e.currentTarget.style.opacity = '1')}
              onMouseLeave={(e) => (e.currentTarget.style.opacity = '0.8')}>
          Books
        </span>
        <span style={{ cursor: 'pointer', opacity: 0.8, transition: 'opacity 0.2s' }}
              onMouseEnter={(e) => (e.currentTarget.style.opacity = '1')}
              onMouseLeave={(e) => (e.currentTarget.style.opacity = '0.8')}>
          Authors
        </span>
        <span style={{ cursor: 'pointer', opacity: 0.8, transition: 'opacity 0.2s' }}
              onMouseEnter={(e) => (e.currentTarget.style.opacity = '1')}
              onMouseLeave={(e) => (e.currentTarget.style.opacity = '0.8')}>
          Settings
        </span>
      </div>
      <div style={{ width: '60px' }} />
    </nav>
  );
}

function Section({ children, dark = false, style = {} }) {
  const sectionStyle = {
    width: '100%',
    minHeight: '60vh',
    background: dark
      ? 'rgba(0, 0, 0, 0.25)'
      : 'rgba(209, 209, 215, 0.15)',
    backdropFilter: 'blur(12px)',
    WebkitBackdropFilter: 'blur(12px)',
    color: dark ? COLORS.white : COLORS.nearBlack,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '80px 24px',
    position: 'relative',
    ...style,
  };

  return <section style={sectionStyle}>{children}</section>;
}

function Container({ children, maxWidth = '980px', style = {} }) {


  return (
    <div style={{ 
      width: '100%', 
      maxWidth, 
      margin: '0 auto',
      ...style 
    }}>
      {children}
    </div>
  );
}

function AppleButton({ 
  variant = 'primary', 
  children, 
  onClick, 
  type = 'button',
  disabled = false,
  style = {} 
}) {
  const baseStyle = {
    fontFamily: TYPOGRAPHY.button.fontFamily,
    fontSize: TYPOGRAPHY.button.fontSize,
    fontWeight: TYPOGRAPHY.button.fontWeight,
    lineHeight: TYPOGRAPHY.button.lineHeight,
    cursor: disabled ? 'not-allowed' : 'pointer',
    opacity: disabled ? 0.5 : 1,
    transition: 'all 0.2s ease',
    border: 'none',
    outline: 'none',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    textDecoration: 'none',
    ...style,
  };

  const variants = {
    primary: {
      background: COLORS.appleBlue,
      color: COLORS.white,
      padding: '8px 15px',
      borderRadius: '8px',
      border: '1px solid transparent',
    },
    secondary: {
      background: COLORS.nearBlack,
      color: COLORS.white,
      padding: '8px 15px',
      borderRadius: '8px',
    },
    pill: {
      background: COLORS.appleBlue,
      color: COLORS.white,
      padding: '8px 24px',
      borderRadius: '980px',
      fontSize: '14px',
      fontWeight: 400,
      lineHeight: 1.43,
      letterSpacing: '-0.224px',
    },
    'pill-outline': {
      background: 'transparent',
      color: COLORS.appleBlue,
      padding: '8px 24px',
      borderRadius: '980px',
      border: `1px solid ${COLORS.appleBlue}`,
      fontSize: '14px',
      fontWeight: 400,
      lineHeight: 1.43,
      letterSpacing: '-0.224px',
    },
  };

  const [isHovered, setIsHovered] = useState(false);
  const [isActive, setIsActive] = useState(false);

  const hoverStyle = isHovered && !disabled ? {
    filter: variant === 'primary' || variant === 'pill' ? 'brightness(1.1)' : 'none',
    background: variant === 'pill-outline' ? 'rgba(0, 113, 227, 0.05)' : undefined,
  } : {};

  const activeStyle = isActive ? {
    transform: 'scale(0.98)',
  } : {};



  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => { setIsHovered(false); setIsActive(false); }}
      onMouseDown={() => setIsActive(true)}
      onMouseUp={() => setIsActive(false)}
      style={{
        ...baseStyle,
        ...variants[variant],
        ...hoverStyle,
        ...activeStyle,
      }}
    >
      {children}
    </button>
  );
}

function AppleInput({
  label,
  value,
  onChange,
  type = 'text',
  placeholder,
  required = false,
  dark = false,
  multiline = false,
  rows = 4,
  style = {},
  error,
}) {
  const labelStyle = {
    fontFamily: TYPOGRAPHY.bodyEmphasis.fontFamily,
    fontSize: TYPOGRAPHY.bodyEmphasis.fontSize,
    fontWeight: TYPOGRAPHY.bodyEmphasis.fontWeight,
    lineHeight: TYPOGRAPHY.bodyEmphasis.lineHeight,
    letterSpacing: TYPOGRAPHY.bodyEmphasis.letterSpacing,
    color: dark ? COLORS.white : COLORS.nearBlack,
    marginBottom: '8px',
    display: 'block',
  };

  const inputBaseStyle = {
    fontFamily: TYPOGRAPHY.body.fontFamily,
    fontSize: TYPOGRAPHY.body.fontSize,
    fontWeight: TYPOGRAPHY.body.fontWeight,
    lineHeight: TYPOGRAPHY.body.lineHeight,
    letterSpacing: TYPOGRAPHY.body.letterSpacing,
    color: dark ? COLORS.white : COLORS.nearBlack,
    background: dark ? 'rgba(39, 39, 41, 0.6)' : 'rgba(255, 255, 255, 0.55)',
    border: 'none',
    borderRadius: '8px',
    padding: '12px 16px',
    width: '100%',
    outline: 'none',
    transition: 'box-shadow 0.2s ease',
    boxSizing: 'border-box',
  };

  const focusStyle = {
    boxShadow: `0 0 0 2px ${COLORS.appleBlue}`,
  };

  const [isFocused, setIsFocused] = useState(false);

  const inputElement = multiline ? (
    <textarea
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      required={required}
      rows={rows}
      onFocus={() => setIsFocused(true)}
      onBlur={() => setIsFocused(false)}
      style={{
        ...inputBaseStyle,
        resize: 'vertical',
        minHeight: `${rows * 24}px`,
        ...(isFocused ? focusStyle : {}),
        ...style,
      }}
    />
  ) : (
    <input
      type={type}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      required={required}
      onFocus={() => setIsFocused(true)}
      onBlur={() => setIsFocused(false)}
      style={{
        ...inputBaseStyle,
        ...(isFocused ? focusStyle : {}),
        ...style,
      }}
    />
  );



  return (
    <div style={{ marginBottom: '24px', width: '100%' }}>
      <label style={labelStyle}>
        {label}
        {required && <span style={{ color: COLORS.appleBlue, marginLeft: '4px' }}>*</span>}
      </label>
      {inputElement}
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
    </div>
  );
}

function AppleSelect({
  label,
  value,
  onChange,
  options,
  placeholder = 'Select...',
  required = false,
  dark = false,
  style = {},
}) {
  const labelStyle = {
    fontFamily: TYPOGRAPHY.bodyEmphasis.fontFamily,
    fontSize: TYPOGRAPHY.bodyEmphasis.fontSize,
    fontWeight: TYPOGRAPHY.bodyEmphasis.fontWeight,
    lineHeight: TYPOGRAPHY.bodyEmphasis.lineHeight,
    letterSpacing: TYPOGRAPHY.bodyEmphasis.letterSpacing,
    color: dark ? COLORS.white : COLORS.nearBlack,
    marginBottom: '8px',
    display: 'block',
  };

  const selectStyle = {
    fontFamily: TYPOGRAPHY.body.fontFamily,
    fontSize: TYPOGRAPHY.body.fontSize,
    fontWeight: TYPOGRAPHY.body.fontWeight,
    lineHeight: TYPOGRAPHY.body.lineHeight,
    letterSpacing: TYPOGRAPHY.body.letterSpacing,
    color: dark ? COLORS.white : COLORS.nearBlack,
    background: dark ? 'rgba(39, 39, 41, 0.6)' : 'rgba(255, 255, 255, 0.55)',
    border: 'none',
    borderRadius: '8px',
    padding: '12px 16px',
    width: '100%',
    outline: 'none',
    cursor: 'pointer',
    appearance: 'none',
    WebkitAppearance: 'none',
    backgroundImage: `url("data:image/svg+xml,%3Csvg width='12' height='8' viewBox='0 0 12 8' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M1 1L6 6L11 1' stroke='${dark ? 'white' : '%231d1d1f'}' stroke-width='1.5'/%3E%3C/svg%3E")`,
    backgroundRepeat: 'no-repeat',
    backgroundPosition: 'right 16px center',
    paddingRight: '40px',
    boxSizing: 'border-box',
    transition: 'box-shadow 0.2s ease',
    ...style,
  };

  const [isFocused, setIsFocused] = useState(false);



  return (
    <div style={{ marginBottom: '24px', width: '100%' }}>
      <label style={labelStyle}>
        {label}
        {required && <span style={{ color: COLORS.appleBlue, marginLeft: '4px' }}>*</span>}
      </label>
      <select
        value={value ?? ''}
        onChange={(e) => {
          const val = e.target.value;
          onChange(val === '' ? null : Number(val));
        }}
        required={required}
        onFocus={() => setIsFocused(true)}
        onBlur={() => setIsFocused(false)}
        style={{
          ...selectStyle,
          ...(isFocused ? { boxShadow: `0 0 0 2px ${COLORS.appleBlue}` } : {}),
        }}
      >
        <option value="" disabled>{placeholder}</option>
        {options.map((opt) => (
          <option key={opt.id} value={opt.id}>{opt.name}</option>
        ))}
      </select>
    </div>
  );
}

// --- Combobox Component (dropdown + free text) ---

function AppleCombobox({
  label,
  value,
  onChange,
  options,
  placeholder = 'Select or type...',
  required = false,
  dark = false,
  style = {},
  error,
}) {
  const labelStyle = {
    fontFamily: TYPOGRAPHY.bodyEmphasis.fontFamily,
    fontSize: TYPOGRAPHY.bodyEmphasis.fontSize,
    fontWeight: TYPOGRAPHY.bodyEmphasis.fontWeight,
    lineHeight: TYPOGRAPHY.bodyEmphasis.lineHeight,
    letterSpacing: TYPOGRAPHY.bodyEmphasis.letterSpacing,
    color: dark ? COLORS.white : COLORS.nearBlack,
    marginBottom: '8px',
    display: 'block',
  };

  const inputBaseStyle = {
    fontFamily: TYPOGRAPHY.body.fontFamily,
    fontSize: TYPOGRAPHY.body.fontSize,
    fontWeight: TYPOGRAPHY.body.fontWeight,
    lineHeight: TYPOGRAPHY.body.lineHeight,
    letterSpacing: TYPOGRAPHY.body.letterSpacing,
    color: dark ? COLORS.white : COLORS.nearBlack,
    background: dark ? 'rgba(39, 39, 41, 0.6)' : 'rgba(255, 255, 255, 0.55)',
    border: 'none',
    borderRadius: '8px',
    padding: '12px 16px',
    width: '100%',
    outline: 'none',
    transition: 'box-shadow 0.2s ease',
    boxSizing: 'border-box',
    ...style,
  };

  const datalistId = `datalist-${label.replace(/\s+/g, '-')}`;

  return (
    <div style={{ marginBottom: '24px', width: '100%' }}>
      <label style={labelStyle}>
        {label}
        {required && <span style={{ color: COLORS.appleBlue, marginLeft: '4px' }}>*</span>}
      </label>
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        required={required}
        list={datalistId}
        style={inputBaseStyle}
      />
      <datalist id={datalistId}>
        {options.map((opt, idx) => (
          <option key={idx} value={opt} />
        ))}
      </datalist>
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
    </div>
  );
}


// --- Main Component ---


function BookFormPage() {
  const { bookId } = useParams();
  const navigate = useNavigate();
  const mode = bookId ? 'edit' : 'create';
  const { isAuthenticated } = useAuth();

  const [formData, setFormData] = useState({
    isbn: '',
    title_cn: '',
    title: '',
    author_ids: [],
    translator: '',
    publisher_id: null,
    publish_date: '',
    brand_id: null,
    book_series_id: null,
    binding_type: '精装',
    paper_type: '胶版纸',
    pages: 1,
    book_count: 1,
    language: '中文',
    compose_type: '横排',
    purchase_price: null,
    purchase_date: new Date().toISOString().split('T')[0],
    thumb_image: '',
    link: '',
    category_id: null,
    bookshelf_id: null,
    read_state: 'unread',
    catalog: '',
    introduction: '',
    summary: '',
    registered: false,
    edition: '',
    printing_info: '',
    printed_number: null,
    douban_score: null,
    purchase_store: '',
    tags: [],
  });

  const [errors, setErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitSuccess, setSubmitSuccess] = useState(false);
  const [isbnLookupLoading, setIsbnLookupLoading] = useState(false);

  const [apiThumbUrl, setApiThumbUrl] = useState('');
  // --- API Data ---
  const [authors, setAuthors] = useState([]);
  const [publishers, setPublishers] = useState([]);
  const [brands, setBrands] = useState([]);
  const [series, setSeries] = useState([]);
  const [categories, setCategories] = useState([]);
  const [showSeriesModal, setShowSeriesModal] = useState(false);
  const [seriesFormData, setSeriesFormData] = useState({ name: '', intro: '' });
  const [seriesSaving, setSeriesSaving] = useState(false);
  const [showPublisherModal, setShowPublisherModal] = useState(false);
  const [publisherFormData, setPublisherFormData] = useState({ name: '', intro: '', logo: '' });
  const [publisherSaving, setPublisherSaving] = useState(false);
  const [showBrandModal, setShowBrandModal] = useState(false);
  const [brandFormData, setBrandFormData] = useState({ name: '', intro: '' });
  const [brandSaving, setBrandSaving] = useState(false);
  const [bookshelves, setBookshelves] = useState([]);
  const [showBookshelfModal, setShowBookshelfModal] = useState(false);
  const [bookshelfFormData, setBookshelfFormData] = useState({ name: '', intro: '' });
  const [bookshelfSaving, setBookshelfSaving] = useState(false);
  const [showCategoryModal, setShowCategoryModal] = useState(false);
  const [categoryFormData, setCategoryFormData] = useState({ name: '', intro: '', parent: '' });
  const [categorySaving, setCategorySaving] = useState(false);
  const [showAuthorModal, setShowAuthorModal] = useState(false);
  const [authorFormData, setAuthorFormData] = useState({ name: '', name_cn: '', nation: '无', dynasty: '', intro: '', photo: '' });
  const [authorSaving, setAuthorSaving] = useState(false);

  useEffect(() => {
    const fetchAll = async () => {
      const apis = [
        { key: 'authors', url: `${window.location.origin}${API_BASE_URL}/authors/?limit=10000` },
        { key: 'publishers', url: `${window.location.origin}${API_BASE_URL}/publishers/?limit=1000` },
        { key: 'brands', url: `${window.location.origin}${API_BASE_URL}/brands/?limit=1000` },
        { key: 'series', url: `${window.location.origin}${API_BASE_URL}/series/?limit=1000` },
        { key: 'categories', url: `${window.location.origin}${API_BASE_URL}/categories/?limit=1000` },
        { key: 'bookshelves', url: `${window.location.origin}${API_BASE_URL}/bookshelves/?limit=1000` },
      ];
      const results = await Promise.allSettled(
        apis.map(api => axios.get(api.url))
      );
      results.forEach((r, i) => {
        if (r.status === 'fulfilled') {
          const data = r.value.data;
          const key = apis[i].key;
          const list = data[key] || data[key + 'Data'] || [];
          const normalized = list.map(item => ({
            id: item.id,
            name: item.name_cn || item.name || item.path || item.title,
            ...(key === 'authors' ? { name_cn: item.name_cn, name_en: item.name, nation: item.nation, dynasty: item.dynasty } : {}),
            ...(key === 'categories' ? { _path: item.path, _rawName: item.name } : {}),
          }));
          switch (key) {
            case 'authors': setAuthors(normalized); break;
            case 'publishers': setPublishers(normalized); break;
            case 'brands': setBrands(normalized); break;
            case 'series': setSeries(normalized); break;
            case 'categories': {
              const withPath = normalized.map(c => {
                const { _path, _rawName, ...rest } = c;
                return { ...rest, name: c._path || c._rawName };
              });
              setCategories(withPath);
              break;
            }
            case 'bookshelves': setBookshelves(normalized); break;
            default: break;
          }
        }
      });
    };
    fetchAll();
  }, []);

  const updateField = useCallback((field, value) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors(prev => {
        const next = { ...prev };
        delete next[field];
        return next;
      });
    }
  }, [errors]);

  const handleLookupIsbn = useCallback(async () => {
    const isbn = (formData.isbn || '').replace(/[^0-9Xx]/g, '');
    if (isbn.length < 10) {
      alert('Please enter a valid ISBN first');
      return;
    }
    setIsbnLookupLoading(true);
    try {
      const res = await axios.get(`${window.location.origin}${API_BASE_URL}/isbn/${isbn}`);
      const data = res.data;

      setFormData(prev => {
        const next = { ...prev };
        const fill = (key, value) => {
          if (value != null && value !== '' && !next[key]) {
            next[key] = value;
          }
        };

        fill('isbn', data.isbn);
        fill('title', data.title);
        fill('title_cn', data.title_cn);
        fill('publish_date', data.publish_date);
        fill('pages', data.pages);
        fill('price', data.price);
        fill('introduction', data.introduction);
        fill('thumb_image', data.thumb_image);
        fill('binding_type', data.binding_type);
        fill('douban_score', data.douban_score);
        fill('translator', data.translator);
        if (data.thumb_image) setApiThumbUrl(data.thumb_image);
        fill('link', data.link);
        fill('catalog', data.catalog);

        // Tags: use the first tag returned from the API
        const tagNames = data.tag_names || [];
        if (tagNames.length > 0 && (!next.tags || next.tags.length === 0)) {
          next.tags = [tagNames[0]];
        }

        // Publisher: try to match by name
        if (data.publisher_name && !next.publisher_id) {
          const match = publishers.find(
            p => p.name === data.publisher_name
          );
          if (match) {
            next.publisher_id = match.id;
          }
        }

        // Authors: use IDs returned by backend (already found/created)
        const authorIds = data.author_ids || [];
        if (authorIds.length > 0) {
          const current = next.author_ids || [];
          const newIds = authorIds.filter(id => !current.includes(id));
          if (newIds.length > 0) {
            next.author_ids = [...current, ...newIds];
          }
        }

        return next;
      });
    } catch (err) {
      const msg = err.response?.data?.detail || 'Failed to look up ISBN';
      alert(msg);
    } finally {
      setIsbnLookupLoading(false);
    }
  }, [formData.isbn, publishers, authors]);

  const [loadingBook, setLoadingBook] = useState(mode === 'edit');
  
  useEffect(() => {
    if (mode === 'edit' && bookId) {
      const fetchBook = async () => {
        setLoadingBook(true);
        try {
          const response = await axios.get(`${window.location.origin}/api/books/${bookId}`);
          const data = response.data;
          setFormData({
            isbn: data.isbn || '',
            title_cn: data.title_cn || '',
            title: data.title || '',
            author_ids: data.authors?.map(a => a.id) || [],
            ...(() => {
              // Capture author names from edit API response as fallback
              const map = {};
              (data.authors || []).forEach(a => { map[a.id] = a.name; });
              setAuthorNameMap(map);
              return {};
            })(),
            translator: data.translator || '',
            publisher_id: data.publisher?.id || null,
            publish_date: data.publish_date ? data.publish_date.substring(0, 10) : '',
            brand_id: data.brand?.id || null,
            book_series_id: data.book_series?.id || null,
            binding_type: data.binding_type || '',
            paper_type: data.paper_type || '',
            pages: data.pages || null,
            book_count: data.book_count || null,
            language: data.language || '',
            compose_type: data.compose_type || '',
            price: data.price || null,
            purchase_price: data.purchase_price || null,
            purchase_date: data.purchase_date ? data.purchase_date.substring(0, 10) : '',
            thumb_image: data.thumb_image || '',
            link: data.link || '',
            category_id: data.category?.id || null,
            bookshelf_id: data.bookshelf?.id || null,
            read_state: data.read_state || '',
            catalog: data.catalog || '',
            introduction: data.introduction || '',
            summary: data.summary || '',
            registered: data.registered || false,
            edition: data.edition || '',
            printing_info: data.printing_info || '',
            printed_number: data.printed_number || null,
            douban_score: data.douban_score || null,
            purchase_store: data.purchase_store || '',
            tags: data.tags || [],
            in_wish: data.in_wish || false,
          });
        } catch (error) {
          console.error('Error fetching book for edit:', error);
        }
        setLoadingBook(false);
      };
      fetchBook();
    }
  }, [bookId, mode]);

  const validateForm = () => {
    const newErrors = {};
    
    if (!formData.title_cn.trim() && !formData.title.trim()) {
      newErrors.title = 'Title (CN or EN) is required';
    }
    if (formData.author_ids.length === 0) {
      newErrors.author_ids = 'At least one author is required';
    }
    if (!formData.isbn.trim()) {
      newErrors.isbn = 'ISBN is required';
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validateForm()) return;

    setIsSubmitting(true);

    // Build payload — strip null for creation (Pydantic rejects null for required fields)
    let payload = formData;
    if (mode === 'create') {
      const clean = {};
      for (const [key, value] of Object.entries(formData)) {
        if (value !== null && value !== undefined) {
          clean[key] = value;
        }
      }
      payload = clean;
    }

    try {
      if (mode === 'edit' && bookId) {
        await axios.put(`${window.location.origin}/api/books/${bookId}`, payload);
      } else {
        await axios.post(`${window.location.origin}/api/books/`, payload);
      }
      setIsSubmitting(false);
      setSubmitSuccess(true);

      setTimeout(() => {
        setSubmitSuccess(false);
        // Restore previous page state if available, otherwise default
        const prevState = sessionStorage.getItem('booksPageState');
        if (prevState) {
          navigate(`/my-library/books${prevState}`);
          sessionStorage.removeItem('booksPageState');
        } else {
          navigate('/my-library/books');
        }
      }, 1500);
    } catch (error) {
      console.error('Error saving book:', error);
      setIsSubmitting(false);
    }
  };
  const handleAddNewPublisher = useCallback(() => {
    setPublisherFormData({ name: '', intro: '', logo: '' });
    setShowPublisherModal(true);
  }, []);
  const handleSavePublisher = useCallback(async () => {
    if (!publisherFormData.name.trim()) return;
    setPublisherSaving(true);
    try {
      const res = await axios.post(`${window.location.origin}/api/publishers/`, publisherFormData);
      const newItem = { id: res.data.id, name: res.data.name };
      setPublishers(prev => [...prev, newItem]);
      updateField('publisher_id', newItem.id);
      setShowPublisherModal(false);
      setPublisherFormData({ name: '', intro: '', logo: '' });
    } catch (err) {
      console.error('Error creating publisher:', err);
    } finally {
      setPublisherSaving(false);
    }
  }, [publisherFormData, updateField]);
  const handleAddNewBrand = useCallback(() => {
    setBrandFormData({ name: '', intro: '' });
    setShowBrandModal(true);
  }, []);
  const handleSaveBrand = useCallback(async () => {
    if (!brandFormData.name.trim()) return;
    setBrandSaving(true);
    try {
      const res = await axios.post(`${window.location.origin}/api/brands/`, brandFormData);
      const newItem = { id: res.data.id, name: res.data.name };
      setBrands(prev => [...prev, newItem]);
      updateField('brand_id', newItem.id);
      setShowBrandModal(false);
      setBrandFormData({ name: '', intro: '' });
    } catch (err) {
      console.error('Error creating brand:', err);
    } finally {
      setBrandSaving(false);
    }
  }, [brandFormData, updateField]);

  const handleAddNewBookshelf = useCallback(() => {
    setBookshelfFormData({ name: '', intro: '' });
    setShowBookshelfModal(true);
  }, []);

  const handleSaveBookshelf = useCallback(async () => {
    if (!bookshelfFormData.name.trim()) return;
    setBookshelfSaving(true);
    try {
      const res = await axios.post(`${window.location.origin}${API_BASE_URL}/bookshelves/`, bookshelfFormData);
      const newItem = { id: res.data.id, name: res.data.name };
      setBookshelves(prev => [...prev, newItem]);
      updateField('bookshelf_id', newItem.id);
      setShowBookshelfModal(false);
    } catch (err) {
      alert('Failed to create bookshelf');
      setBookshelfSaving(false);
    }
  }, [bookshelfFormData, updateField]);

  const handleAddNewAuthor = useCallback(() => {
    setAuthorFormData({ name: '', name_cn: '', nation: '无', dynasty: '', intro: '', photo: '' });
    setShowAuthorModal(true);
  }, []);

  const handleSaveAuthor = useCallback(async () => {
    if (!authorFormData.name.trim()) return;
    setAuthorSaving(true);
    try {
      const res = await axios.post(`${window.location.origin}/api/authors/`, authorFormData);
      const newItem = { id: res.data.id, name: res.data.name, name_cn: res.data.name_cn };
      setAuthors(prev => [...prev, newItem]);
      updateField('author_ids', [...(formData.author_ids || []), newItem.id]);
      setShowAuthorModal(false);
      setAuthorFormData({ name: '', name_cn: '', nation: '无', dynasty: '', intro: '', photo: '' });
    } catch (err) {
      console.error('Error creating author:', err);
    } finally {
      setAuthorSaving(false);
    }
  }, [authorFormData, updateField, formData.author_ids]);

  const handleAddNewCategory = useCallback(() => {
    setCategoryFormData({ name: '', intro: '' });
    setShowCategoryModal(true);
  }, []);

  const handleSaveCategory = useCallback(async () => {
    if (!categoryFormData.name.trim()) return;
    setCategorySaving(true);
    try {
      const payload = { name: categoryFormData.name, intro: categoryFormData.intro };
      if (categoryFormData.parent) payload.parent = parseInt(categoryFormData.parent);
      const res = await axios.post(`${window.location.origin}/api/categories/`, payload);
      const newItem = { id: res.data.id, name: res.data.path || res.data.name };
      setCategories(prev => [...prev, newItem]);
      updateField('category_id', newItem.id);
      setShowCategoryModal(false);
      setCategoryFormData({ name: '', intro: '', parent: '' });
    } catch (err) {
      console.error('Error creating category:', err);
    } finally {
      setCategorySaving(false);
    }
  }, [categoryFormData, updateField]);

  const handleAddNewSeries = useCallback(() => {
    setSeriesFormData({ name: '', intro: '' });
    setShowSeriesModal(true);
  }, []);

  const handleSaveSeries = useCallback(async () => {
    if (!seriesFormData.name.trim()) return;
    setSeriesSaving(true);
    try {
      const res = await axios.post(`${window.location.origin}/api/series/`, seriesFormData);
      const newItem = { id: res.data.id, name: res.data.name };
      setSeries(prev => [...prev, newItem]);
      updateField('book_series_id', newItem.id);
      setShowSeriesModal(false);
      setSeriesFormData({ name: '', intro: '' });
    } catch (err) {
      console.error('Error creating series:', err);
    } finally {
      setSeriesSaving(false);
    }
  }, [seriesFormData, updateField]);

  const [authorSearch, setAuthorSearch] = useState('');
  const [authorDropdownOpen, setAuthorDropdownOpen] = useState(false);
  const [authorNameMap, setAuthorNameMap] = useState({});  // id → formatted name fallback
  const authorWrapperRef = useRef(null);
  const authorDropdownRef = useRef(null);

  // Close author dropdown on scroll outside the dropdown
  useEffect(() => {
    if (!authorDropdownOpen) return;
    const close = (e) => {
      if (authorDropdownRef.current && authorDropdownRef.current.contains(e.target)) return;
      setAuthorDropdownOpen(false);
    };
    window.addEventListener('scroll', close, true);
    return () => window.removeEventListener('scroll', close, true);
  }, [authorDropdownOpen]);
  const handleAuthorToggle = (authorId) => {

    setFormData(prev => {
      const current = prev.author_ids;
      const updated = current.includes(authorId)
        ? current.filter(id => id !== authorId)
        : [...current, authorId];
      return { ...prev, author_ids: updated };
    });
  };

  const handleAuthorAdd = (authorId) => {
    setFormData(prev => {
      if (prev.author_ids.includes(authorId)) return prev;
      return { ...prev, author_ids: [...prev.author_ids, authorId] };
    });
    setAuthorSearch('');
    setAuthorDropdownOpen(false);
  };

  const handleAuthorRemove = (authorId) => {
    setFormData(prev => ({
      ...prev,
      author_ids: prev.author_ids.filter(id => id !== authorId),
    }));
  };

  const formatAuthorName = (a) => {
    const prefix = a.dynasty ? `[${a.dynasty}]` : (a.nation ? `[${a.nation}]` : '');
    const displayName = a.name_cn || a.name_en || a.name;
    return prefix ? `${prefix} ${displayName}` : displayName;
  };

  const filteredAuthors = authors.filter(a =>
    !formData.author_ids.includes(a.id) &&
    (a.name.toLowerCase().includes(authorSearch.toLowerCase()) ||
     (a.name_en && a.name_en.toLowerCase().includes(authorSearch.toLowerCase())))
  );

  const authorDropdownPos = (() => {
    if (!authorDropdownOpen || !authorWrapperRef.current) return null;
    const rect = authorWrapperRef.current.getBoundingClientRect();
    return {
      top: rect.bottom + window.scrollY,
      left: rect.left + window.scrollX,
      width: rect.width,
    };
  })();

  const handleTagAdd = (tag) => {
    if (tag.trim() && !formData.tags.includes(tag.trim())) {
      setFormData(prev => ({ ...prev, tags: [...prev.tags, tag.trim()] }));
    }
  };

  const handleTagRemove = (tag) => {
    setFormData(prev => ({ ...prev, tags: prev.tags.filter(t => t !== tag) }));
  };

  // --- Sections ---

  const heroSection = (
    <Section dark style={{ minHeight: '40vh', paddingTop: '120px' }}>
      <Container>
        <h1 style={{
          fontFamily: TYPOGRAPHY.hero.fontFamily,
          fontSize: 'clamp(32px, 5vw, 56px)',
          fontWeight: TYPOGRAPHY.hero.fontWeight,
          lineHeight: TYPOGRAPHY.hero.lineHeight,
          letterSpacing: TYPOGRAPHY.hero.letterSpacing,
          color: COLORS.white,
          textAlign: 'center',
          margin: '0 0 16px 0',
        }}>
          {mode === 'create' ? 'Add New Book' : 'Update Book'}
        </h1>
        <p style={{
          fontFamily: TYPOGRAPHY.subHeading.fontFamily,
          fontSize: TYPOGRAPHY.subHeading.fontSize,
          fontWeight: TYPOGRAPHY.subHeading.fontWeight,
          lineHeight: TYPOGRAPHY.subHeading.lineHeight,
          letterSpacing: TYPOGRAPHY.subHeading.letterSpacing,
          color: 'rgba(255, 255, 255, 0.8)',
          textAlign: 'center',
          margin: '0 0 40px 0',
        }}>
          {mode === 'create' 
            ? 'Register a new book to your personal library collection.' 
            : 'Update the details of your existing book record.'}
        </p>
        <div style={{ display: 'flex', gap: '16px', justifyContent: 'center' }}>
          <AppleButton variant="pill" onClick={() => window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' })}>
            Scroll to Form ↓
          </AppleButton>
        </div>
      </Container>
    </Section>
  );

  const basicInfoSection = (
    <Section dark style={{ padding: '80px 24px' }}>
      <Container>
        <h2 style={{
          fontFamily: TYPOGRAPHY.sectionHeading.fontFamily,
          fontSize: 'clamp(28px, 3vw, 40px)',
          fontWeight: TYPOGRAPHY.sectionHeading.fontWeight,
          lineHeight: TYPOGRAPHY.sectionHeading.lineHeight,
          letterSpacing: TYPOGRAPHY.sectionHeading.letterSpacing,
          color: COLORS.white,
          margin: '0 0 48px 0',
          textAlign: 'center',
        }}>
          Basic Information
        </h2>
        
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', 
          gap: '0 32px',
          maxWidth: '800px',
          margin: '0 auto',
        }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8 }}>
              <div style={{ flex: 1 }}>
                <AppleInput
                  label="ISBN"
                  value={formData.isbn}
                  onChange={(v) => updateField('isbn', v)}
                  placeholder="978-7-..."
                  required
                  error={errors.isbn}
                  dark
                />
              </div>
              <button
                type="button"
                onClick={handleLookupIsbn}
                disabled={isbnLookupLoading}
                style={{
                  fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Icons", "Helvetica Neue", Helvetica, Arial, sans-serif',
                  fontSize: '13px',
                  fontWeight: 600,
                  lineHeight: '1.4',
                  letterSpacing: '-0.01em',
                  padding: '12px 16px',
                  border: 'none',
                  borderRadius: '8px',
                  background: COLORS.appleBlue,
                  color: COLORS.white,
                  cursor: 'pointer',
                  whiteSpace: 'nowrap',
                  alignSelf: 'flex-end',
                  marginBottom: '8px',
                  opacity: isbnLookupLoading ? 0.6 : 1,
                }}
              >
                {isbnLookupLoading ? '...' : 'Lookup'}
              </button>
            </div>
          </div>
          
          <AppleInput
            label="Title (Chinese)"
            value={formData.title_cn}
            onChange={(v) => updateField('title_cn', v)}
            placeholder="书名"
            required
            error={errors.title_cn}
            dark
          />
          
          <AppleInput
            label="Title (Original)"
            value={formData.title}
            onChange={(v) => updateField('title', v)}
            placeholder="Original Title"
            dark
          />
          
          <AppleInput
            label="Translator"
            value={formData.translator}
            onChange={(v) => updateField('translator', v)}
            placeholder="Translator name"
            dark
          />

          <div ref={authorWrapperRef} style={{ marginBottom: '24px', width: '100%' }}>
            <label style={{
              fontFamily: TYPOGRAPHY.bodyEmphasis.fontFamily,
              fontSize: TYPOGRAPHY.bodyEmphasis.fontSize,
              fontWeight: TYPOGRAPHY.bodyEmphasis.fontWeight,
              lineHeight: TYPOGRAPHY.bodyEmphasis.lineHeight,
              letterSpacing: TYPOGRAPHY.bodyEmphasis.letterSpacing,
              color: COLORS.white,
              marginBottom: '8px',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}>
              <span>Authors {errors.author_ids && <span style={{ color: '#ff3b30', fontWeight: 400 }}>({errors.author_ids})</span>}</span>
              {isAuthenticated && (
                <button type="button" onClick={handleAddNewAuthor}
                  style={{ fontSize: '12px', color: COLORS.brightBlue, background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>
                  + New Author
                </button>
              )}
            </label>

            {/* Selected author pills */}
            {formData.author_ids.length > 0 && (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginBottom: '8px' }}>
                {formData.author_ids.map(id => {
                  const author = authors.find(a => a.id === id);
                  return (
                    <span key={id} style={{
                      padding: '4px 10px',
                      background: COLORS.appleBlue,
                      color: COLORS.white,
                      borderRadius: '980px',
                      fontFamily: TYPOGRAPHY.caption.fontFamily,
                      fontSize: TYPOGRAPHY.caption.fontSize,
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '6px',
                    }}>
                      {author ? formatAuthorName(author) : (authorNameMap[id] || `ID:${id}`)}
                      <button type="button" onClick={() => handleAuthorRemove(id)} style={{
                        background: 'none', border: 'none', color: 'rgba(255,255,255,0.7)',
                        cursor: 'pointer', padding: 0, fontSize: '14px', lineHeight: 1,
                      }}>×</button>
                    </span>
                  );
                })}
              </div>
            )}

            {/* Search input */}
            <input
              type="text"
              value={authorSearch}
              onChange={(e) => {
                setAuthorSearch(e.target.value);
                setAuthorDropdownOpen(true);
              }}
              onFocus={() => setAuthorDropdownOpen(true)}
              onBlur={(e) => setTimeout(() => setAuthorDropdownOpen(false), 150)}
              placeholder="Search authors..."
              style={{
                fontFamily: TYPOGRAPHY.body.fontFamily,
                fontSize: TYPOGRAPHY.body.fontSize,
                fontWeight: TYPOGRAPHY.body.fontWeight,
                color: COLORS.white,
                background: 'rgba(39, 39, 41, 0.6)',
                border: 'none',
                borderRadius: '8px',
                padding: '12px 16px',
                width: '100%',
                outline: 'none',
                boxSizing: 'border-box',
              }}
            />


            {/* Dropdown via portal */}
            {authorDropdownOpen && filteredAuthors.length > 0 && authorDropdownPos && createPortal(
              <div ref={authorDropdownRef} style={{
                position: 'absolute',
                top: authorDropdownPos.top,
                left: authorDropdownPos.left,
                width: authorDropdownPos.width,
                maxHeight: '200px',
                overflowY: 'auto',
                background: 'rgba(39, 39, 41, 0.95)',
                backdropFilter: 'blur(8px)',
                borderRadius: '0 0 8px 8px',
                boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
                zIndex: 9999,
              }} onMouseDown={(e) => e.preventDefault()}>
                {filteredAuthors.map(a => (
                  <div key={a.id} onClick={() => handleAuthorAdd(a.id)} style={{
                    padding: '10px 16px',
                    cursor: 'pointer',
                    fontFamily: TYPOGRAPHY.body.fontFamily,
                    fontSize: TYPOGRAPHY.body.fontSize,
                    color: COLORS.white,
                  }}>
                    {formatAuthorName(a)}
                  </div>
                ))}
              </div>,
              document.body
            )}
          </div>

          <SearchableSelect
            label="Publisher"
            value={formData.publisher_id}
            onChange={(v) => updateField('publisher_id', v)}
            options={publishers}
            placeholder="Search and select publisher..."
            dark
            onAddNew={handleAddNewPublisher}
          />

          <AppleInput
            label="Publish Date"
            value={formData.publish_date}
            onChange={(v) => updateField('publish_date', v)}
            type="date"
            dark
          />

          <SearchableSelect
            label="Category"
            value={formData.category_id}
            onChange={(v) => updateField('category_id', v)}
            options={categories}
            placeholder="Search and select category..."
            dark
            error={errors.category_id}
            onAddNew={() => {
              setCategoryFormData({ name: '', intro: '' });
              setShowCategoryModal(true);
            }}
            addNewLabel="+ New Category"
          />

          <SearchableSelect
            label="Bookshelf"
            value={formData.bookshelf_id}
            onChange={(v) => updateField('bookshelf_id', v)}
            options={bookshelves}
            placeholder="Search and select location..."
            dark
            onAddNew={handleAddNewBookshelf}
            addNewLabel="+ New Bookshelf"
          />
        </div>
      </Container>
    </Section>
  );

  const detailsSection = (
    <Section dark style={{ padding: '80px 24px' }}>
      <Container>
        <h2 style={{
          fontFamily: TYPOGRAPHY.sectionHeading.fontFamily,
          fontSize: 'clamp(28px, 3vw, 40px)',
          fontWeight: TYPOGRAPHY.sectionHeading.fontWeight,
          lineHeight: TYPOGRAPHY.sectionHeading.lineHeight,
          letterSpacing: TYPOGRAPHY.sectionHeading.letterSpacing,
          color: COLORS.white,
          margin: '0 0 48px 0',
          textAlign: 'center',
        }}>
          Physical Details
        </h2>

        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', 
          gap: '0 32px',
          maxWidth: '800px',
          margin: '0 auto',
        }}>
          <SearchableSelect
            label="Binding Type"
            value={formData.binding_type}
            onChange={(v) => updateField('binding_type', v)}
            options={BINDING_TYPES.map(name => ({ id: name, name }))}
            placeholder="精装 / 平装..."
            dark
          />

          <SearchableSelect
            label="Paper Type"
            value={formData.paper_type}
            onChange={(v) => updateField('paper_type', v)}
            options={PAPER_TYPES.map(name => ({ id: name, name }))}
            placeholder="铜版纸 / 胶版纸..."
            dark
          />

          <AppleInput
            label="Pages"
            value={formData.pages ?? ''}
            onChange={(v) => updateField('pages', v ? Number(v) : null)}
            type="number"
            placeholder="0"
            dark
          />

          <AppleInput
            label="Book Count"
            value={formData.book_count ?? ''}
            onChange={(v) => updateField('book_count', v ? Number(v) : null)}
            type="number"
            placeholder="1"
            dark
          />

          <SearchableSelect
            label="Language"
            value={formData.language}
            onChange={(v) => updateField('language', v)}
            options={LANGUAGES.map(name => ({ id: name, name }))}
            placeholder="中文 / 英文..."
            dark
          />

          <SearchableSelect
            label="Compose Type"
            value={formData.compose_type}
            onChange={(v) => updateField('compose_type', v)}
            options={COMPOSE_TYPES.map(name => ({ id: name, name }))}
            placeholder="横排 / 竖排"
            dark
          />

          <SearchableSelect
            label="Brand"
            value={formData.brand_id}
            onChange={(v) => updateField('brand_id', v)}
            options={brands}
            placeholder="Search and select brand..."
            dark
            onAddNew={handleAddNewBrand}
            addNewLabel="+ New Brand"
          />

          <SearchableSelect
            label="Book Series"
            value={formData.book_series_id}
            onChange={(v) => updateField('book_series_id', v)}
            options={series}
            placeholder="Search and select series..."
            dark
            onAddNew={handleAddNewSeries}
            addNewLabel="+ New Series"
          />
        </div>
      </Container>
    </Section>
  );

  const pricingSection = (
    <Section dark style={{ padding: '80px 24px' }}>
      <Container>
        <h2 style={{
          fontFamily: TYPOGRAPHY.sectionHeading.fontFamily,
          fontSize: 'clamp(28px, 3vw, 40px)',
          fontWeight: TYPOGRAPHY.sectionHeading.fontWeight,
          lineHeight: TYPOGRAPHY.sectionHeading.lineHeight,
          letterSpacing: TYPOGRAPHY.sectionHeading.letterSpacing,
          color: COLORS.white,
          margin: '0 0 48px 0',
          textAlign: 'center',
        }}>
          Purchase & Pricing
        </h2>

        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', 
          gap: '0 32px',
          maxWidth: '800px',
          margin: '0 auto',
        }}>
          <AppleInput
            label="Price (CNY)"
            value={formData.price ?? ''}
            onChange={(v) => updateField('price', v ? Number(v) : null)}
            type="number"
            step="0.01"
            placeholder="0.00"
            dark
          />

          <AppleInput
            label="Purchase Price (CNY)"
            value={formData.purchase_price ?? ''}
            onChange={(v) => updateField('purchase_price', v ? Number(v) : null)}
            type="number"
            step="0.01"
            placeholder="0.00"
            dark
          />

          <AppleInput
            label="Purchase Date"
            value={formData.purchase_date}
            onChange={(v) => updateField('purchase_date', v)}
            type="date"
            dark
          />

          <SearchableSelect
            label="Purchase Store"
            value={formData.purchase_store}
            onChange={(v) => updateField('purchase_store', v)}
            options={PURCHASE_STORES.map(name => ({ id: name, name }))}
            placeholder="京东自营 / 当当..."
            dark
          />

          <AppleInput
            label="Douban Score"
            value={formData.douban_score ?? ''}
            onChange={(v) => updateField('douban_score', v ? Number(v) : null)}
            step="0.1"
            min="0"
            max="10"
            placeholder="0 - 10"
            dark
          />

          <AppleInput
            label="Edition"
            value={formData.edition}
            onChange={(v) => updateField('edition', v)}
            placeholder="1st Edition"
            dark
          />

          <AppleInput
            label="Printing Info"
            value={formData.printing_info}
            onChange={(v) => updateField('printing_info', v)}
            placeholder="1st Printing"
            dark
          />

          <AppleInput
            label="Printed Number"
            value={formData.printed_number ?? ''}
            onChange={(v) => updateField('printed_number', v ? Number(v) : null)}
            type="number"
            placeholder="0"
            dark
          />
        </div>
      </Container>
    </Section>
  );

  const contentSection = (
    <Section dark style={{ padding: '80px 24px' }}>
      <Container>
        <h2 style={{
          fontFamily: TYPOGRAPHY.sectionHeading.fontFamily,
          fontSize: 'clamp(28px, 3vw, 40px)',
          fontWeight: TYPOGRAPHY.sectionHeading.fontWeight,
          lineHeight: TYPOGRAPHY.sectionHeading.lineHeight,
          letterSpacing: TYPOGRAPHY.sectionHeading.letterSpacing,
          color: COLORS.white,
          margin: '0 0 48px 0',
          textAlign: 'center',
        }}>
          Content & Metadata
        </h2>

        <div style={{ maxWidth: '800px', margin: '0 auto' }}>
          <AppleInput
            label="Thumbnail Image URL"
            value={formData.thumb_image}
            onChange={(v) => updateField('thumb_image', v)}
            placeholder="https://..."
            dark
          />
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8, marginBottom: 16, fontSize: 14, color: '#86868b', cursor: 'pointer' }}>
            <input type="checkbox" checked={formData.thumb_image === `/books/${formData.isbn}.png`}
              onChange={(e) => {
                if (e.target.checked) {
                  updateField('thumb_image', `/books/${formData.isbn}.png`);
                } else {
                  updateField('thumb_image', apiThumbUrl || '');
                }
              }}
            />
            Use Default (/books/{formData.isbn || 'ISBN'}.png)
          </label>

          <AppleInput
            label="External Link"
            value={formData.link}
            onChange={(v) => updateField('link', v)}
            placeholder="https://douban.com/..."
            dark
          />

          <AppleInput
            label="Catalog"
            value={formData.catalog}
            onChange={(v) => updateField('catalog', v)}
            placeholder="Table of contents..."
            multiline
            rows={6}
            dark
          />

          <AppleInput
            label="Introduction"
            value={formData.introduction}
            onChange={(v) => updateField('introduction', v)}
            placeholder="Book introduction..."
            multiline
            rows={6}
            dark
          />

          <AppleInput
            label="Summary"
            value={formData.summary}
            onChange={(v) => updateField('summary', v)}
            placeholder="Brief summary..."
            multiline
            rows={4}
            dark
          />

          <div style={{ marginBottom: '24px' }}>
            <label style={{
              fontFamily: TYPOGRAPHY.bodyEmphasis.fontFamily,
              fontSize: TYPOGRAPHY.bodyEmphasis.fontSize,
              fontWeight: TYPOGRAPHY.bodyEmphasis.fontWeight,
              lineHeight: TYPOGRAPHY.bodyEmphasis.lineHeight,
              letterSpacing: TYPOGRAPHY.bodyEmphasis.letterSpacing,
              color: COLORS.white,
              marginBottom: '12px',
              display: 'block',
            }}>
              Tags
            </label>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', marginBottom: '12px' }}>
              {formData.tags.map(tag => (
                <span
                  key={tag}
                  style={{
                    padding: '4px 12px',
                    background: 'rgba(38, 38, 40, 0.7)',
                    color: COLORS.white,
                    borderRadius: '980px',
                    fontFamily: TYPOGRAPHY.caption.fontFamily,
                    fontSize: TYPOGRAPHY.caption.fontSize,
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '6px',
                  }}
                >
                  {tag}
                  <button
                    type="button"
                    onClick={() => handleTagRemove(tag)}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: COLORS.white32,
                      cursor: 'pointer',
                      padding: '0',
                      fontSize: '16px',
                      lineHeight: 1,
                    }}
                  >
                    ×
                  </button>
                </span>
              ))}
            </div>
            <input
              type="text"
              placeholder="Add tag and press Enter"
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  handleTagAdd(e.currentTarget.value);
                  e.currentTarget.value = '';
                }
              }}
              style={{
                fontFamily: TYPOGRAPHY.body.fontFamily,
                fontSize: TYPOGRAPHY.body.fontSize,
                background: 'rgba(39, 39, 41, 0.6)',
                color: COLORS.white,
                border: 'none',
                borderRadius: '8px',
                padding: '12px 16px',
                width: '100%',
                outline: 'none',
                boxSizing: 'border-box',
              }}
            />
          </div>

          <div style={{ 
            display: 'flex', 
            alignItems: 'center', 
            gap: '12px',
            marginBottom: '24px',
          }}>
            <input
              type="checkbox"
              id="registered"
              checked={formData.registered}
              onChange={(e) => updateField('registered', e.target.checked)}
              style={{
                width: '20px',
                height: '20px',
                accentColor: COLORS.appleBlue,
                cursor: 'pointer',
              }}
            />
            <label 
              htmlFor="registered"
              style={{
                fontFamily: TYPOGRAPHY.body.fontFamily,
                fontSize: TYPOGRAPHY.body.fontSize,
                color: COLORS.white,
                cursor: 'pointer',
              }}
            >
              Registered in library system
            </label>
          </div>

          <div style={{ marginBottom: '24px' }}>
            <label style={{
              fontFamily: TYPOGRAPHY.bodyEmphasis.fontFamily,
              fontSize: TYPOGRAPHY.bodyEmphasis.fontSize,
              fontWeight: TYPOGRAPHY.bodyEmphasis.fontWeight,
              color: COLORS.white,
              marginBottom: '12px',
              display: 'block',
            }}>
              Read State
            </label>
            <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
              {['unread', 'reading', 'read', 'abandoned'].map(state => (
                <button
                  key={state}
                  type="button"
                  onClick={() => updateField('read_state', state)}
                  style={{
                    padding: '8px 20px',
                    borderRadius: '980px',
                    border: `1px solid ${formData.read_state === state ? COLORS.appleBlue : 'rgba(255, 255, 255, 0.2)'}`,
                    background: formData.read_state === state ? COLORS.appleBlue : 'transparent',
                    color: COLORS.white,
                    fontFamily: TYPOGRAPHY.caption.fontFamily,
                    fontSize: TYPOGRAPHY.caption.fontSize,
                    cursor: 'pointer',
                    transition: 'all 0.2s ease',
                    textTransform: 'capitalize',
                  }}
                >
                  {state}
                </button>
              ))}
            </div>
          </div>
        </div>
      </Container>
    </Section>
  );

  const submitSection = (
    <Section dark style={{ padding: '60px 24px', minHeight: 'auto' }}>
      <Container style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '24px' }}>
        {submitSuccess && (
          <div style={{
            padding: '16px 32px',
            background: 'rgba(52, 199, 89, 0.15)',
            borderRadius: '8px',
            color: '#34c759',
            fontFamily: TYPOGRAPHY.body.fontFamily,
            fontSize: TYPOGRAPHY.body.fontSize,
            fontWeight: 600,
          }}>
            ✓ Book {mode === 'create' ? 'created' : 'updated'} successfully!
          </div>
        )}
        
        <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', justifyContent: 'center' }}>
          <AppleButton 
            variant="primary" 
            type="submit" 
            disabled={isSubmitting}
            style={{ minWidth: '200px' }}
          >
            {isSubmitting ? 'Processing...' : mode === 'create' ? 'Create Book' : 'Update Book'}
          </AppleButton>
          
          <AppleButton 
            variant="pill-outline" 
            type="button"
            onClick={() => window.location.reload()}
          >
            Reset Form
          </AppleButton>
        </div>

        <p style={{
          fontFamily: TYPOGRAPHY.caption.fontFamily,
          fontSize: TYPOGRAPHY.caption.fontSize,
          color: 'rgba(255, 255, 255, 0.48)',
          letterSpacing: TYPOGRAPHY.caption.letterSpacing,
          marginTop: '16px',
        }}>
          All fields marked with <span style={{ color: COLORS.appleBlue }}>*</span> are required
        </p>
      </Container>
    </Section>
  );

  if (loadingBook) return <div style={{ padding: '40px', textAlign: 'center', color: '#666' }}>Loading book data...</div>;

  return (
    <div className="section light" style={{ 
      fontFamily: TYPOGRAPHY.fontText,
      minHeight: '100vh',
      margin: 0,
      padding: 0,
    }}>
      <Navigation />
      
      <form onSubmit={handleSubmit} noValidate>
        {heroSection}
        {basicInfoSection}
        {detailsSection}
        {pricingSection}
        {contentSection}
        {submitSection}
      </form>

      {showPublisherModal && createPortal(
        <div style={{
          position: 'fixed', inset: 0, zIndex: 1000,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          background: 'rgba(29,29,31,0.6)',
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
        }} onClick={() => setShowPublisherModal(false)}>
          <div style={{
            background: 'rgba(255,255,255,0.6)',
            backdropFilter: 'blur(30px)',
            WebkitBackdropFilter: 'blur(30px)',
            borderRadius: '20px',
            padding: '24px 28px',
            width: Math.min(560, window.innerWidth - 32),
            maxHeight: '80vh',
            overflowY: 'auto',
          }} onClick={e => e.stopPropagation()}>
            <h3 style={{
              fontFamily: TYPOGRAPHY.tileHeading.fontFamily,
              fontSize: TYPOGRAPHY.tileHeading.fontSize,
              fontWeight: TYPOGRAPHY.tileHeading.fontWeight,
              lineHeight: TYPOGRAPHY.tileHeading.lineHeight,
              color: COLORS.nearBlack,
              margin: '0 0 24px 0',
            }}>New Publisher</h3>

            <AppleInput
              label="Publisher Name"
              value={publisherFormData.name}
              onChange={(v) => setPublisherFormData(prev => ({ ...prev, name: v }))}
              required
              placeholder="Enter publisher name"
              style={{ marginBottom: '16px' }}
            />

            <AppleInput
              label="Introduction"
              value={publisherFormData.intro}
              onChange={(v) => setPublisherFormData(prev => ({ ...prev, intro: v }))}
              placeholder="Brief introduction"
              multiline
              rows={3}
              style={{ marginBottom: '16px' }}
            />

            <AppleInput
              label="Logo URL"
              value={publisherFormData.logo}
              onChange={(v) => setPublisherFormData(prev => ({ ...prev, logo: v }))}
              placeholder="https://example.com/logo.png"
              style={{ marginBottom: '24px' }}
            />

            <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
              <AppleButton
                variant="pill-outline"
                onClick={() => setShowPublisherModal(false)}
                disabled={publisherSaving}
              >
                Cancel
              </AppleButton>
              <AppleButton
                variant="pill"
                onClick={handleSavePublisher}
                disabled={publisherSaving || !publisherFormData.name.trim()}
              >
                {publisherSaving ? 'Saving...' : 'Create Publisher'}
              </AppleButton>
            </div>
          </div>
        </div>,
        document.body
      )}

      {showBookshelfModal && createPortal(
        <div style={{
          position: 'fixed', inset: 0, zIndex: 1000,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          background: 'rgba(29,29,31,0.6)',
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
        }} onClick={() => setShowBookshelfModal(false)}>
          <div style={{
            background: 'rgba(255,255,255,0.6)',
            backdropFilter: 'blur(30px)',
            WebkitBackdropFilter: 'blur(30px)',
            borderRadius: '20px',
            padding: '24px 28px',
            width: Math.min(560, window.innerWidth - 32),
            maxHeight: '80vh',
            overflowY: 'auto',
          }} onClick={e => e.stopPropagation()}>
            <h3 style={{
              fontFamily: TYPOGRAPHY.tileHeading.fontFamily,
              fontSize: TYPOGRAPHY.tileHeading.fontSize,
              fontWeight: TYPOGRAPHY.tileHeading.fontWeight,
              lineHeight: TYPOGRAPHY.tileHeading.lineHeight,
              color: COLORS.nearBlack,
              margin: '0 0 24px 0',
            }}>New Bookshelf</h3>

            <AppleInput
              label="Bookshelf Name"
              value={bookshelfFormData.name}
              onChange={(v) => setBookshelfFormData(prev => ({ ...prev, name: v }))}
              required
              placeholder="Enter bookshelf name"
              style={{ marginBottom: '16px' }}
            />

            <AppleInput
              label="Introduction"
              value={bookshelfFormData.intro}
              onChange={(v) => setBookshelfFormData(prev => ({ ...prev, intro: v }))}
              placeholder="Brief introduction"
              multiline
              rows={3}
              style={{ marginBottom: '24px' }}
            />

            <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
              <AppleButton
                variant="pill-outline"
                onClick={() => setShowBookshelfModal(false)}
                disabled={bookshelfSaving}
              >
                Cancel
              </AppleButton>
              <AppleButton
                variant="pill"
                onClick={handleSaveBookshelf}
                disabled={bookshelfSaving || !bookshelfFormData.name.trim()}
              >
                {bookshelfSaving ? 'Saving...' : 'Create Bookshelf'}
              </AppleButton>
            </div>
          </div>
        </div>,
        document.body
      )}

      {showCategoryModal && createPortal(
        <div style={{
          position: 'fixed', inset: 0, zIndex: 1000,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          background: 'rgba(29,29,31,0.6)',
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
        }} onClick={() => setShowCategoryModal(false)}>
          <div style={{
            background: 'rgba(255,255,255,0.6)',
            backdropFilter: 'blur(30px)',
            WebkitBackdropFilter: 'blur(30px)',
            borderRadius: '20px',
            padding: '24px 28px',
            width: Math.min(560, window.innerWidth - 32),
            maxHeight: '80vh',
            overflowY: 'auto',
          }} onClick={e => e.stopPropagation()}>
            <h3 style={{
              fontFamily: TYPOGRAPHY.tileHeading.fontFamily,
              fontSize: TYPOGRAPHY.tileHeading.fontSize,
              fontWeight: TYPOGRAPHY.tileHeading.fontWeight,
              lineHeight: TYPOGRAPHY.tileHeading.lineHeight,
              color: COLORS.nearBlack,
              margin: '0 0 24px 0',
            }}>New Category</h3>

            <AppleInput
              label="Category Name"
              value={categoryFormData.name}
              onChange={(v) => setCategoryFormData(prev => ({ ...prev, name: v }))}
              required
              placeholder="Enter category name"
              style={{ marginBottom: '16px' }}
            />

            <SearchableSelect
              label="Parent Category"
              value={categoryFormData.parent ? parseInt(categoryFormData.parent) : null}
              onChange={(v) => setCategoryFormData(prev => ({ ...prev, parent: v || '' }))}
              options={categories.map(c => ({ id: c.id, name: c.name }))}
              placeholder="None (root category)"
              dark
            />

            <AppleInput
              label="Introduction"
              value={categoryFormData.intro}
              onChange={(v) => setCategoryFormData(prev => ({ ...prev, intro: v }))}
              placeholder="Brief introduction"
              multiline
              rows={3}
              style={{ marginBottom: '24px' }}
            />

            <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
              <AppleButton
                variant="pill-outline"
                onClick={() => setShowCategoryModal(false)}
                disabled={categorySaving}
              >
                Cancel
              </AppleButton>
              <AppleButton
                variant="pill"
                onClick={handleSaveCategory}
                disabled={categorySaving || !categoryFormData.name.trim()}
              >
                {categorySaving ? 'Saving...' : 'Create Category'}
              </AppleButton>
            </div>
          </div>
        </div>,
        document.body
      )}

      {showBrandModal && createPortal(
        <div style={{
          position: 'fixed', inset: 0, zIndex: 1000,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          background: 'rgba(29,29,31,0.6)',
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
        }} onClick={() => setShowBrandModal(false)}>
          <div style={{
            background: 'rgba(255,255,255,0.6)',
            backdropFilter: 'blur(30px)',
            WebkitBackdropFilter: 'blur(30px)',
            borderRadius: '20px',
            padding: '24px 28px',
            width: Math.min(560, window.innerWidth - 32),
            maxHeight: '80vh',
            overflowY: 'auto',
          }} onClick={e => e.stopPropagation()}>
            <h3 style={{
              fontFamily: TYPOGRAPHY.tileHeading.fontFamily,
              fontSize: TYPOGRAPHY.tileHeading.fontSize,
              fontWeight: TYPOGRAPHY.tileHeading.fontWeight,
              lineHeight: TYPOGRAPHY.tileHeading.lineHeight,
              color: COLORS.nearBlack,
              margin: '0 0 24px 0',
            }}>New Brand</h3>

            <AppleInput
              label="Brand Name"
              value={brandFormData.name}
              onChange={(v) => setBrandFormData(prev => ({ ...prev, name: v }))}
              required
              placeholder="Enter brand name"
              style={{ marginBottom: '16px' }}
            />

            <AppleInput
              label="Introduction"
              value={brandFormData.intro}
              onChange={(v) => setBrandFormData(prev => ({ ...prev, intro: v }))}
              placeholder="Brief introduction"
              multiline
              rows={3}
              style={{ marginBottom: '24px' }}
            />

            <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
              <AppleButton
                variant="pill-outline"
                onClick={() => setShowBrandModal(false)}
                disabled={brandSaving}
              >
                Cancel
              </AppleButton>
              <AppleButton
                variant="pill"
                onClick={handleSaveBrand}
                disabled={brandSaving || !brandFormData.name.trim()}
              >
                {brandSaving ? 'Saving...' : 'Create Brand'}
              </AppleButton>
            </div>
          </div>
        </div>,
        document.body
      )}

      {showAuthorModal && createPortal(
        <div style={{
          position: 'fixed', inset: 0, zIndex: 1000,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          background: 'rgba(29,29,31,0.6)',
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
        }} onClick={() => setShowAuthorModal(false)}>
          <div style={{
            background: 'rgba(255,255,255,0.6)',
            backdropFilter: 'blur(30px)',
            WebkitBackdropFilter: 'blur(30px)',
            borderRadius: '20px',
            padding: '24px 28px',
            width: Math.min(560, window.innerWidth - 32),
            maxHeight: '80vh',
            overflowY: 'auto',
          }} onClick={e => e.stopPropagation()}>
            <h3 style={{
              fontFamily: TYPOGRAPHY.tileHeading.fontFamily,
              fontSize: TYPOGRAPHY.tileHeading.fontSize,
              fontWeight: TYPOGRAPHY.tileHeading.fontWeight,
              lineHeight: TYPOGRAPHY.tileHeading.lineHeight,
              color: COLORS.nearBlack,
              margin: '0 0 24px 0',
            }}>New Author</h3>

            <AppleInput
              label="Author Name (Required)"
              value={authorFormData.name}
              onChange={(v) => setAuthorFormData(prev => ({ ...prev, name: v }))}
              required
              placeholder="Enter author name"
              style={{ marginBottom: '16px' }}
            />

            <AppleInput
              label="Chinese Name"
              value={authorFormData.name_cn}
              onChange={(v) => setAuthorFormData(prev => ({ ...prev, name_cn: v }))}
              placeholder="中文名"
              style={{ marginBottom: '16px' }}
            />

            <div style={{ marginBottom: '16px', width: '100%' }}>
              <label style={{
                fontFamily: TYPOGRAPHY.bodyEmphasis.fontFamily,
                fontSize: TYPOGRAPHY.bodyEmphasis.fontSize,
                fontWeight: TYPOGRAPHY.bodyEmphasis.fontWeight,
                lineHeight: TYPOGRAPHY.bodyEmphasis.lineHeight,
                letterSpacing: TYPOGRAPHY.bodyEmphasis.letterSpacing,
                color: COLORS.nearBlack,
                marginBottom: '8px',
                display: 'block',
              }}>Nation</label>
              <select
                value={authorFormData.nation}
                onChange={(e) => setAuthorFormData(prev => ({ ...prev, nation: e.target.value }))}
                style={{
                  fontFamily: TYPOGRAPHY.body.fontFamily,
                  fontSize: TYPOGRAPHY.body.fontSize,
                  fontWeight: TYPOGRAPHY.body.fontWeight,
                  lineHeight: TYPOGRAPHY.body.lineHeight,
                  letterSpacing: TYPOGRAPHY.body.letterSpacing,
                  color: COLORS.nearBlack,
                  background: 'rgba(255,255,255,0.55)',
                  border: 'none',
                  borderRadius: '8px',
                  padding: '12px 16px',
                  width: '100%',
                  outline: 'none',
                  cursor: 'pointer',
                  appearance: 'none',
                  WebkitAppearance: 'none',
                  backgroundImage: `url("data:image/svg+xml,%3Csvg width='12' height='8' viewBox='0 0 12 8' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M1 1L6 6L11 1' stroke='%231d1d1f' stroke-width='1.5'/%3E%3C/svg%3E")`,
                  backgroundRepeat: 'no-repeat',
                  backgroundPosition: 'right 16px center',
                  paddingRight: '40px',
                  boxSizing: 'border-box',
                }}
              >
                {NATIONS.map(n => (
                  <option key={n} value={n}>{n}</option>
                ))}
              </select>
            </div>

            <div style={{ marginBottom: '16px', width: '100%' }}>
              <label style={{
                fontFamily: TYPOGRAPHY.bodyEmphasis.fontFamily,
                fontSize: TYPOGRAPHY.bodyEmphasis.fontSize,
                fontWeight: TYPOGRAPHY.bodyEmphasis.fontWeight,
                lineHeight: TYPOGRAPHY.bodyEmphasis.lineHeight,
                letterSpacing: TYPOGRAPHY.bodyEmphasis.letterSpacing,
                color: COLORS.nearBlack,
                marginBottom: '8px',
                display: 'block',
              }}>Dynasty</label>
              <select
                value={authorFormData.dynasty}
                onChange={(e) => setAuthorFormData(prev => ({ ...prev, dynasty: e.target.value }))}
                style={{
                  fontFamily: TYPOGRAPHY.body.fontFamily,
                  fontSize: TYPOGRAPHY.body.fontSize,
                  fontWeight: TYPOGRAPHY.body.fontWeight,
                  lineHeight: TYPOGRAPHY.body.lineHeight,
                  letterSpacing: TYPOGRAPHY.body.letterSpacing,
                  color: COLORS.nearBlack,
                  background: 'rgba(255,255,255,0.55)',
                  border: 'none',
                  borderRadius: '8px',
                  padding: '12px 16px',
                  width: '100%',
                  outline: 'none',
                  cursor: 'pointer',
                  appearance: 'none',
                  WebkitAppearance: 'none',
                  backgroundImage: `url("data:image/svg+xml,%3Csvg width='12' height='8' viewBox='0 0 12 8' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M1 1L6 6L11 1' stroke='%231d1d1f' stroke-width='1.5'/%3E%3C/svg%3E")`,
                  backgroundRepeat: 'no-repeat',
                  backgroundPosition: 'right 16px center',
                  paddingRight: '40px',
                  boxSizing: 'border-box',
                }}
              >
                <option value="">None</option>
                {DYNASTIES.map(d => (
                  <option key={d} value={d}>{d}</option>
                ))}
              </select>
            </div>

            <AppleInput
              label="Introduction"
              value={authorFormData.intro}
              onChange={(v) => setAuthorFormData(prev => ({ ...prev, intro: v }))}
              placeholder="Brief introduction"
              multiline
              rows={3}
              style={{ marginBottom: '16px' }}
            />

            <AppleInput
              label="Photo URL"
              value={authorFormData.photo}
              onChange={(v) => setAuthorFormData(prev => ({ ...prev, photo: v }))}
              placeholder="https://example.com/photo.jpg"
              style={{ marginBottom: '24px' }}
            />

            <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
              <AppleButton
                variant="pill-outline"
                onClick={() => setShowAuthorModal(false)}
                disabled={authorSaving}
              >
                Cancel
              </AppleButton>
              <AppleButton
                variant="pill"
                onClick={handleSaveAuthor}
                disabled={authorSaving || !authorFormData.name.trim()}
              >
                {authorSaving ? 'Saving...' : 'Create Author'}
              </AppleButton>
            </div>
          </div>
        </div>,
        document.body
      )}

      {showSeriesModal && createPortal(
        <div style={{
          position: 'fixed', inset: 0, zIndex: 1000,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          background: 'rgba(29,29,31,0.6)',
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
        }} onClick={() => setShowSeriesModal(false)}>
          <div style={{
            background: 'rgba(255,255,255,0.6)',
            backdropFilter: 'blur(30px)',
            WebkitBackdropFilter: 'blur(30px)',
            borderRadius: '20px',
            padding: '24px 28px',
            width: Math.min(560, window.innerWidth - 32),
            maxHeight: '80vh',
            overflowY: 'auto',
          }} onClick={e => e.stopPropagation()}>
            <h3 style={{
              fontFamily: TYPOGRAPHY.tileHeading.fontFamily,
              fontSize: TYPOGRAPHY.tileHeading.fontSize,
              fontWeight: TYPOGRAPHY.tileHeading.fontWeight,
              lineHeight: TYPOGRAPHY.tileHeading.lineHeight,
              color: COLORS.nearBlack,
              margin: '0 0 24px 0',
            }}>New Series</h3>

            <AppleInput
              label="Series Name"
              value={seriesFormData.name}
              onChange={(v) => setSeriesFormData(prev => ({ ...prev, name: v }))}
              required
              placeholder="Enter series name"
              style={{ marginBottom: '16px' }}
            />

            <AppleInput
              label="Introduction"
              value={seriesFormData.intro}
              onChange={(v) => setSeriesFormData(prev => ({ ...prev, intro: v }))}
              placeholder="Brief introduction"
              multiline
              rows={3}
              style={{ marginBottom: '24px' }}
            />

            <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
              <AppleButton
                variant="pill-outline"
                onClick={() => setShowSeriesModal(false)}
                disabled={seriesSaving}
              >
                Cancel
              </AppleButton>
              <AppleButton
                variant="pill"
                onClick={handleSaveSeries}
                disabled={seriesSaving || !seriesFormData.name.trim()}
              >
                {seriesSaving ? 'Saving...' : 'Create Series'}
              </AppleButton>
            </div>
          </div>
        </div>,
        document.body
      )}


      <footer style={{
        background: COLORS.pureBlack,
        color: 'rgba(255, 255, 255, 0.48)',
        padding: '40px 24px',
        textAlign: 'center',
        fontFamily: TYPOGRAPHY.micro.fontFamily,
        fontSize: TYPOGRAPHY.micro.fontSize,
        letterSpacing: TYPOGRAPHY.micro.letterSpacing,
      }}>
        <Container>
          <p style={{ margin: '0 0 8px 0' }}>Personal Library Management System</p>
          <p style={{ margin: 0, opacity: 0.6 }}>Designed with Apple Design System principles</p>
        </Container>
      </footer>
    </div>
  );
}

export default BookFormPage;
