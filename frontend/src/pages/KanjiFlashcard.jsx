import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { fetchKanji } from '../api/kanji.js';
import { getErrorMessage } from '../api/client.js';
import { CardIcon, CheckIcon, CrossIcon, RestartIcon } from '../components/icons.jsx';
import { shuffle } from '../utils.js';
import './pages.css';

// F-B: flashcard kanji (v1) — mặt trước chữ kanji lớn, mặt sau âm đọc + nghĩa.
// v1 KHÔNG lưu SM-2 và không ghi StudySession (backend chưa có progress cho kanji);
// chỉ đếm Nhớ/Chưa nhớ trên màn hình kết quả.
// F-B+: thêm chế độ ?pick=1 — đọc tập chữ đã chọn từ sessionStorage `mjp_kanji_pick`
// (trang /kanji lưu trước khi chuyển sang), theo mẫu quick mode của Quiz.jsx.
// v2.0 (A3/A5): nhúng trong Lesson Hub — truyền pick/lessonNo/jlpt qua props;
// lật thẻ 3D; logic chấm điểm giữ nguyên 100%.
// Props: pick, lessonNo, jlpt, embedded (bool), onExit (callback khi embedded).
const KANJI_PICK_KEY = 'mjp_kanji_pick';

export default function KanjiFlashcard({
  pick: pPick,
  lessonNo: pLessonNo,
  jlpt: pJlpt,
  embedded = false,
  onExit,
}) {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const pick = pPick ?? params.get('pick');
  const lessonNo = pLessonNo ?? params.get('lessonNo');
  const jlpt = pJlpt ?? params.get('jlpt');

  const [cards, setCards] = useState([]);
  const [idx, setIdx] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [results, setResults] = useState([]); // [{ character, ok }] từng thẻ đã trả lời
  const [finished, setFinished] = useState(false);

  const scopeName = useMemo(() => {
    if (pick === '1') return 'tập đã chọn';
    const parts = [];
    if (lessonNo) parts.push(`bài ${lessonNo}`);
    if (jlpt) parts.push(jlpt);
    return parts.join(' · ');
  }, [pick, lessonNo, jlpt]);

  // Embedded (Lesson Hub): nút quay lại thoát khỏi khối luyện tập.
  const goBack = () => {
    if (onExit) { onExit(); return; }
    navigate(-1);
  };

  useEffect(() => {
    setLoading(true);
    setError('');
    if (pick === '1') {
      let arr = null;
      try {
        arr = JSON.parse(sessionStorage.getItem(KANJI_PICK_KEY) || 'null');
      } catch {
        arr = null;
      }
      if (Array.isArray(arr) && arr.length > 0) {
        setCards(shuffle(arr.filter((k) => k && k.character)));
      } else {
        setError('Không tìm thấy danh sách chữ đã chọn. Hãy chọn lại từ trang Kanji.');
      }
      setLoading(false);
      return;
    }
    fetchKanji({ lessonNo: lessonNo || undefined, jlpt: jlpt || undefined })
      .then((data) => setCards(shuffle(Array.isArray(data) ? data : [])))
      .catch((e) => setError(getErrorMessage(e, 'Không tải được thẻ kanji.')))
      .finally(() => setLoading(false));
  }, [pick, lessonNo, jlpt]);

  const answer = (ok) => {
    const card = cards[idx];
    setResults((prev) => [...prev, { character: card.character, ok }]);
    if (idx + 1 >= cards.length) {
      setFinished(true);
    } else {
      setIdx(idx + 1);
      setFlipped(false);
    }
  };

  // Luyện lại: onlyWrong=true → chỉ các chữ bị đánh "Chưa nhớ".
  const restart = (onlyWrong) => {
    const wrongChars = new Set(results.filter((r) => !r.ok).map((r) => r.character));
    const next = shuffle(onlyWrong ? cards.filter((c) => wrongChars.has(c.character)) : cards);
    setCards(next);
    setIdx(0);
    setFlipped(false);
    setResults([]);
    setFinished(false);
  };

  if (loading) return <div className="page"><p className="muted">Đang chuẩn bị thẻ…</p></div>;
  if (error) return (
    <div className="page">
      <div className="alert alert-error">{error}</div>
      <button type="button" className="btn btn-outline" onClick={goBack}>
        {onExit ? '← Thoát luyện tập' : 'Về trang Kanji'}
      </button>
    </div>
  );

  if (cards.length === 0) {
    return (
      <div className="page">
        <h1>Flashcard Kanji</h1>
        <p className="muted">Phạm vi này chưa có chữ kanji nào để luyện.</p>
        <button type="button" className="btn btn-outline" onClick={goBack}>Quay lại</button>
      </div>
    );
  }

  if (finished) {
    const rememberedCount = results.filter((r) => r.ok).length;
    const wrongCount = results.length - rememberedCount;
    return (
      <div className="page result-page">
        <h1>Hoàn thành!</h1>
        <div className="result-summary">
          <div className="result-big">{rememberedCount}/{cards.length}</div>
          <div className="muted">chữ bạn đánh giá "Nhớ"{scopeName && ` · ${scopeName}`}</div>
        </div>
        <div className="result-actions">
          <button type="button" className="btn btn-primary" onClick={() => restart(false)}>
            <RestartIcon size={16} /> Luyện lại toàn bộ
          </button>
          {wrongCount > 0 ? (
            <button type="button" className="btn btn-outline" onClick={() => restart(true)}>
              <CardIcon size={16} /> Luyện lại {wrongCount} chữ chưa nhớ
            </button>
          ) : (
            <span className="muted">Tuyệt vời, nhớ hết rồi!</span>
          )}
          <button type="button" className="btn btn-outline" onClick={goBack}>
            {onExit ? '← Thoát luyện tập' : 'Về trang Kanji'}
          </button>
        </div>
      </div>
    );
  }

  const card = cards[idx];

  return (
    <div className="page practice-page">
      <div className="practice-top">
        <span className="muted">Thẻ {idx + 1}/{cards.length}{scopeName && ` · ${scopeName}`}</span>
        <div className="progress-bar slim"><div className="progress-fill" style={{ width: `${(idx / cards.length) * 100}%` }} /></div>
      </div>

      {/* A5: lật thẻ 3D — .fc-scene(perspective) > .fc-3d(preserve-3d) > .fc-face(front/back). */}
      <div className="fc-scene">
        <div className={`fc-3d${flipped ? ' fc-flipped' : ''}`} onClick={() => setFlipped(!flipped)} role="button" aria-label="Lật thẻ">
          <div className="fc-face fc-front">
            <div className="kanji-flash-char">{card.character}</div>
            <div className="kanji-meta">
              <span className="badge">{card.jlpt}</span>
              <span className="muted">{card.strokes} nét</span>
            </div>
            <div className="flash-hint">Chạm để lật thẻ</div>
          </div>
          <div className="fc-face fc-back">
            <div className="kanji-flash-char small">{card.character}</div>
            <div className="kanji-flash-readings">
              {card.onYomi && card.onYomi.length > 0 && (
                <div><span className="kanji-reading-label">Âm On:</span> {card.onYomi.join('、 ')}</div>
              )}
              {card.kunYomi && card.kunYomi.length > 0 && (
                <div><span className="kanji-reading-label">Âm Kun:</span> {card.kunYomi.join('、 ')}</div>
              )}
            </div>
            <div className="flash-meaning">{card.meaningVi}</div>
          </div>
        </div>
      </div>

      <div className="grade-row">
        <button type="button" className="gbtn no" onClick={() => answer(false)}>
          <CrossIcon size={18} /> Chưa nhớ
        </button>
        <button type="button" className="gbtn yes" onClick={() => answer(true)}>
          <CheckIcon size={18} /> Nhớ rồi
        </button>
      </div>
      {!flipped && <p className="muted center">Hãy tự nhớ âm đọc và nghĩa trước khi lật thẻ nhé!</p>}
    </div>
  );
}
