import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchMyInvoices } from '../api/payments.js';
import { getErrorMessage } from '../api/client.js';
import { formatDateTime } from '../utils.js';
import { CrownIcon } from './icons.jsx';
import '../pages/nangcap.css';

// F-044 — "Hóa đơn của tôi" trong tab Cá nhân: badge trạng thái + số tiền + ngày tạo.
export const INVOICE_STATUS_LABELS = {
  PENDING: 'Đang chờ thanh toán',
  PAID: 'Đã thanh toán',
  EXPIRED: 'Hết hạn',
  CANCELLED: 'Đã hủy',
};

export const INVOICE_STATUS_CLASSES = {
  PENDING: 'st-pending',
  PAID: 'st-paid',
  EXPIRED: 'st-expired',
  CANCELLED: 'st-cancelled',
};

export const PLAN_LABELS = {
  MONTHLY: 'Gói tháng',
  YEARLY: 'Gói năm',
};

export function formatVnd(amount) {
  if (amount == null) return '—';
  return `${new Intl.NumberFormat('vi-VN').format(amount)}đ`;
}

export function InvoiceStatusBadge({ status }) {
  return (
    <span className={`inv-badge ${INVOICE_STATUS_CLASSES[status] || 'st-expired'}`}>
      {INVOICE_STATUS_LABELS[status] || status}
    </span>
  );
}

export default function MyInvoices() {
  const [invoices, setInvoices] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const data = await fetchMyInvoices();
      setInvoices(Array.isArray(data) ? data : []);
      setError('');
    } catch (e) {
      setError(getErrorMessage(e, 'Không tải được danh sách hóa đơn.'));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (invoices === null && !error) return <p className="muted">Đang tải hóa đơn…</p>;
  if (error) return <div className="alert alert-error">{error}</div>;
  if (invoices.length === 0) {
    return (
      <div className="inv-empty">
        <p className="muted">Bạn chưa có hóa đơn nào.</p>
        <p className="muted">Khi nâng cấp Premium, hóa đơn sẽ hiện ở đây để bạn theo dõi trạng thái thanh toán.</p>
        <Link to="/ca-nhan/nang-cap" className="btn btn-outline btn-sm">
          <CrownIcon size={16} /> Xem các gói Premium
        </Link>
      </div>
    );
  }

  return (
    <ul className="inv-list">
      {invoices.map((inv) => (
        <li key={inv.code} className="inv-item">
          <div className="inv-main">
            <span className="inv-code">{inv.code}</span>
            <span className="muted">· {PLAN_LABELS[inv.planType] || inv.planType}</span>
          </div>
          <div className="inv-meta">
            <strong>{formatVnd(inv.amount)}</strong>
            <span className="muted">{formatDateTime(inv.createdAt)}</span>
            <InvoiceStatusBadge status={inv.status} />
          </div>
          {inv.status === 'PENDING' && (
            <Link to="/ca-nhan/nang-cap" className="btn btn-sm btn-outline">
              Tiếp tục thanh toán
            </Link>
          )}
        </li>
      ))}
    </ul>
  );
}
