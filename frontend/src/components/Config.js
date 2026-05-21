// Always use relative path — works via nginx proxy in dev & prod,
// avoids mixed-content issues on HTTPS.
const API_BASE_URL = '/api';

const MEDIA_BASE_URL = 'https://zhangwenzhi-1315027057.cos.ap-guangzhou.myqcloud.com/media';

export { API_BASE_URL, MEDIA_BASE_URL };
