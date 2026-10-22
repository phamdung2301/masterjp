import { useEffect, useState } from 'react';
import { adminFetchAuditLogs } from '../../api/admin.js';
import { getErrorMessage } from '../../api/client.js';
import { formatDateTime } from '../../utils.js';

// Nhật ký thao tác của admin trên nội dung (F-030–F-033, F-036 ghi audit_logs).
export default function AdminAudit() {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    adminFetchAuditLogs()
      .then((data) => setLogs(Array.isArray(data) ? data : data.items || []))
      .catch((e) => setError(getErrorMessage(e, 'Không tải được nhật ký.')))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <h2>Nhật ký thao tác</h2>
      {error && <div className="alert alert-error">{error}</div>}
      {loading ? <p className="muted">Đang tải…</p> : (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead><tr><th>Thời gian</th><th>Admin</th><th>Hành động</th><th>Đối tượng</th><th>Chi tiết</th></tr></thead>
            <tbody>
              {logs.map((l, i) => (
                <tr key={l.id || i}>
                  <td>{formatDateTime(l.createdAt)}</td>
                  <td>{l.adminEmail || l.adminId}</td>
                  <td>{l.action}</td>
                  <td>{l.targetType}{l.targetId ? `/${l.targetId}` : ''}</td>
                  <td className="audit-diff">{l.diff ? JSON.stringify(l.diff) : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
