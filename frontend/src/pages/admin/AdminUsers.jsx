import { useEffect, useState } from 'react';
import { adminFetchUsers, adminUpdateUserPlan } from '../../api/admin.js';
import { getErrorMessage } from '../../api/client.js';
import { formatDate } from '../../utils.js';

// US-015 (F-036): admin gán/thu hồi gói PREMIUM (có/không thời hạn).
export default function AdminUsers() {
  const [users, setUsers] = useState([]);
  const [q, setQ] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editingPlan, setEditingPlan] = useState(null); // userId
  const [plan, setPlan] = useState('PREMIUM');
  const [expiresAt, setExpiresAt] = useState('');

  const load = (keyword = '') => {
    setLoading(true);
    adminFetchUsers(keyword.trim() || undefined)
      .then((data) => setUsers(Array.isArray(data) ? data : data.items || []))
      .catch((e) => setError(getErrorMessage(e, 'Không tải được danh sách người dùng.')))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const openPlanEdit = (u) => {
    setEditingPlan(u.id);
    setPlan(u.plan === 'PREMIUM' ? 'PREMIUM' : 'PREMIUM');
    setExpiresAt(u.planExpiresAt ? u.planExpiresAt.slice(0, 10) : '');
  };

  const handleSavePlan = async (u) => {
    setError('');
    try {
      const updated = await adminUpdateUserPlan(u.id, {
        plan,
        expiresAt: expiresAt || undefined,
      });
      setUsers(users.map((x) => (x.id === u.id ? { ...x, plan: updated.plan ?? plan, planExpiresAt: updated.planExpiresAt ?? (expiresAt ? new Date(expiresAt).toISOString() : null) } : x)));
      setEditingPlan(null);
      setNotice(`Đã cập nhật gói cho ${u.email}.`);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  return (
    <div>
      <h2>Người dùng</h2>
      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert alert-success">{notice}</div>}

      <form className="form-inline" onSubmit={(e) => { e.preventDefault(); load(q); }}>
        <input type="text" placeholder="Tìm theo email/tên…" value={q} onChange={(e) => setQ(e.target.value)} />
        <button type="submit" className="btn btn-outline btn-sm">Tìm</button>
      </form>

      {loading ? <p className="muted">Đang tải…</p> : (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead><tr><th>Email</th><th>Tên</th><th>Role</th><th>Gói</th><th>Hết hạn</th><th>Streak</th><th></th></tr></thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td>{u.email}</td>
                  <td>{u.name}</td>
                  <td><span className={`badge ${u.role === 'ADMIN' ? 'badge-admin' : 'badge-learner'}`}>{u.role}</span></td>
                  <td>
                    {editingPlan === u.id ? (
                      <select value={plan} onChange={(e) => setPlan(e.target.value)} className="select-inline">
                        <option value="PREMIUM">PREMIUM</option>
                        <option value="FREE">FREE</option>
                      </select>
                    ) : (
                      <span className={`badge ${u.plan === 'PREMIUM' ? 'badge-premium' : ''}`}>{u.plan}</span>
                    )}
                  </td>
                  <td>
                    {editingPlan === u.id ? (
                      <input type="date" value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)} placeholder="Không thời hạn" />
                    ) : (
                      formatDate(u.planExpiresAt)
                    )}
                  </td>
                  <td>{u.streakDays ?? 0} 🔥</td>
                  <td className="row-actions">
                    {editingPlan === u.id ? (
                      <>
                        <button type="button" className="btn btn-sm btn-primary" onClick={() => handleSavePlan(u)}>Lưu</button>
                        <button type="button" className="btn btn-sm btn-ghost" onClick={() => setEditingPlan(null)}>Hủy</button>
                      </>
                    ) : (
                      <button type="button" className="btn btn-sm btn-outline" onClick={() => openPlanEdit(u)}>Gán gói</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
