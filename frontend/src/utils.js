// Map mã nội bộ (BE) → tiếng Việt tự nhiên cho người học. Không hiện mã nội bộ ra UI.
export const SECTION_LABELS = {
  VOCAB: 'Từ vựng chính',
  REFERENCE: 'Từ tham khảo',
  PHRASE: 'Cụm từ',
};

export const SECTION_TABS = [
  { value: 'VOCAB', label: 'Từ vựng chính' },
  { value: 'REFERENCE', label: 'Từ tham khảo' },
  { value: 'PHRASE', label: 'Cụm từ' },
];

export const QUIZ_MODE_LABELS = {
  JP_VI: 'Nhật → Việt',
  VI_JP: 'Việt → Nhật',
  LISTENING: 'Nghe → chọn',
  TYPING: 'Gõ phiên âm',
};

export const QUIZ_MODES = [
  { value: 'JP_VI', label: 'Nhật → Việt', desc: 'Nhìn từ tiếng Nhật, chọn nghĩa đúng' },
  { value: 'VI_JP', label: 'Việt → Nhật', desc: 'Nhìn nghĩa tiếng Việt, chọn từ đúng' },
  { value: 'LISTENING', label: 'Nghe → chọn', desc: 'Nghe phát âm, chọn từ đúng' },
];

export const STATUS_LABELS = {
  NEW: 'Từ mới',
  LEARNING: 'Đang học',
  MASTERED: 'Đã thuộc',
};

export const STATUS_COLORS = {
  NEW: 'status-new',
  LEARNING: 'status-learning',
  MASTERED: 'status-mastered',
};

// F-A: bảng cụm chủ đề (22 cụm + 'khac'). Backend là nguồn label chính thức cho
// GET /api/lessons/{id}/clusters; bảng này chỉ để admin gợi ý (datalist) và
// lookup dự phòng khi BE trả mã lạ. Sửa ở đây khi backend thêm cụm mới.
export const CLUSTER_LIST = [
  { value: 'gia-dinh', label: 'Gia đình' },
  { value: 'con-nguoi', label: 'Con người' },
  { value: 'nghe-nghiep', label: 'Nghề nghiệp' },
  { value: 'hoc-tap', label: 'Học tập' },
  { value: 'cong-viec', label: 'Công việc' },
  { value: 'do-dung', label: 'Đồ dùng' },
  { value: 'an-uong', label: 'Ăn uống' },
  { value: 'mua-sam', label: 'Mua sắm' },
  { value: 'tien-bac', label: 'Tiền bạc' },
  { value: 'dia-diem', label: 'Địa điểm' },
  { value: 'du-lich', label: 'Du lịch' },
  { value: 'giao-thong', label: 'Giao thông' },
  { value: 'thoi-gian', label: 'Thời gian' },
  { value: 'thien-nhien', label: 'Thiên nhiên & thời tiết' },
  { value: 'dong-vat', label: 'Động vật' },
  { value: 'co-the', label: 'Cơ thể' },
  { value: 'suc-khoe', label: 'Sức khỏe' },
  { value: 'quan-ao', label: 'Quần áo' },
  { value: 'mau-sac', label: 'Màu sắc' },
  { value: 'so-dem', label: 'Số đếm' },
  { value: 'vi-tri', label: 'Vị trí & hướng' },
  { value: 'giao-tiep', label: 'Giao tiếp' },
  { value: 'cam-xuc', label: 'Cảm xúc & tính cách' },
  { value: 'tinh-chat', label: 'Tính chất' },
  { value: 'hanh-dong', label: 'Hành động' },
  { value: 'the-thao', label: 'Thể thao' },
  { value: 'giai-tri', label: 'Giải trí' },
  { value: 'khoa-hoc', label: 'Khoa học' },
  { value: 'khac', label: 'Khác' },
];

export function clusterLabel(code) {
  if (!code) return 'Khác';
  const found = CLUSTER_LIST.find((c) => c.value === code);
  return found ? found.label : code;
}

export function formatDate(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

export function formatDateTime(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export function shuffle(arr) {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

export function playAudio(url) {
  if (!url) return Promise.resolve();
  return new Audio(resolveAssetUrl(url)).play().catch(() => {});
}

/**
 * Giai quyet URL tai nguyen (audio...) luu trong DB dang relative ("/audio/...")
 * Dev: VITE_API_BASE_URL rong -> giu nguyen de di qua Vite proxy (/audio -> BE).
 * Production: FE va BE khac origin -> prefix voi origin cua BE suy tu
 * VITE_API_BASE_URL (vd. "https://xxx.onrender.com/api" -> "https://xxx.onrender.com").
 */
export function resolveAssetUrl(url) {
  if (!url || /^https?:\/\//i.test(url)) return url;
  if (!url.startsWith('/')) return url;
  const apiBase = import.meta.env.VITE_API_BASE_URL || '';
  const beOrigin = apiBase.replace(/\/api\/?$/, '').replace(/\/$/, '');
  return beOrigin ? beOrigin + url : url;
}
