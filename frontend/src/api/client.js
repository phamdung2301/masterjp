// API client — axios, baseURL '/api' qua Vite proxy (không hardcode localhost).
// Token: localStorage key 'mjp_access_token', header Authorization: Bearer {accessToken}.
// Interceptor 401 → chuyển về /dang-nhap.
import axios from 'axios';

export const TOKEN_KEY = 'mjp_access_token';

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
});

client.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

client.interceptors.response.use(
  (res) => res,
  (error) => {
    // F-045: backend trả 429 FREEMIUM_LIMIT (vượt 20 từ mới/ngày) hoặc DAILY_LIMIT
    // (hết quota AI) → mở PaywallModal washi thay vì toast lỗi thô.
    // PaywallModalHost (đặt trong App.jsx) lắng nghe event này và render modal.
    if (error.response && error.response.status === 429) {
      const data = error.response.data || {};
      if (data.code === 'FREEMIUM_LIMIT' || data.code === 'DAILY_LIMIT') {
        window.dispatchEvent(
          new CustomEvent('mjp:open-paywall', {
            detail: { code: data.code, limit: data.limit, resetAt: data.resetAt },
          })
        );
      }
    }
    if (error.response && error.response.status === 401) {
      localStorage.removeItem(TOKEN_KEY);
      if (!window.location.pathname.startsWith('/dang-nhap')) {
        window.location.href = '/dang-nhap';
      }
    }
    return Promise.reject(error);
  }
);

export function getErrorMessage(error, fallback = 'Đã xảy ra lỗi, vui lòng thử lại.') {
  const data = error?.response?.data;
  if (typeof data?.message === 'string' && data.message.trim()) return data.message;
  if (typeof data?.error === 'string' && data.error.trim()) return data.error;
  return fallback;
}

export default client;
