import { useEffect, useRef, useState } from 'react';
import { fetchGrammarDetail } from '../api/grammar.js';
import { getErrorMessage } from '../api/client.js';
import GrammarPointCard from './GrammarPointCard.jsx';
import '../pages/pages.css';

// GrammarRelatedModal — modal đơn giản (dùng lại pattern modal-backdrop/modal của index.css)
// hiển thị 1 điểm ngữ pháp theo id (khi bấm chip compareWith). Card tự fetch detail khi mở.
// Props:
// - grammarId: id điểm ngữ pháp cần xem (null/undefined = đóng modal)
// - relatedTitles: map id → title để hiển thị tiêu đề + chip liên quan (optional)
// - onClose(): đóng modal
// - onRelated(id): bấm chip liên quan bên trong modal → chuyển sang xem điểm đó (optional)
export default function GrammarRelatedModal({ grammarId, relatedTitles, onClose, onRelated }) {
  const [data, setData] = useState(null); // list-view-ish object cho GrammarPointCard
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const seqRef = useRef(0);

  useEffect(() => {
    if (!grammarId) {
      setData(null);
      setError('');
      return;
    }
    const seq = ++seqRef.current;
    setLoading(true);
    setError('');
    setData(null);
    fetchGrammarDetail(grammarId)
      .then((d) => {
        if (seq !== seqRef.current) return;
        setData(d || { id: grammarId });
      })
      .catch((e) => {
        if (seq !== seqRef.current) return;
        // Lỗi nhẹ: vẫn render card với các trường đã biết để card tự retry detail.
        setData({ id: grammarId, title: relatedTitles?.[grammarId] || '' });
        setError(getErrorMessage(e, 'Không tải được điểm ngữ pháp này.'));
      })
      .finally(() => {
        if (seq === seqRef.current) setLoading(false);
      });
    // relatedTitles chỉ dùng để tra tiêu đề — không đưa vào deps để tránh refetch khi map thay đổi.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [grammarId]);

  if (!grammarId) return null;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal grammar-modal"
        role="dialog"
        aria-modal="true"
        aria-label="Điểm ngữ pháp liên quan"
        onClick={(e) => e.stopPropagation()}
      >
        <button type="button" className="grammar-modal-close" onClick={onClose} aria-label="Đóng">
          ✕
        </button>
        {loading && <p className="muted">Đang tải điểm ngữ pháp…</p>}
        {error && !loading && <div className="alert alert-error">{error}</div>}
        {data && (
          <GrammarPointCard
            point={data}
            defaultOpen
            relatedTitles={relatedTitles}
            onRelated={onRelated}
          />
        )}
      </div>
    </div>
  );
}
