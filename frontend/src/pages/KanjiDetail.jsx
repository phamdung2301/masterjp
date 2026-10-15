import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fetchKanjiDetail } from '../api/kanji.js';
import { getErrorMessage } from '../api/client.js';
import WordCard from '../components/WordCard.jsx';
import './pages.css';

// F-B: chi tiết chữ kanji — âm on/kun, nghĩa, số nét, cấp độ, các bài xuất hiện
// + danh sách từ chứa chữ đó (tối đa 50, theo contract). Bấm vào từ → sang bài học chứa từ.
export default function KanjiDetail() {
  const { char } = useParams();
  const character = char || ''; // useParams() đã decode sẵn — không decode 2 lần
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    setError('');
    fetchKanjiDetail(character)
      .then((data) => setDetail(data))
      .catch((e) => setError(getErrorMessage(e, 'Không tìm thấy chữ kanji này.')))
      .finally(() => setLoading(false));
  }, [character]);

  if (loading) return <div className="page"><p className="muted">Đang tải…</p></div>;
  if (error) return <div className="page"><div className="alert alert-error">{error}</div></div>;
  if (!detail) return null;

  const words = detail.words || [];

  return (
    <div className="page">
      <Link to="/kanji" className="back-link">← Tất cả kanji</Link>

      <div className="kanji-detail-card">
        <div className="kanji-detail-char">{detail.character}</div>
        <div className="kanji-detail-info">
          <h1>{detail.meaningVi}</h1>
          <div className="kanji-readings">
            <div className="kanji-reading">
              <span className="kanji-reading-label">Âm On</span>
              <span className="kanji-reading-values">
                {detail.onYomi && detail.onYomi.length > 0 ? detail.onYomi.join('、 ') : '—'}
              </span>
            </div>
            <div className="kanji-reading">
              <span className="kanji-reading-label">Âm Kun</span>
              <span className="kanji-reading-values">
                {detail.kunYomi && detail.kunYomi.length > 0 ? detail.kunYomi.join('、 ') : '—'}
              </span>
            </div>
          </div>
          <div className="kanji-meta-list">
            <span className="badge">{detail.jlpt}</span>
            <span>{detail.strokes} nét</span>
            {detail.lessonNos && detail.lessonNos.length > 0 && (
              <span className="muted">
                Xuất hiện trong bài: {detail.lessonNos.join(', ')}
              </span>
            )}
          </div>
        </div>
      </div>

      <h2>Từ vựng chứa chữ này ({words.length})</h2>
      <p className="muted">Bấm vào một từ để sang bài học chứa từ đó.</p>
      <div className="word-list">
        {words.map((w) => (
          w.lessonId ? (
            <Link key={w.id} to={`/bai-hoc/${w.lessonId}`} className="word-link">
              <WordCard word={w} />
            </Link>
          ) : (
            <WordCard key={w.id} word={w} />
          )
        ))}
      </div>
      {words.length === 0 && <p className="muted">Chưa có từ vựng nào chứa chữ này.</p>}
    </div>
  );
}
