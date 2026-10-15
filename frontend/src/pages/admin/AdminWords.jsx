import { useEffect, useState } from 'react';
import { adminAudioJob, adminCreateWord, adminDeleteWord, adminFetchWords, adminRegenerateAudio, adminUpdateWord } from '../../api/admin.js';
import { getErrorMessage } from '../../api/client.js';
import { SECTION_LABELS, CLUSTER_LIST, clusterLabel } from '../../utils.js';

const EMPTY = { id: null, lessonId: '', section: 'VOCAB', kana: '', kanji: '', romaji: '', meaningVi: '', pos: '', exampleJa: '', exampleVi: '', orderInLesson: '', cluster: '' };

// US-010 (F-031, F-033): admin CRUD từ vựng + tìm kiếm + tạo lại audio (job nền, poll 5s).
// F-A: quản trị cụm chủ đề — xem/sửa field `cluster` từng từ (datalist gợi ý).
export default function AdminWords() {
  const [words, setWords] = useState([]);
  const [q, setQ] = useState('');
  const [lessonNo, setLessonNo] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [form, setForm] = useState(EMPTY);
  const [showForm, setShowForm] = useState(false);
  const [jobStatus, setJobStatus] = useState({});

  const load = (params = {}) => {
    setLoading(true);
    adminFetchWords(params)
      .then((data) => setWords(Array.isArray(data) ? data : data.items || []))
      .catch((e) => setError(getErrorMessage(e, 'Không tải được danh sách từ vựng.')))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const handleSearch = (e) => {
    e.preventDefault();
    load({ q: q.trim() || undefined, lessonNo: lessonNo || undefined });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setNotice('');
    const payload = {
      lessonId: form.lessonId.trim(),
      section: form.section,
      kana: form.kana.trim(),
      kanji: form.kanji.trim() || null,
      romaji: form.romaji.trim(),
      meaningVi: form.meaningVi.trim(),
      pos: form.pos.trim(),
      exampleJa: form.exampleJa.trim() || null,
      exampleVi: form.exampleVi.trim() || null,
      orderInLesson: form.orderInLesson === '' ? undefined : Number(form.orderInLesson),
      // F-A: cụm chuẩn hoá về slug lowercase; để trống → null (BE gom vào 'khac').
      cluster: form.cluster.trim().toLowerCase() || null,
    };
    if (!payload.kana || !payload.romaji || !payload.meaningVi || !payload.lessonId) {
      setError('Kana, romaji, nghĩa tiếng Việt và ID bài học là bắt buộc.');
      return;
    }
    try {
      if (form.id) {
        // ⚠️ UpdateWordRequest KHÔNG có field lessonId — gửi thừa -> Jackson 400
        // (FAIL_ON_UNKNOWN_PROPERTIES). Bỏ lessonId khỏi payload update.
        const { lessonId: _omit, ...updatePayload } = payload;
        const updated = await adminUpdateWord(form.id, updatePayload);
        setWords(words.map((w) => (w.id === form.id ? updated : w)));
        setNotice('Đã cập nhật từ vựng.');
      } else {
        const created = await adminCreateWord(payload);
        setWords([created, ...words]);
        setNotice('Đã thêm từ vựng.');
      }
      setForm(EMPTY);
      setShowForm(false);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleEdit = (w) => {
    setForm({
      id: w.id, lessonId: w.lessonId || '', section: w.section || 'VOCAB',
      kana: w.kana || '', kanji: w.kanji || '', romaji: w.romaji || '',
      meaningVi: w.meaningVi || '', pos: w.pos || '',
      exampleJa: w.exampleJa || '', exampleVi: w.exampleVi || '',
      orderInLesson: w.orderInLesson ?? '', cluster: w.cluster || '',
    });
    setShowForm(true);
  };

  const handleDelete = async (w) => {
    if (!window.confirm(`Xóa từ "${w.kana}"? (xóa mềm — tiến trình học vẫn giữ nguyên)`)) return;
    try {
      await adminDeleteWord(w.id);
      setWords(words.filter((x) => x.id !== w.id));
      setNotice('Đã xóa từ vựng.');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  // F-033: tạo lại audio — job nền, poll trạng thái mỗi 5s.
  const handleRegenerateAudio = async (wordIds) => {
    setError('');
    try {
      const { jobId } = await adminRegenerateAudio(wordIds);
      setJobStatus((s) => ({ ...s, [jobId]: 'Đang xử lý…' }));
      const t = setInterval(async () => {
        try {
          // Contract: { jobId, status, total, done, failed }; status: QUEUED | RUNNING | DONE | FAILED.
          const job = await adminAudioJob(jobId);
          const progress = `${job.done ?? 0}/${job.total ?? 0}`;
          if (job.status === 'DONE') {
            clearInterval(t);
            const failedNote = (job.failed ?? 0) > 0 ? `, ${job.failed} lỗi` : '';
            setJobStatus((s) => ({ ...s, [jobId]: `Xong (${progress}${failedNote})` }));
            setNotice('Đã tạo lại audio xong.');
          } else if (job.status === 'FAILED') {
            clearInterval(t);
            setJobStatus((s) => ({ ...s, [jobId]: 'Thất bại.' }));
          } else {
            setJobStatus((s) => ({ ...s, [jobId]: `${job.status}… ${progress}` }));
          }
        } catch {
          clearInterval(t);
          setJobStatus((s) => ({ ...s, [jobId]: 'Không đọc được trạng thái job.' }));
        }
      }, 5000);
    } catch (err) {
      setError(getErrorMessage(err, 'Không khởi tạo được job tạo audio.'));
    }
  };

  const setF = (k, v) => setForm({ ...form, [k]: v });

  return (
    <div>
      <h2>Quản lý từ vựng</h2>
      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert alert-success">{notice}</div>}
      {Object.entries(jobStatus).length > 0 && (
        <div className="card">
          <h3>Trạng thái tạo audio</h3>
          {Object.entries(jobStatus).map(([jobId, st]) => (
            <div key={jobId} className="muted">{jobId}: {st}</div>
          ))}
        </div>
      )}

      <form className="form-inline" onSubmit={handleSearch}>
        <input type="text" placeholder="Tìm từ (kana/kanji/nghĩa)…" value={q} onChange={(e) => setQ(e.target.value)} />
        <input type="number" placeholder="Số bài" value={lessonNo} onChange={(e) => setLessonNo(e.target.value)} min={1} max={50} />
        <button type="submit" className="btn btn-outline btn-sm">Tìm</button>
        <button type="button" className="btn btn-primary btn-sm" onClick={() => { setForm(EMPTY); setShowForm(!showForm); }}>
          {showForm ? 'Ẩn form' : '＋ Thêm từ'}
        </button>
      </form>

      {showForm && (
        <form className="card" onSubmit={handleSubmit}>
          <h3>{form.id ? 'Sửa từ vựng' : 'Thêm từ vựng'}</h3>
          <div className="form-grid">
            <label>ID bài học*<input type="text" value={form.lessonId} onChange={(e) => setF('lessonId', e.target.value)} required /></label>
            <label>Phần
              <select value={form.section} onChange={(e) => setF('section', e.target.value)}>
                <option value="VOCAB">{SECTION_LABELS.VOCAB}</option>
                <option value="REFERENCE">{SECTION_LABELS.REFERENCE}</option>
                <option value="PHRASE">{SECTION_LABELS.PHRASE}</option>
              </select>
            </label>
            <label>Kana*<input type="text" value={form.kana} onChange={(e) => setF('kana', e.target.value)} required /></label>
            <label>Kanji<input type="text" value={form.kanji} onChange={(e) => setF('kanji', e.target.value)} /></label>
            <label>Romaji*<input type="text" value={form.romaji} onChange={(e) => setF('romaji', e.target.value)} required /></label>
            <label>Nghĩa tiếng Việt*<input type="text" value={form.meaningVi} onChange={(e) => setF('meaningVi', e.target.value)} required /></label>
            <label>Loại từ<input type="text" value={form.pos} onChange={(e) => setF('pos', e.target.value)} placeholder="danh từ, động từ nhóm 1…" /></label>
            <label>Thứ tự trong bài<input type="number" value={form.orderInLesson} onChange={(e) => setF('orderInLesson', e.target.value)} /></label>
            <label>Cụm chủ đề
              <input type="text" value={form.cluster} onChange={(e) => setF('cluster', e.target.value)}
                list="cluster-suggestions" placeholder="vd. do-dung — để trống = Khác" />
              <datalist id="cluster-suggestions">
                {CLUSTER_LIST.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
              </datalist>
            </label>
            <label className="span-2">Câu ví dụ (tiếng Nhật)<input type="text" value={form.exampleJa} onChange={(e) => setF('exampleJa', e.target.value)} /></label>
            <label className="span-2">Dịch câu ví dụ<input type="text" value={form.exampleVi} onChange={(e) => setF('exampleVi', e.target.value)} /></label>
          </div>
          <div className="row-actions">
            <button type="submit" className="btn btn-primary btn-sm">{form.id ? 'Lưu' : 'Thêm'}</button>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => { setShowForm(false); setForm(EMPTY); }}>Hủy</button>
          </div>
        </form>
      )}

      {loading ? <p className="muted">Đang tải…</p> : (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead><tr><th>Từ</th><th>Nghĩa</th><th>Phần</th><th>Cụm</th><th>Bài</th><th>Audio</th><th></th></tr></thead>
            <tbody>
              {words.map((w) => (
                <tr key={w.id}>
                  <td>{w.kanji || w.kana} <span className="muted">({w.kana})</span></td>
                  <td>{w.meaningVi}</td>
                  <td>{SECTION_LABELS[w.section] || w.section}</td>
                  <td>{clusterLabel(w.cluster)}</td>
                  <td>{w.lessonNo ?? ''}</td>
                  <td>{w.audioUrl ? '✅' : '❌'}</td>
                  <td className="row-actions">
                    <button type="button" className="btn btn-sm btn-outline" onClick={() => handleEdit(w)}>Sửa</button>
                    <button type="button" className="btn btn-sm btn-outline" onClick={() => handleRegenerateAudio([w.id])}>🔊 Tạo audio</button>
                    <button type="button" className="btn btn-sm btn-ghost danger" onClick={() => handleDelete(w)}>Xóa</button>
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
