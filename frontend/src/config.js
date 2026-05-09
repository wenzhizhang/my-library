// src/config.js
const env = process.env.REACT_APP_ENV || process.env.NODE_ENV || 'development';

// Base path for routing - for production deployment
export const BASE_PATH = process.env.REACT_APP_BASE_PATH || (env === 'production' ? '/my-library' : '');

// Library base path for navigation
export const LIBRARY_PATH = process.env.REACT_APP_LIBRARY_PATH || '/my-library';

// API base URL
export const API_BASE_URL = '/api';

// Media base URL
export const MEDIA_BASE_URL = 'https://zhangwenzhi-1315027057.cos.ap-guangzhou.myqcloud.com/media';