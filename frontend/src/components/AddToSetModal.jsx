import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { addWordsToSet, createSet, fetchMySets } from '../api/sets.js';
import { getErrorMessage } from '../api/client.js';

// Modal "Thêm vào bộ thẻ": chọn bộ có sẵn hoặc tạo bộ mới.
// API: POST /api/sets/{id}/words { wordIds } → { added, skipped }.
export default function AddToSetModal({ open, onClose, words, onDone }) {
  const [sets, setSets] = useState([]);
  const [loading, setLoading] = useState(false);
  const [newName, setNewName] = useState('');
  const [error, setError] = useState('');
  const [result, setResult] = useState('');
  const [doneSetId, setDoneSetId] = useState(null);

  useEffect(() => {
    if (!open) return;
    setError('');
    setResult('');
    setNewName('');
    setDoneSetId(null);
    setLoading(true);
    fetchMySets()
      .then(setSets)
      .catch((e) => setError(getErrorMessage(e, 'Không tải được danh sách bộ thẻ.')))
      .finally(() => setLoading(false));
  }, [open ]);

  if (!open) return null;

  const addTo = async (setId) => {
    setError('');
    setResult('');
    try {
      const { added, skipped } = await addWordsToSet(setId, words.map((w) => w.id));
      setResult(`Đã thêm ${added} từ${skipped > 0 ? ` (${skipped} từ đã có sẵn)` : ''}.`);
      setDoneSetId(setId);
      onDone && onDone();
    } catch (e) {
      setError(getErrorMessage(e));
    }
  };

  const createAndAdd = async () => {
    if (!newName.trim()) {
      setError('Vui lòng nhập tên bộ thẻ.');
      return;
    }
    setError('');
    try {
      const created = await createSet({ name: newName.trim() });
      const { added, skipped } = await addWordsToSet(created.id, words.map((w) => w.id));
      setResult(`Đã tạo bộ "${created.name}" và thêm ${added} từ${skipped > 0 ? ` (${skipped} từ đã có sẵn)` : ''}.`);
      setDoneSetId(created.id);
      setNewName('');
      onDone && onDone();
    } catch (e) {
      setError(getErrorMessage(e));
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Thêm {words.length} từ vào bộ thẻ</h3>
        {error && <div className="alert alert-error">{error}</div>}
        {result && <div className="alert alert-success">{result}</div>}
        {loading ? (
          <p>Đang tải…</p>
        ) : (
          <>
            <div className="set-pick-list">
              {sets.length === 0 && <p className="muted">Bạn chưa có bộ thẻ nào — hãy tạo bộ mới bên dưới.</p>}
              {sets.map((s) => (
                <button key={s.id} type="button" className="set-pick-item" onClick={() => addTo(s.id)}>
                  <span className="set-pick-name">{s.name}</span>
                  <span className="set-pick-count">{s.wordCount ?? s.wordIds?.length ?? 0} từ</span>
                </button>
              ))}
            </div>
            <div className="new-set-row">
              <input
                type="text"
                placeholder="Tên bộ thẻ mới…"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                maxLength={100}
              />
              <button type="button" className="btn btn-primary btn-sm" onClick={createAndAdd}>Tạo và thêm</button>
            </div>
          </>
        )}
        <div className="modal-actions">
          {result && doneSetId && (
            <Link to={`/bo-the/${doneSetId}`} className="btn btn-primary btn-sm">Xem bộ thẻ</Link>
          )}
          <button type="button" className="btn btn-ghost" onClick={onClose}>Đóng</button>
        </div>
      </div>
    </div>
  );
}
