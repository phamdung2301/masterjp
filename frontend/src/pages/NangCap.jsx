import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { cancelInvoice, createInvoice, fetchMyInvoices } from '../api/payments.js';
import { getErrorMessage } from '../api/client.js';
import { formatDate, formatDateTime } from '../utils.js';
import CopyButton from '../components/CopyButton.jsx';
import MyInvoices, { InvoiceStatusBadge, formatVnd, PLAN_LABELS } from '../components/MyInvoices.jsx';
import { CheckIcon, CrownIcon, WarnIcon } from '../components/icons.jsx';
import './nangcap.css';

// F-044 — Trang nâng cấp Premium (/ca-nhan/nang-cap).
// Luồng: chọn gói MONTHLY/YEARLY → POST /api/payments/invoices → hiện hướng dẫn
// chuyển khoản (nội dung = mã hóa đơn, nút sao chép lớn + cảnh báo ghi đúng nội dung)
// → trạng thái PENDING + poll 15s/lần tối đa ~5 phút, sau đó nút "Kiểm tra lại" thủ công.
// Kích hoạt Premium hoàn toàn tự động qua SePay webhook — FE không bao giờ tự set PAID.

const POLL_INTERVAL_MS = 15000;
const POLL_MAX_ROUNDS = 20; // ~5 phút

const PLANS = [
  {
    type: 'MONTHLY',
    name: 'Premium tháng',
    price: '49.000đ',
    per: '/ tháng',
    desc: 'Học không giới hạn từ mới, luyện nghe + gõ từ, thống kê chi tiết.',
    hot: false,
  },
  {
    type: 'YEARLY',
    name: 'Premium năm',
    price: '390.000đ',
    per: '/ năm · tiết kiệm 34%',
    desc: 'Mọi quyền lợi Premium tháng, huy hiệu Người ủng hộ, ưu tiên tính năng mới.',
    hot: true,
    tag: 'TIẾT KIỆM 34%',
  },
];

function formatCountdown(iso) {
  if (!iso) return '';
  const ms = new Date(iso).getTime() - Date.now();
  if (Number.isNaN(ms) || ms <= 0) return 'đã hết hạn';
  const h = Math.floor(ms / 3600000);
  const m = Math.floor((ms % 3600000) / 60000);
  if (h > 0) return `còn ${h} giờ ${m} phút`;
  return `còn ${m} phút`;
}

function findPending(list) {
  if (!Array.isArray(list)) return null;
  const now = Date.now();
  return (
    list.find(
      (i) => i.status === 'PENDING' && (!i.expiresAt || new Date(i.expiresAt).getTime() > now)
    ) || null
  );
}

