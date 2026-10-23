import { useCallback, useEffect, useState } from 'react';
import { adminFetchInvoices, adminFetchSepayTransactions, extractPage } from '../../api/payments.js';
import { getErrorMessage } from '../../api/client.js';
import { formatDateTime, formatDate } from '../../utils.js';
import '../../pages/nangcap.css'; // dùng chung inv-badge + formatVnd
import { formatVnd, PLAN_LABELS } from '../../components/MyInvoices.jsx';
import './Admin.css';

// F-044 — Admin xem hóa đơn + giao dịch SePay (READ-ONLY, v1).
// Không có nút thao tác: UNMATCHED xử lý tay qua DB (DOC-13 §3.5).
// Contract: GET /api/admin/payments/invoices?status=&userId=&page=&size=
//           GET /api/admin/payments/sepay/transactions?status=&page=&size=

const PAGE_SIZE = 20;

const INVOICE_STATUSES = ['', 'PENDING', 'PAID', 'EXPIRED', 'CANCELLED'];
const INVOICE_STATUS_VI = { PENDING: 'Đang chờ', PAID: 'Đã thanh toán', EXPIRED: 'Hết hạn', CANCELLED: 'Đã hủy' };
const SEPAY_STATUSES = ['', 'PENDING', 'MATCHED', 'UNMATCHED'];
const SEPAY_STATUS_VI = { PENDING: 'Chờ xử lý', MATCHED: 'Đã khớp', UNMATCHED: 'Chưa khớp' };
const SEPAY_STATUS_CLASS = { PENDING: 'st-pending', MATCHED: 'st-paid', UNMATCHED: 'st-sepay-unmatched' };

function truncate(s, n = 24) {
  if (!s) return '—';
  return s.length > n ? `${s.slice(0, n)}…` : s;
}

export default function AdminPayments() {
  const [view, setView] = useState('invoices'); // 'invoices' | 'transactions'
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, size: PAGE_SIZE };
      if (status) params.status = status;
      const data =
        view === 'invoices'
          ? await adminFetchInvoices(params)
          : await adminFetchSepayTransactions(params);
      const { items, totalPages: tp } = extractPage(data, PAGE_SIZE);
      setRows(items);
      setTotalPages(Math.max(tp, 1));
      setError('');
    } catch (e) {
      setError(getErrorMessage(e, 'Không tải được dữ liệu thanh toán.'));
    } finally {
      setLoading(false);
    }
  }, [view, status, page]);

  useEffect(() => {
    load();
  }, [load]);

  const switchView = (v) => {
    setView(v);
    setStatus('');
    setPage(0);
  };

  return (
    <div className="admin-payments">
      <h2>Thanh toán</h2>
      <p className="muted">
        Chỉ xem — không có thao tác ở v1. Giao dịch <strong>chưa khớp</strong> được xử lý
        thủ công qua DB (hoàn tiền / hướng dẫn user chuyển lại đúng nội dung).
      </p>

      <div className="admin-pay-tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={view === 'invoices'}
          className={`btn ${view === 'invoices' ? 'btn-primary' : 'btn-outline'}`}
          onClick={() => switchView('invoices')}
        >
          Hóa đơn
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={view === 'transactions'}
          className={`btn ${view === 'transactions' ? 'btn-primary' : 'btn-outline'}`}
          onClick={() => switchView('transactions')}
        >
          Giao dịch SePay
        </button>
      </div>

      <div className="admin-pay-filter">
        <label>
          Trạng thái:{' '}
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
            {(view === 'invoices' ? INVOICE_STATUSES : SEPAY_STATUSES).map((s) => (
              <option key={s} value={s}>
                {s === '' ? 'Tất cả' : view === 'invoices' ? INVOICE_STATUS_VI[s] : SEPAY_STATUS_VI[s]}
              </option>
            ))}
          </select>
        </label>
      </div>

      {loading && <p className="muted">Đang tải…</p>}
      {error && <div className="alert alert-error">{error}</div>}

      {!loading && !error && view === 'invoices' && (
        <InvoiceTable rows={rows} page={page} totalPages={totalPages} onPage={setPage} />
      )}
      {!loading && !error && view === 'transactions' && (
        <SepayTable rows={rows} page={page} totalPages={totalPages} onPage={setPage} />
      )}
    </div>
  );
}

