import AudioButton from './AudioButton.jsx';
import { SECTION_LABELS, STATUS_COLORS, STATUS_LABELS } from '../utils.js';

export default function WordCard({ word, selectable, selected, onToggleSelect, onAddToSet, showLesson }) {
  const handleCardClick = () => {
    if (selectable && onToggleSelect) onToggleSelect(word);
  };
  return (
    <div className={`word-card${selected ? ' selected' : ''}`} onClick={handleCardClick}>
      {selectable && (
        <input
          type="checkbox"
          className="word-card-check"
          checked={!!selected}
          onChange={(e) => { e.stopPropagation(); onToggleSelect && onToggleSelect(word); }}
          aria-label="Chọn từ"
        />
      )}
      <div className="word-card-main">
        <div className="word-card-line1">
          <span className="word-kanji">{word.kanji || word.kana}</span>
          {word.kanji && <span className="word-kana">{word.kana}</span>}
          <AudioButton url={word.audioUrl} />
        </div>
        <div className="word-card-line2">
          <span className="word-romaji">{word.romaji}</span>
          <span className="word-meaning">{word.meaningVi}</span>
        </div>
        {word.pos && <div className="word-pos">{word.pos}</div>}
        {word.exampleJa && (
          <div className="word-example">
            <div className="word-example-ja">{word.exampleJa}</div>
            {word.exampleVi && <div className="word-example-vi">{word.exampleVi}</div>}
          </div>
        )}
      </div>
      <div className="word-card-side">
        {word.studyStatus && (
          <span className={`status-dot ${STATUS_COLORS[word.studyStatus]}`} title={STATUS_LABELS[word.studyStatus] || word.studyStatus} />
        )}
        {showLesson && word.lessonLabel && <span className="word-lesson">{word.lessonLabel}</span>}
        {onAddToSet && (
          <button
            type="button"
            className="btn btn-sm btn-outline"
            onClick={(e) => { e.stopPropagation(); onAddToSet(word); }}
          >
            ＋ Bộ thẻ
          </button>
        )}
      </div>
    </div>
  );
}
