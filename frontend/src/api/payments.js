import client from './client.js';

// F-044 — Thanh toán qua SePay (Phase D). Field khớp DOC-13 §3.5 từng ký tự.
// Base URL đã là '/api' (vite proxy sẵn có) — không thêm prefix mới.

// Tạo hóa đơn: POST /api/payments/invoices { planType: 'MONTHLY' | 'YEARLY' }.
// Response 201: { code, planType, amount, status, createdAt, expiresAt,
//   transferInstructions: { bankName, accountNumber, accountHolder, amount, transferContent, note } }
export async function createInvoice(planType) {
  const { data } = await client.post('/payments/invoices', { planType });
  return data;
}

// Hóa đơn của chính user (mới nhất trước). status tùy chọn: PENDING | PAID | EXPIRED | CANCELLED.
export async function fetchMyInvoices(status) {
  const { data } = await client.get('/payments/invoices', {
    params: status ? { status } : {},
  });
  return data;
}

// Hủy hóa đơn PENDING của chính mình → { code, status: 'CANCELLED' }.
export async function cancelInvoice(code) {
  const { data } = await client.delete(`/payments/invoices/${code}`);
  return data;
}

// --- Admin (read-only, ROLE_ADMIN) — DOC-13 §3.5. Không có write operation ở v1. ---

// GET /api/admin/payments/invoices?status=&userId=&page=&size= (phân trang, mới nhất trước)
export async function adminFetchInvoices(params = {}) {
  const { data } = await client.get('/admin/payments/invoices', { params });
  return data;
}

// GET /api/admin/payments/sepay/transactions?status=&page=&size=
export async function adminFetchSepayTransactions(params = {}) {
  const { data } = await client.get('/admin/payments/sepay/transactions', { params });
  return data;
}

// Hỗ trợ đọc kết quả phân trang của BE: PageResponse { items, total, page, size }
// (convention của mọi endpoint admin hiện có) + mảng thuần + Spring Page.
export function extractPage(data, size = 20) {
  if (Array.isArray(data)) return { items: data, page: 0, totalPages: 1 };
  const items = Array.isArray(data?.items) ? data.items
    : Array.isArray(data?.content) ? data.content : [];
  const total = Number.isInteger(data?.total) ? data.total : items.length;
  const pageSize = Number.isInteger(data?.size) && data.size > 0 ? data.size : size;
  const page = Number.isInteger(data?.page) ? data.page
    : Number.isInteger(data?.number) ? data.number : 0;
  return { items, page, totalPages: Math.max(1, Math.ceil(total / pageSize)) };
}
