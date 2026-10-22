import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { fetchSet, removeWordFromSet, shareSet, updateSet } from '../api/sets.js';
import { getErrorMessage } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import WordCard from '../components/WordCard.jsx';
import { QUIZ_MODES } from '../utils.js';
import { CardIcon, PencilIcon } from '../components/icons.jsx';
import './pages.css';

// US-007/US-013/US-014: chi tiết bộ thẻ — danh sách từ, luyện flashcard/quiz theo bộ,
// chia sẻ công khai (chỉ PREMIUM/ADMIN thấy nút; FREE gọi API → 403).
export default function SetDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { canSharePublic } = useAuth();
  const [set, setSet] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [quizMode, setQuizMode] = useState('JP_VI');

  useEffect(() => {
    setLoading(true);
    fetchSet(id)
      .then((s) => { setSet(s); setName(s.name); setDescription(s.description || ''); })
      .catch((e) => setError(getErrorMessage(e, 'Không tải được bộ thẻ.')))
      .finally(() => setLoading(false));
  }, [id]);

  const words = set?.words || [];

  const handleSave = async () => {
    if (!name.trim()) { setError('Tên bộ thẻ không được để trống.'); return; }
    try {
      const updated = await updateSet(id, { name: name.trim(), description: description.trim() || undefined });
      setSet({ ...set, name: updated.name, description: updated.description });
      setEditing(false);
      setNotice('Đã lưu thay đổi.');
    } catch (e) {
      setError(getErrorMessage(e));
    }
  };

  const handleRemoveWord = async (wordId) => {
    try {
      await removeWordFromSet(id, wordId);
      setSet({ ...set, words: words.filter((w) => w.id !== wordId) });
    } catch (e) {
      setError(getErrorMessage(e));
    }
  };

  const handleShare = async (isPublic) => {
    setError('');
    setNotice('');
    try {
      await shareSet(id, isPublic);
      setSet({ ...set, isPublic });
      setNotice(isPublic ? 'Bộ thẻ đã được chia sẻ công khai — mọi người có thể thấy ở trang Khám phá.' : 'Đã chuyển bộ thẻ về riêng tư.');
    } catch (e) {
      if (e?.response?.status === 403) {
        setError('Nâng cấp gói Premium để chia sẻ bộ thẻ công khai.');
      } else {
        setError(getErrorMessage(e));
      }
    }
  };

  if (loading) return <div className="page"><p className="muted">Đang tải…</p></div>;
  if (error && !set) return <div className="page"><div className="alert alert-error">{error}</div></div>;
  if (!set) return null;

  return (
    <div className="page">
      <Link to="/ca-nhan?tab=bo-the" className="back-link">← Bộ thẻ của tôi</Link>
      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert alert-success">{notice}</div>}

      {editing ? (
        <div className="card">
          <input type="text" value={name} onChange={(e) => setName(e.target.value)} maxLength={100} />
          <input type="text" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={300} placeholder="Mô tả" />
          <div className="row-actions">
            <button type="button" className="btn btn-primary btn-sm" onClick={handleSave}>Lưu</button>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => { setEditing(false); setName(set.name); setDescription(set.description || ''); }}>Hủy</button>
          </div>
        </div>
      ) : (
        <div className="set-header">
          <div>
            <h1>{set.name}</h1>
            {set.description && <p className="muted">{set.description}</p>}
            <div className="set-card-meta">
              <span>{words.length} từ</span>
              {set.isPublic && <span className="badge badge-public">Công khai</span>}
            </div>
          </div>
          <button type="button" className="btn btn-outline btn-sm" onClick={() => setEditing(true)}>Sửa tên/mô tả</button>
        </div>
      )}

      <div className="practice-bar">
        <span className="practice-label">Luyện theo bộ này:</span>
        <button type="button" className="btn btn-primary btn-sm"
          onClick={() => navigate(`/luyen-nhanh/the?scopeType=SET&scopeId=${id}`)}><CardIcon size={15} /> Flashcard</button>
        <select className="select-inline" value={quizMode} onChange={(e) => setQuizMode(e.target.value)} aria-label="Dạng quiz">
          {QUIZ_MODES.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
        </select>
        <button type="button" className="btn btn-outline btn-sm"
          onClick={() => navigate(`/luyen-nhanh/quiz?mode=${quizMode.toLowerCase()}&scopeType=SET&scopeId=${id}`)}><PencilIcon size={15} /> Quiz</button>
        <button type="button" className="btn btn-outline btn-sm"
          onClick={() => navigate(`/luyen-nhanh/go?scopeType=SET&scopeId=${id}`)}>Gõ phiên âm</button>
      </div>

      {canSharePublic ? (
        <div className="card share-card">
          <div>
            <strong>Chia sẻ công khai</strong>
            <p className="muted">Bộ công khai sẽ xuất hiện ở trang Khám phá, người khác có thể sao chép về.</p>
          </div>
          <button
            type="button"
            className={`btn btn-sm ${set.isPublic ? 'btn-outline' : 'btn-primary'}`}
            onClick={() => handleShare(!set.isPublic)}
          >
            {set.isPublic ? 'Chuyển về riêng tư' : 'Chia sẻ công khai'}
          </button>
        </div>
      ) : (
        <div className="card share-card muted">
          <div>
            <strong>Chia sẻ công khai</strong>
            <p className="muted">Chỉ thành viên Premium mới chia sẻ bộ thẻ công khai. <Link to="/#bang-gia">Xem bảng giá →</Link></p>
          </div>
        </div>
      )}

      <h3>Từ trong bộ ({words.length})</h3>
      {words.length === 0 ? (
        <p className="muted">Bộ chưa có từ nào. Từ trang bài học hoặc từ điển, bấm "＋ Bộ thẻ" để thêm từ vào đây.</p>
      ) : (
        <div className="word-list">
          {words.map((w) => (
            <div key={w.id} className="word-row-with-action">
              <WordCard word={w} />
              <button type="button" className="btn btn-sm btn-ghost danger" onClick={() => handleRemoveWord(w.id)}>Xóa khỏi bộ</button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
