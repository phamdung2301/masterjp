import { useState } from 'react';
import { fetchGrammarDetail } from '../api/grammar.js';
import { getErrorMessage } from '../api/client.js';
import '../pages/pages.css';

// GrammarPointCard — component dùng chung cho trang Ngữ pháp và tab Ngữ pháp trong bài học.
// Props:
// - point: object list-view { id, lessonNo, level, title, titleVi, pattern, meaningVi, tags }
// - onRelated(id): callback khi bấm chip compareWith (optional — không truyền thì chip chỉ hiển thị)
// - relatedTitles: map id → title để hiển thị tên điểm liên quan trên chip (optional)
// - showLesson: hiện nhãn "Bài {lessonNo}" trên card (optional, dùng cho kết quả tìm kiếm)
// - defaultOpen: card mở rộng ngay từ đầu (optional, dùng trong modal xem điểm liên quan)
export default function GrammarPointCard({ point, onRelated, relatedTitles, showLesson, defaultOpen }) {
  const [open, setOpen] = useState(!!defaultOpen);
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');

  const loadDetail = async () => {
    if (detail || detailLoading) return;
    setDetailLoading(true);
    setDetailError('');
    try {
      const d = await fetchGrammarDetail(point.id);
      setDetail(d || {});
    } catch (e) {
      setDetailError(getErrorMessage(e, 'Không tải được chi tiết điểm ngữ pháp.'));
    } finally {
      setDetailLoading(false);
    }
  };

  const toggle = () => {
    const willOpen = !open;
    setOpen(willOpen);
    if (willOpen) loadDetail();
  };

  const tags = point.tags || [];
  const compareIds = detail?.compareWith || [];
  const chipLabel = (rid) =>
    (relatedTitles && relatedTitles[rid]) ? relatedTitles[rid] : rid;

  return (
    <div className={`grammar-card${open ? ' open' : ''}`}>
      <button type="button" className="grammar-card-head" onClick={toggle} aria-expanded={open}>
        <span className="grammar-card-chevron">{open ? '▾' : '▸'}</span>
        <span className="grammar-card-main">
          <span className="grammar-title">{point.title}</span>
          {point.titleVi && <span className="grammar-title-vi"> — {point.titleVi}</span>}
        </span>
        {showLesson && point.lessonNo != null && (
          <span className="grammar-lesson-badge">Bài {point.lessonNo}</span>
        )}
        <span className="grammar-detail-link">{open ? 'Thu gọn' : 'Chi tiết'}</span>
      </button>

      <div className="grammar-summary">
        {point.pattern && <code className="grammar-pattern">{point.pattern}</code>}
        {point.meaningVi && <div className="grammar-meaning">{point.meaningVi}</div>}
        {tags.length > 0 && (
          <div className="grammar-tags">
            {tags.map((t) => (
              <span key={t} className="grammar-tag">{t}</span>
            ))}
          </div>
        )}
      </div>

      {open && (
        <div className="grammar-detail">
          {detailLoading && <p className="muted">Đang tải chi tiết…</p>}
          {detailError && <div className="alert alert-error">{detailError}</div>}
          {detail && !detailLoading && !detailError && (
            <>
              {detail.explanation && <p className="grammar-explanation">{detail.explanation}</p>}
              {detail.formation && (
                <div className="grammar-formation">
                  <span className="grammar-label">Cách tạo:</span>
                  <code className="grammar-pattern">{detail.formation}</code>
                </div>
              )}
              {Array.isArray(detail.examples) && detail.examples.length > 0 && (
                <div className="grammar-examples">
                  <span className="grammar-label">Ví dụ:</span>
                  <ul>
                    {detail.examples.map((ex, i) => (
                      <li key={i} className="grammar-example">
                        <div className="grammar-example-ja">
                          <strong>{ex.ja}</strong>
                          {ex.kana && <span className="grammar-example-kana">{ex.kana}</span>}
                        </div>
                        {ex.vi && <div className="grammar-example-vi">{ex.vi}</div>}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              {detail.notes && (
                <div className="grammar-notes">
                  <span className="grammar-notes-icon">⚠️</span>
                  <span>{detail.notes}</span>
                </div>
              )}
              {compareIds.length > 0 && (
                <div className="grammar-compare">
                  <span className="grammar-label">So sánh với:</span>
                  <div className="grammar-chips">
                    {compareIds.map((rid) =>
                      onRelated ? (
                        <button
                          key={rid}
                          type="button"
                          className="grammar-chip grammar-chip-btn"
                          title={chipLabel(rid)}
                          onClick={(e) => { e.stopPropagation(); onRelated(rid); }}
                        >
                          {chipLabel(rid)}
                        </button>
                      ) : (
                        <span key={rid} className="grammar-chip" title={chipLabel(rid)}>
                          {chipLabel(rid)}
                        </span>
                      )
                    )}
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
