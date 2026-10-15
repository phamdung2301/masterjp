import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchPublicSets, forkSet } from '../api/sets.js';
import { getErrorMessage } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import LoginPrompt from '../components/LoginPrompt.jsx';
import './pages.css';

// US-013 (F-035): Khám phá bộ thẻ công khai — không cần login để xem;
// nút "Sao chép về" cần login (guest → mời đăng nhập).
export default function Explore() {
  const { isLoggedIn } = useAuth();
  const [q, setQ] = useState('');
  const [sets, setSets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [loginPrompt, setLoginPrompt] = useState('');
  const [notice, setNotice] = useState('');
  const [forking, setForking] = useState(null);

  const load = (keyword = '') => {
    setLoading(true);
    setError('');
    // BE trả { items, total } — lấy items.
    fetchPublicSets(keyword.trim() || undefined)
      .then((data) => setSets(Array.isArray(data) ? data : data.items || []))
      .catch((e) => setError(getErrorMessage(e, 'Không tải được danh sách bộ thẻ.')))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const handleFork = async (s) => {
    if (!isLoggedIn) {
      setLoginPrompt('Đăng nhập để sao chép bộ thẻ này về tài khoản của bạn.');
      return;
    }
    setForking(s.id);
    setError('');
    setNotice('');
    try {
      const forked = await forkSet(s.id);
      setNotice(`Đã sao chép "${s.name}" về tài khoản của bạn.`);
      setSets(sets.map((x) => x.id === s.id ? { ...x, forkCount: (x.forkCount || 0) + 1 } : x));
    } catch (e) {
      setError(getErrorMessage(e, 'Không sao chép được, vui lòng thử lại.'));
    } finally {
      setForking(null);
    }
  };

  return (
    <div className="page">
      <h1>Khám phá</h1>
      <p className="muted">Bộ thẻ công khai do cộng đồng chia sẻ — sao chép về để luyện theo cách của bạn.</p>
      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert alert-success">{notice} <Link to="/bo-the">Xem bộ thẻ của tôi →</Link></div>}
      <form className="search-box" onSubmit={(e) => { e.preventDefault(); load(q); }}>
        <input type="text" placeholder="Tìm bộ thẻ theo tên…" value={q} onChange={(e) => setQ(e.target.value)} />
        <button type="submit" className="btn btn-primary">Tìm</button>
      </form>
      {loading ? (
        <p className="muted">Đang tải…</p>
      ) : sets.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">🌐</div>
          <p>Chưa có bộ thẻ công khai nào{ q.trim() ? ' khớp tìm kiếm' : '' }.</p>
        </div>
      ) : (
        <div className="set-grid">
          {sets.map((s) => (
            <div key={s.id} className="set-card">
              <div className="set-card-title">{s.name}</div>
              {s.description && <p className="muted set-card-desc">{s.description}</p>}
              <div className="set-card-meta">
                <span>{s.wordCount ?? s.wordIds?.length ?? 0} từ</span>
                {s.authorName && <span className="muted">bởi {s.authorName}</span>}
                {(s.forkCount ?? 0) > 0 && <span className="muted">📋 {s.forkCount} lượt sao chép</span>}
              </div>
              <div className="set-card-actions">
                <button type="button" className="btn btn-sm btn-primary"
                  onClick={() => handleFork(s)} disabled={forking === s.id}>
                  {forking === s.id ? 'Đang sao chép…' : '📋 Sao chép về'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
      <LoginPrompt open={!!loginPrompt} onClose={() => setLoginPrompt('')} message={loginPrompt} />
    </div>
  );
}
