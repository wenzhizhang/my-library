const env = process.env.REACT_APP_ENV || process.env.NODE_ENV || 'development';

const API_BASE_URL = env === 'development' ? 'http://localhost:8000/api' : '/api';
const MEDIA_BASE_URL = 'https://zhangwenzhi-1315027057.cos.ap-guangzhou.myqcloud.com/media';

export { API_BASE_URL, MEDIA_BASE_URL };