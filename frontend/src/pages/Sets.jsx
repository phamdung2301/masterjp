import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { createSet, deleteSet, fetchMySets } from '../api/sets.js';
import { getErrorMessage } from '../api/client.js';
import { formatDate } from '../utils.js';
import './pages.css';

// US-007 (F-024): bộ thẻ của tôi — CRUD.
export default function Sets() {
  const [sets, setSets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [creating, setCreating] = useState(false);
  const [notice, setNotice] = useState('');

  const load = () => {
    setLoading(true);
    fetchMySets()
      .then(setSets)
      .catch((e) => setError(getErrorMessage(e, 'Không tải được danh sách bộ thẻ.')))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const handleCreate = async (e) => {
    e.preventDefault();
    if (!name.trim()) { setError('Vui lòng nhập tên bộ thẻ.'); return; }
    setError('');
    setCreating(true);
    try {
      const created = await createSet({ name: name.trim(), description: description.trim() || undefined });
      setSets([created, ...sets]);
      setName('');
      setDescription('');
      setNotice(`Đã tạo bộ "${created.name}".`);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (s) => {
    if (!window.confirm(`Xóa bộ thẻ "${s.name}"? Các từ bên trong sẽ không bị xóa khỏi hệ thống.`)) return;
    try {
      await deleteSet(s.id);
      setSets(sets.filter((x) => x.id !== s.id));
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  return (
    <div className="page">
      <h1>Bộ thẻ của tôi</h1>
      <p className="muted">Gom từ khó từ nhiều bài vào bộ thẻ riêng để luyện tập.</p>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert alert-success">{notice}</div>}

      <form className="card form-inline" onSubmit={handleCreate}>
        <h3>Tạo bộ thẻ mới</h3>
        <input type="text" placeholder="Tên bộ thẻ (ví dụ: Từ hay quên bài 1–10)" value={name}
          onChange={(e) => setName(e.target.value)} maxLength={100} />
        <input type="text" placeholder="Mô tả (không bắt buộc)" value={description}
          onChange={(e) => setDescription(e.target.value)} maxLength={300} />
        <button type="submit" className="btn btn-primary" disabled={creating}>
          {creating ? 'Đang tạo…' : 'Tạo bộ thẻ'}
        </button>
      </form>

      {loading ? (
        <p className="muted">Đang tải…</p>
      ) : sets.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">🏷️</div>
          <p>Bạn chưa có bộ thẻ nào. Tạo bộ đầu tiên, hoặc từ trang bài học / từ điển bấm "＋ Bộ thẻ" để thêm từ vào.</p>
        </div>
      ) : (
        <div className="set-grid">
          {sets.map((s) => (
            <div key={s.id} className="set-card">
              <Link to={`/bo-the/${s.id}`} className="set-card-title">{s.name}</Link>
              {s.description && <p className="muted set-card-desc">{s.description}</p>}
              <div className="set-card-meta">
                <span>{s.wordCount ?? s.wordIds?.length ?? 0} từ</span>
                {s.isPublic && <span className="badge badge-public">🌐 Công khai</span>}
                <span className="muted">{formatDate(s.updatedAt || s.createdAt)}</span>
              </div>
              <div className="set-card-actions">
                <Link to={`/bo-the/${s.id}`} className="btn btn-sm btn-outline">Xem & luyện</Link>
                <button type="button" className="btn btn-sm btn-ghost danger" onClick={() => handleDelete(s)}>Xóa</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
