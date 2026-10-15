import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { fetchKanji } from '../api/kanji.js';
import { getErrorMessage } from '../api/client.js';
import { shuffle } from '../utils.js';
import './pages.css';

// F-B: flashcard kanji (v1) — mặt trước chữ kanji lớn, mặt sau âm đọc + nghĩa.
// v1 KHÔNG lưu SM-2 và không ghi StudySession (backend chưa có progress cho kanji);
// chỉ đếm Nhớ/Chưa nhớ trên màn hình kết quả.
export default function KanjiFlashcard() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const lessonNo = params.get('lessonNo');
  const jlpt = params.get('jlpt');

  const [cards, setCards] = useState([]);
  const [idx, setIdx] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [remembered, setRemembered] = useState(0);
  const [finished, setFinished] = useState(false);

  const scopeName = useMemo(() => {
    const parts = [];
    if (lessonNo) parts.push(`bài ${lessonNo}`);
    if (jlpt) parts.push(jlpt);
    return parts.join(' · ');
  }, [lessonNo, jlpt]);

  useEffect(() => {
    setLoading(true);
    setError('');
    fetchKanji({ lessonNo: lessonNo || undefined, jlpt: jlpt || undefined })
      .then((data) => setCards(shuffle(Array.isArray(data) ? data : [])))
      .catch((e) => setError(getErrorMessage(e, 'Không tải được thẻ kanji.')))
      .finally(() => setLoading(false));
  }, [lessonNo, jlpt]);

  const answer = (ok) => {
    if (ok) setRemembered((n) => n + 1);
    if (idx + 1 >= cards.length) {
      setFinished(true);
    } else {
      setIdx(idx + 1);
      setFlipped(false);
    }
  };

  if (loading) return <div className="page"><p className="muted">Đang chuẩn bị thẻ…</p></div>;
  if (error) return <div className="page"><div className="alert alert-error">{error}</div></div>;

  if (cards.length === 0) {
    return (
      <div className="page">
        <h1>Flashcard Kanji</h1>
        <p className="muted">Phạm vi này chưa có chữ kanji nào để luyện.</p>
        <button type="button" className="btn btn-outline" onClick={() => navigate(-1)}>Quay lại</button>
      </div>
    );
  }

  if (finished) {
    return (
      <div className="page result-page">
        <h1>Hoàn thành! 🎉</h1>
        <div className="result-summary">
          <div className="result-big">{remembered}/{cards.length}</div>
          <div className="muted">chữ bạn đánh giá "Nhớ"{scopeName && ` · ${scopeName}`}</div>
        </div>
        <div className="result-actions">
          <button type="button" className="btn btn-primary" onClick={() => {
            setCards(shuffle(cards));
            setIdx(0);
            setFlipped(false);
            setRemembered(0);
            setFinished(false);
          }}>Học lại</button>
          <button type="button" className="btn btn-outline" onClick={() => navigate('/kanji')}>Về trang kanji</button>
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

      <div className={`flash-card kanji-flash${flipped ? ' flipped' : ''}`} onClick={() => setFlipped(!flipped)}>
        {!flipped ? (
          <div className="flash-front">
            <div className="kanji-flash-char">{card.character}</div>
            <div className="kanji-meta">
              <span className="badge">{card.jlpt}</span>
              <span className="muted">{card.strokes} nét</span>
            </div>
            <div className="flash-hint">Chạm để lật thẻ</div>
          </div>
        ) : (
          <div className="flash-back">
            <div className="kanji-flash-char small">{card.character}</div>
            <div className="kanji-flash-readings">
              {card.onYomi && card.onYomi.length > 0 && (
                <div><span className="kanji-reading-label">On:</span> {card.onYomi.join('、 ')}</div>
              )}
              {card.kunYomi && card.kunYomi.length > 0 && (
                <div><span className="kanji-reading-label">Kun:</span> {card.kunYomi.join('、 ')}</div>
              )}
            </div>
            <div className="flash-meaning">{card.meaningVi}</div>
          </div>
        )}
      </div>

      <div className="flash-actions">
        <button type="button" className="btn btn-outline btn-lg" onClick={() => answer(false)}>
          😕 Chưa nhớ
        </button>
        <button type="button" className="btn btn-primary btn-lg" onClick={() => answer(true)}>
          😊 Nhớ rồi
        </button>
      </div>
      {!flipped && <p className="muted center">Hãy tự nhớ âm đọc và nghĩa trước khi lật thẻ nhé!</p>}
    </div>
  );
}
