// Toast "Nhan huy hieu moi!" — lang nghe event 'mjp:new-badges' do
// api/study.js recordSession ban ra khi response co newBadges non-empty.
// Can duoc mount 1 lan o App root (xem wiring snippet trong bao cao C2).
import { useEffect, useState } from 'react';

// KHOP BadgeCatalog backend (service/BadgeCatalog.java) — chi de hien thi.
const BADGE_NAMES = {
  FIRST_SESSION: 'Khởi động',
  VOCAB_100: 'Vốn từ 100',
  VOCAB_500: 'Vốn từ 500',
  STREAK_7: 'Tuần rực lửa',
  STREAK_30: 'Tháng bền bỉ',
  LESSONS_5: 'Chạy đà',
  LESSONS_25: 'Nửa chặng N5',
  LESSONS_50: 'Chinh phục 50',
  SHADOW_1: 'Luyện nói đầu tiên',
  SENSEI_10: 'Học trò Sensei',
  REVIEW_100: 'Bậc thầy ôn tập',
  NIGHT_OWL: 'Cú đêm chăm chỉ',
};

const SHOW_MS = 6000;

export default function NewBadgeToast() {
  const [codes, setCodes] = useState([]);

  useEffect(() => {
    let timer = null;
    const handler = (e) => {
      const list = Array.isArray(e?.detail?.codes) ? e.detail.codes : [];
      if (list.length === 0) return;
      setCodes(list);
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => setCodes([]), SHOW_MS);
    };
    window.addEventListener('mjp:new-badges', handler);
    return () => {
      window.removeEventListener('mjp:new-badges', handler);
      if (timer) clearTimeout(timer);
    };
  }, []);

  if (codes.length === 0) return null;

  return (
    <div className="badge-toast" role="status">
      <span className="jp-badge gold">祝</span>
      <div>
        <strong>Nhận huy hiệu mới!</strong>
        <p>{codes.map((c) => BADGE_NAMES[c] || c).join(' · ')}</p>
      </div>
    </div>
  );
}