function Pager({ page, totalPages, onPage }) {
  if (totalPages <= 1) return null;
  return (
    <div className="admin-pay-pager">
      <button type="button" className="btn btn-sm btn-outline" disabled={page <= 0} onClick={() => onPage(page - 1)}>
        ← Trước
      </button>
      <span className="muted">Trang {page + 1} / {totalPages}</span>
      <button type="button" className="btn btn-sm btn-outline" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
        Sau →
      </button>
    </div>
  );
}

function InvoiceTable({ rows, page, totalPages, onPage }) {
  if (rows.length === 0) return <p className="muted">Chưa có hóa đơn nào.</p>;
  return (
    <>
      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Mã hóa đơn</th>
              <th>User</th>
              <th>Gói</th>
              <th>Số tiền</th>
              <th>Trạng thái</th>
              <th>Tạo lúc</th>
              <th>Thanh toán lúc</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((inv) => (
              <tr key={inv.code || inv.id}>
                <td><span className="inv-code">{inv.code}</span></td>
                {/* BE có thể trả userId / userEmail / user — đọc phòng thủ, không đoán field. */}
                <td className="admin-pay-user" title={inv.userId || ''}>
                  {inv.userEmail || inv.user?.email || truncate(inv.userId, 12)}
                </td>
                <td>{PLAN_LABELS[inv.planType] || inv.planType}</td>
                <td>{formatVnd(inv.amount)}</td>
                <td>
                  <span className={`inv-badge ${inv.status === 'PENDING' ? 'st-pending' : inv.status === 'PAID' ? 'st-paid' : 'st-expired'}`}>
                    {INVOICE_STATUS_VI[inv.status] || inv.status}
                  </span>
                </td>
                <td>{formatDateTime(inv.createdAt)}</td>
                <td>{inv.paidAt ? formatDateTime(inv.paidAt) : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pager page={page} totalPages={totalPages} onPage={onPage} />
    </>
  );
}

function SepayTable({ rows, page, totalPages, onPage }) {
  if (rows.length === 0) return <p className="muted">Chưa có giao dịch SePay nào.</p>;
  return (
    <>
      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead>
            <tr>
              <th>ID giao dịch</th>
              <th>Số tiền</th>
              <th>Nội dung CK</th>
              <th>Người gửi</th>
              <th>Trạng thái</th>
              <th>Ghi chú</th>
              <th>Ngày giao dịch</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((tx) => (
              <tr key={tx.bankTransactionId || tx.id}>
                <td className="admin-pay-user" title={tx.bankTransactionId || ''}>
                  {truncate(tx.bankTransactionId, 14)}
                </td>
                <td>{formatVnd(tx.amount)}</td>
                <td title={tx.transferContent || ''}>{truncate(tx.transferContent, 20)}</td>
                <td>{truncate(tx.senderAccount, 18)}</td>
                <td>
                  <span className={`inv-badge ${SEPAY_STATUS_CLASS[tx.status] || 'st-pending'}`}>
                    {SEPAY_STATUS_VI[tx.status] || tx.status}
                  </span>
                </td>
                {/* note là mã nội bộ (CODE_NOT_FOUND / AMOUNT_MISMATCH / ...) — chỉ admin thấy. */}
                <td>{tx.note ? <code className="admin-pay-note">{tx.note}</code> : '—'}</td>
                <td>{tx.transactionDate ? formatDateTime(tx.transactionDate) : formatDate(tx.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pager page={page} totalPages={totalPages} onPage={onPage} />
    </>
  );
}