export default function NangCap() {
  const { user, refreshUser } = useAuth();
  const [planType, setPlanType] = useState('YEARLY');
  const [invoice, setInvoice] = useState(null);
  const [loading, setLoading] = useState(true); // tải invoice PENDING hiện có lúc vào trang
  const [creating, setCreating] = useState(false);
  const [polling, setPolling] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [error, setError] = useState('');
  const pollRounds = useRef(0);

  const isPremium = user?.plan === 'PREMIUM';

  // Lấy trạng thái hóa đơn mới nhất từ server (dùng cho poll + nút kiểm tra thủ công).
  const checkInvoiceStatus = useCallback(
    async (code) => {
      const list = await fetchMyInvoices();
      const found = (Array.isArray(list) ? list : []).find((i) => i.code === code);
      if (found) {
        setInvoice(found);
        if (found.status === 'PAID') refreshUser(); // cập nhật plan trong header ngay
      }
      return found;
    },
    [refreshUser]
  );

  // Vào trang: nếu đang có invoice PENDING chưa hết hạn → hiện thẳng bước chuyển khoản
  // (DOC-13 §3.5: FE hiển thị invoice mới nhất chưa hết hạn).
  useEffect(() => {
    let cancelled = false;
    fetchMyInvoices()
      .then((list) => {
        if (!cancelled) setInvoice(findPending(list));
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Poll 15s/lần sau khi tạo hóa đơn, tối đa ~5 phút → dừng, hiện nút kiểm tra thủ công.
  useEffect(() => {
    const code = invoice?.code;
    if (!invoice || invoice.status !== 'PENDING' || !code) {
      setPolling(false);
      return;
    }
    pollRounds.current = 0;
    setPolling(true);
    const id = setInterval(async () => {
      pollRounds.current += 1;
      let done = false;
      try {
        const found = await checkInvoiceStatus(code);
        done = !found || found.status !== 'PENDING';
      } catch {
        // Lỗi mạng: bỏ qua kỳ này, thử lại kỳ sau — không làm gián đoạn việc chờ.
      }
      if (done || pollRounds.current >= POLL_MAX_ROUNDS) {
        clearInterval(id);
        setPolling(false);
      }
    }, POLL_INTERVAL_MS);
    return () => clearInterval(id);
  }, [invoice?.code, checkInvoiceStatus]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleCreate = async () => {
    setCreating(true);
    setError('');
    try {
      const data = await createInvoice(planType); // 201 { code, planType, amount, ..., transferInstructions }
      setInvoice(data);
    } catch (e) {
      setError(getErrorMessage(e, 'Không tạo được hóa đơn, vui lòng thử lại.'));
    } finally {
      setCreating(false);
    }
  };

  const handleManualCheck = async () => {
    if (!invoice) return;
    setRefreshing(true);
    try {
      await checkInvoiceStatus(invoice.code);
    } catch {
      setError('Không kiểm tra được trạng thái lúc này, vui lòng thử lại sau.');
    } finally {
      setRefreshing(false);
    }
  };

  const handleCancel = async () => {
    if (!invoice) return;
    // eslint-disable-next-line no-restricted-globals
    if (!confirm('Hủy hóa đơn này? Bạn sẽ cần tạo hóa đơn mới nếu vẫn muốn nâng cấp.')) return;
    setCancelling(true);
    try {
      await cancelInvoice(invoice.code);
      setInvoice(null);
    } catch (e) {
      setError(getErrorMessage(e, 'Không hủy được hóa đơn, vui lòng thử lại.'));
    } finally {
      setCancelling(false);
    }
  };

  if (loading) {
    return (
      <div className="page nangcap">
        <p className="muted">Đang tải…</p>
      </div>
    );
  }

  return (
    <div className="page nangcap">
      <Link to="/ca-nhan" className="nangcap-back">← Quay lại Cá nhân</Link>
      <h1>
        <CrownIcon size={26} /> Nâng cấp Premium
      </h1>

      {isPremium && (
        <div className="alert alert-success nangcap-premium-note">
          <CheckIcon size={18} />
          <div>
            <strong>Bạn đang dùng Premium</strong>
            {user?.planExpiresAt && <> — có hiệu lực đến {formatDate(user.planExpiresAt)}</>}.
            Mua thêm gói bên dưới để <strong>gia hạn cộng dồn</strong> (không mất ngày đã trả).
          </div>
        </div>
      )}

      {error && <div className="alert alert-error">{error}</div>}

      {!invoice && (
        <section className="nangcap-plans" aria-label="Chọn gói Premium">
          {PLANS.map((p) => (
            <button
              key={p.type}
              type="button"
              className={`nangcap-plan${planType === p.type ? ' selected' : ''}${p.hot ? ' hot' : ''}`}
              onClick={() => setPlanType(p.type)}
              aria-pressed={planType === p.type}
            >
              {p.tag && <span className="nangcap-tag">{p.tag}</span>}
              <div className="nangcap-plan-name">{p.name}</div>
              <div className="nangcap-plan-price">{p.price}<span>{p.per}</span></div>
              <p className="nangcap-plan-desc">{p.desc}</p>
              <span className="nangcap-radio" aria-hidden="true" />
            </button>
          ))}
          <button
            type="button"
            className="btn btn-primary btn-lg btn-block"
            onClick={handleCreate}
            disabled={creating}
          >
            {creating ? 'Đang tạo hóa đơn…' : `Tạo hóa đơn — ${PLANS.find((p) => p.type === planType).price}`}
          </button>
          <p className="muted nangcap-note">
            Thanh toán bằng chuyển khoản ngân hàng. Hệ thống tự động kích hoạt Premium
            trong vài phút sau khi nhận được tiền — bạn không cần nhắn tin cho ai.
          </p>
        </section>
      )}

      {invoice && invoice.status === 'PENDING' && (
        <TransferInstructions
          invoice={invoice}
          polling={polling}
          refreshing={refreshing}
          cancelling={cancelling}
          onManualCheck={handleManualCheck}
          onCancel={handleCancel}
        />
      )}

      {invoice && invoice.status === 'PAID' && (
        <section className="nangcap-success" aria-live="polite">
          <span className="jp-badge matcha" style={{ width: 64, height: 64, fontSize: 32, borderRadius: 20 }}>賀</span>
          <h2>Thanh toán thành công!</h2>
          <p>
            Chào mừng bạn đến với Premium — hóa đơn <strong>{invoice.code}</strong> (
            {PLAN_LABELS[invoice.planType] || invoice.planType} · {formatVnd(invoice.amount)}) đã được xác nhận.
            {user?.planExpiresAt && (
              <> Gói của bạn có hiệu lực đến <strong>{formatDate(user.planExpiresAt)}</strong>.</>
            )}
          </p>
          <Link to="/hoc" className="btn btn-primary btn-lg">Bắt đầu học ngay</Link>
        </section>
      )}

      {invoice && (invoice.status === 'EXPIRED' || invoice.status === 'CANCELLED') && (
        <section className="nangcap-dead">
          <h2>Hóa đơn {invoice.status === 'EXPIRED' ? 'đã hết hạn' : 'đã được hủy'}</h2>
          <p className="muted">
            {invoice.status === 'EXPIRED'
              ? 'Hóa đơn chỉ có hiệu lực 24 giờ. Bạn tạo hóa đơn mới để tiếp tục nhé — Tanu-chan chờ bạn!'
              : 'Bạn đã hủy hóa đơn này. Muốn nâng cấp lúc nào, cứ tạo hóa đơn mới nhé!'}
          </p>
          <button type="button" className="btn btn-primary" onClick={() => setInvoice(null)}>
            Tạo hóa đơn mới
          </button>
        </section>
      )}

      <section className="nangcap-history" aria-label="Hóa đơn của tôi">
        <h2>Hóa đơn của tôi</h2>
        <MyInvoices />
      </section>
    </div>
  );
}

// Bước 2: hướng dẫn chuyển khoản + trạng thái PENDING (poll).
function TransferInstructions({ invoice, polling, refreshing, cancelling, onManualCheck, onCancel }) {
  const ti = invoice.transferInstructions || {};

  return (
    <section className="nangcap-transfer" aria-label="Hướng dẫn chuyển khoản">
      <h2>Chuyển khoản để kích hoạt Premium</h2>
      <ol className="nangcap-steps">
        <li>Mở app ngân hàng, chuyển <strong>đúng số tiền</strong> đến tài khoản bên dưới.</li>
        <li>
          <strong>Nội dung chuyển khoản:</strong> ghi đúng mã{' '}
          <strong className="nangcap-code-inline">{ti.transferContent || invoice.code}</strong>{' '}
          (bấm nút sao chép cho chắc).
        </li>
        <li>Xong — không cần nhắn ai, hệ thống tự kích hoạt Premium trong vài phút.</li>
      </ol>

      <div className="nangcap-bank-card">
        <div className="nangcap-bank-row">
          <span className="muted">Ngân hàng</span>
          <strong>{ti.bankName || '—'}</strong>
        </div>
        <div className="nangcap-bank-row">
          <span className="muted">Số tài khoản</span>
          <strong className="nangcap-mono">{ti.accountNumber || '—'}</strong>
          {ti.accountNumber && <CopyButton text={ti.accountNumber} label="Sao chép STK" />}
        </div>
        <div className="nangcap-bank-row">
          <span className="muted">Chủ tài khoản</span>
          <strong>{ti.accountHolder || '—'}</strong>
        </div>
        <div className="nangcap-bank-row">
          <span className="muted">Số tiền</span>
          <strong className="nangcap-amount">{formatVnd(ti.amount ?? invoice.amount)}</strong>
        </div>
      </div>

      <div className="nangcap-content-card">
        <span className="muted">Nội dung chuyển khoản</span>
        <div className="nangcap-content-code">{ti.transferContent || invoice.code}</div>
        <CopyButton text={ti.transferContent || invoice.code} label="Sao chép nội dung" big />
      </div>

      <div className="alert nangcap-warn" role="alert">
        <WarnIcon size={20} />
        <div>
          <strong>Nhớ ghi đúng nội dung chuyển khoản.</strong> Hệ thống chỉ tự nhận ra giao dịch
          của bạn khi nội dung khớp mã hóa đơn. Thiếu hoặc sai nội dung, bạn sẽ phải chờ xử lý thủ công.
        </div>
      </div>

      {ti.note && <p className="muted nangcap-note">{ti.note}</p>}

      <div className="nangcap-status" aria-live="polite">
        <div className="nangcap-status-head">
          <InvoiceStatusBadge status={invoice.status} />
          <span className="muted">Hóa đơn hết hạn {formatCountdown(invoice.expiresAt)} ({formatDateTime(invoice.expiresAt)})</span>
        </div>
        <p className="nangcap-status-hint">
          <strong>Bạn đã chuyển khoản rồi à? Tuyệt!</strong> Hệ thống đang chờ ngân hàng báo về —
          thường chỉ mất vài phút. Trang này tự kiểm tra mỗi 15 giây, bạn cứ để yên là được.
        </p>
        <div className="nangcap-status-actions">
          {polling ? (
            <span className="muted nangcap-polling" aria-label="Đang tự động kiểm tra">
              <span className="nangcap-spinner" aria-hidden="true" /> Đang tự động kiểm tra…
            </span>
          ) : (
            <button type="button" className="btn btn-outline" onClick={onManualCheck} disabled={refreshing}>
              {refreshing ? 'Đang kiểm tra…' : 'Tôi đã chuyển khoản — kiểm tra lại'}
            </button>
          )}
          <button type="button" className="btn btn-ghost danger" onClick={onCancel} disabled={cancelling}>
            {cancelling ? 'Đang hủy…' : 'Hủy hóa đơn'}
          </button>
        </div>
      </div>
    </section>
  );
}

