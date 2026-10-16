import { useEffect, useState } from 'react';
import { adminCreateLesson, adminDeleteLesson, adminFetchLessons, adminUpdateLesson } from '../../api/admin.js';
import { getErrorMessage } from '../../api/client.js';

// US-010 (F-030): admin CRUD bài học.
export default function AdminLessons() {
  const [lessons, setLessons] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [form, setForm] = useState({ id: null, level: 'N5', lessonNo: '', titleJa: '', titleVi: '' });

  const load = () => {
    setLoading(true);
    adminFetchLessons()
      .then(setLessons)
      .catch((e) => setError(getErrorMessage(e, 'Không tải được danh sách bài học.')))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const resetForm = () => setForm({ id: null, level: 'N5', lessonNo: '', titleJa: '', titleVi: '' });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setNotice('');
    const payload = {
      level: form.level,
      lessonNo: Number(form.lessonNo),
      titleJa: form.titleJa.trim(),
      titleVi: form.titleVi.trim(),
    };
    try {
      if (form.id) {
        const updated = await adminUpdateLesson(form.id, payload);
        setLessons(lessons.map((l) => (l.id === form.id ? updated : l)));
        setNotice('Đã cập nhật bài học.');
      } else {
        const created = await adminCreateLesson(payload);
        setLessons([...lessons, created]);
        setNotice('Đã thêm bài học.');
      }
      resetForm();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleEdit = (l) => setForm({ id: l.id, level: l.level, lessonNo: l.lessonNo, titleJa: l.titleJa || '', titleVi: l.titleVi || '' });

  const handleDelete = async (l) => {
    if (!window.confirm(`Xóa bài ${l.lessonNo} — ${l.titleVi}?`)) return;
    try {
      await adminDeleteLesson(l.id);
      setLessons(lessons.filter((x) => x.id !== l.id));
      setNotice('Đã xóa bài học.');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  return (
    <div>
      <h2>Quản lý bài học</h2>
      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert alert-success">{notice}</div>}

      <form className="card" onSubmit={handleSubmit}>
        <h3>{form.id ? 'Sửa bài học' : 'Thêm bài học'}</h3>
        <div className="form-grid">
          <label>Cấp độ
            <select value={form.level} onChange={(e) => setForm({ ...form, level: e.target.value })}>
              <option value="N5">N5</option>
              <option value="N4">N4</option>
            </select>
          </label>
          <label>Số bài
            <input type="number" min={1} max={50} value={form.lessonNo} onChange={(e) => setForm({ ...form, lessonNo: e.target.value })} required />
          </label>
          <label>Tiêu đề tiếng Nhật
            <input type="text" value={form.titleJa} onChange={(e) => setForm({ ...form, titleJa: e.target.value })} placeholder="第1課" />
          </label>
          <label>Tiêu đề tiếng Việt
            <input type="text" value={form.titleVi} onChange={(e) => setForm({ ...form, titleVi: e.target.value })} required placeholder="Chào hỏi, giới thiệu" />
            <small className="muted">Chỉ nhập tên bài — không cần "Bài N", hệ thống tự thêm khi hiển thị.</small>
          </label>
        </div>
        <div className="row-actions">
          <button type="submit" className="btn btn-primary btn-sm">{form.id ? 'Lưu' : 'Thêm'}</button>
          {form.id && <button type="button" className="btn btn-ghost btn-sm" onClick={resetForm}>Hủy</button>}
        </div>
      </form>

      {loading ? <p className="muted">Đang tải…</p> : (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead><tr><th>Cấp độ</th><th>Bài</th><th>Tiêu đề JP</th><th>Tiêu đề VI</th><th></th></tr></thead>
            <tbody>
              {lessons.map((l) => (
                <tr key={l.id}>
                  <td>{l.level}</td>
                  <td>{l.lessonNo}</td>
                  <td>{l.titleJa}</td>
                  <td>{l.titleVi}</td>
                  <td className="row-actions">
                    <button type="button" className="btn btn-sm btn-outline" onClick={() => handleEdit(l)}>Sửa</button>
                    <button type="button" className="btn btn-sm btn-ghost danger" onClick={() => handleDelete(l)}>Xóa</button>
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
