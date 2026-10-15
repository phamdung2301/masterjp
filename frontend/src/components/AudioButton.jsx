import { playAudio } from '../utils.js';

// Nút phát audio của từng từ. audioUrl có thể null (phase 1) → ẩn nút.
export default function AudioButton({ url, size = 'md', title = 'Nghe phát âm' }) {
  if (!url) return null;
  return (
    <button
      type="button"
      className={`audio-btn audio-btn-${size}`}
      onClick={(e) => { e.stopPropagation(); playAudio(url); }}
      title={title}
      aria-label={title}
    >
      🔊
    </button>
  );
}
