# DOC-10 — Thiết kế lại IA cho Master JP v2.0 (Phase A)

**Ngày:** 2026-10-22 · **Trạng thái:** ĐÃ DUYỆT bởi user ("duyệt" 2026-10-22) · **Triển khai:** Phase A, branch `v2/phase-a-ia-visual`
**Tài liệu gốc:** `~/workspace/master-jp-v2/KE-HOACH-PHAT-TRIEN.md` (Phần 4.1–4.5) — đây là bản spec kỹ thuật rút gọn để implement.

## 1. Quyết định kiến trúc (binding)

- Điều hướng chính còn **4 tab**: **Học** (`/hoc`), **Ôn tập** (`/on-tap`, giữ nguyên), **Từ điển** (`/tu-dien`, giữ nguyên), **Cá nhân** (`/ca-nhan`, mới).
- **Lesson Hub** (`/hoc/bai/:id`): mọi nội dung của 1 bài (Từ vựng, Ngữ pháp, Kanji) nằm trong 1 trang; "Luyện tập" là **mode picker bên trong bài**, không phải tab/page độc lập.
- **1 hàng đợi ôn tập duy nhất** (không đổi logic SM-2 backend — Phase A chỉ đổi UI gom hiển thị).
- Route cũ **không được 404**: redirect 301 về route mới tương đương (bảng ở mục 4).

## 2. Design tokens — Washi theme (A2)

CSS variables trong `frontend/src/index.css` (hoặc file theme riêng import từ index.css):

```
--washi-bg: #FAF6EE;      /* nền giấy washi */
--washi-ink: #1F3A5F;     /* chàm ai-iro — chữ chính, header */
--washi-shu: #E2482B;     /* đỏ shu-iro — CTA, nhấn */
--washi-gold: #E8A93D;    /* vàng mù tạt — badge, cảnh báo nhẹ */
--washi-matcha: #7A9B57;  /* xanh matcha — thành công */
--washi-line: #EFE6D2;    /* viền nhạt */
--washi-muted: #8a97a8;   /* chữ phụ */
```

- Font chữ Nhật: `Noto Sans JP` (Google Fonts import trong index.css); tiếng Việt: font hiện tại của project.
- **Không emoji làm icon.** Badge ký tự Nhật (ngữ 語 · thẻ 札 · hỏi 問 · văn 文 · hán 漢 · ôn 復) hoặc SVG vẽ tay, theo preview `pv-trang-chu.html`.
- Ảnh minh hoạ: tham chiếu `/assets/<filename>` (8 file: `mascot-hero.png`, `card-vocab.jpeg`, `card-flashcard.jpeg`, `card-quiz.jpeg`, `card-grammar.jpeg`, `card-kanji.jpeg`, `card-review.jpeg`, `footer-mascot.png`). Ảnh do user upload tay qua GitHub web sau merge — code phải render đẹp cả khi ảnh chưa có (nền màu fallback).

## 3. Spec từng màn hình (A3/A4/A5)

### 3.1. Trang chủ guest `/` (A4) — theo `pv-trang-chu.html`
- Hero: badge "MINNA NO NIHONGO · N5–N4 · 50 BÀI", headline, sub, 2 CTA (Bắt đầu miễn phí / Xem thử bài 1), 3 stat (2.692 từ · 145 ngữ pháp · 988 kanji), ảnh `mascot-hero.png`.
- 6 card tính năng dùng 6 ảnh `card-*.jpeg` + badge ký tự Nhật + link.
- Bảng giá 3 gói (Free / Premium tháng 49k / Premium năm 390k) — **chỉ hiển thị**, chưa gắn thanh toán (Phase D).
- Footer + `footer-mascot.png`.

### 3.2. Tab Học `/hoc`
- Lộ trình 50 bài dạng grid/list có filter N5/N4, mỗi bài hiện tiến trình % (dùng API hiện có của Lessons).
- Nút "Học tiếp" → bài đang dở (dựa vào study progress hiện có, không thêm API mới).

### 3.3. Lesson Hub `/hoc/bai/:id`
- Header bài + thanh tiến trình.
- Section (dạng tab như preview `pv-bai-hoc.html`): **Từ vựng** (bảng + audio + checkbox chọn) | **Cụm chủ đề** | **Ngữ pháp** (công thức + ví dụ + "Luyện ngay") | **Kanji** (grid + stroke order).
- Khối **"Luyện tập"**: mode picker 4 mode — Flashcard 3D / Trắc nghiệm / Nghe / Gõ từ — render component practice hiện có trong ngữ cảnh bài (truyền lessonId), không viết lại logic chấm điểm.
- Dữ liệu: dùng đúng API/DTO hiện có của LessonDetail, Grammar, Kanji. **Không thêm endpoint mới.**

### 3.4. Ôn tập `/on-tap` — giữ logic, đổi UI
- Gom hiển thị: số từ đến hạn + thời gian ước tính + streak. Logic SM-2 và `PATCH /api/progress/answer` giữ nguyên.

### 3.5. Cá nhân `/ca-nhan` (mới) — TAB: Tổng quan | Bộ thẻ của tôi | Thành tích | Cài đặt
- Tổng quan: reuse Dashboard hiện có. Bộ thẻ: reuse Sets/SetDetail hiện có (render trong tab, không đổi logic). Thành tích: UI mới (huy hiệu tĩnh, chưa cần API mới — dùng progress hiện có). Cài đặt: link tới trang cài đặt hiện có (nếu có).

### 3.6. Flashcard 3D (A5)
- Lật thẻ bằng CSS `perspective` + `transform: rotateY(180deg)` + `backface-visibility: hidden` (theo preview `pv-luyen-tap.html`).
- Confetti khi hoàn thành phiên: dùng `canvas-confetti` nếu đã có trong `package.json`, nếu chưa — tự viết hiệu ứng CSS/JS nhẹ, **không thêm dependency mới** khi chưa được duyệt.
- Logic đánh giá Nhớ/Chưa nhớ và `PATCH /api/progress/answer` sau mỗi câu — giữ nguyên 100%.

## 4. Bảng redirect (không break bookmark)

| Route cũ | Route mới |
|---|---|
| `/bai-hoc` | → `/hoc` |
| `/bai-hoc/:id` | → `/hoc/bai/:id` |
| `/luyen-tap/flashcard`, `/luyen-tap/quiz`, `/luyen-tap/go-phien-am`, `/luyen-tap/kanji`, `/luyen-tap/kanji-quiz`, `/luyen-tap-nhanh` | → `/hoc` (user chọn bài rồi chọn mode) |
| `/tong-quan` | → `/ca-nhan` |
| `/bo-the`, `/bo-the/:id` | → `/ca-nhan` (tab Bộ thẻ) |
| `/kham-pha` | giữ nguyên (quyết định ở phase sau) |

Dùng `<Navigate replace>` của react-router-dom.

## 5. Ràng buộc kỹ thuật (Phase A = frontend-only)

1. **Không đổi backend API.** Mọi field FE đọc/gửi phải khớp DTO backend từng ký tự (chạy `api-contract-guard` sau implement).
2. Không thêm dependency npm mới.
3. Mọi copy tiếng Việt qua `copy-ux-linter` (ngôn ngữ end-user tự nhiên).
4. Bug phát hiện → log đầy đủ vào `~/workspace/bug-log/`.
5. Branch `v2/phase-a-ia-visual`, PR để user merge. **Không push main.**

## 6. Tiêu chí nghiệm thu (acceptance)

- [ ] 4 tab điều hướng hiển thị đúng, active state đúng route.
- [ ] Lesson Hub render đủ 4 section với dữ liệu thật từ API hiện có.
- [ ] 6 route practice cũ redirect về `/hoc`, không 404.
- [ ] Flashcard lật 3D mượt, chấm điểm/SM-2 vẫn ghi đúng sau mỗi câu.
- [ ] Homepage mới khớp preview (hero, 6 card, pricing), render ổn khi thiếu ảnh.
- [ ] Không có emoji nào dùng làm icon trong code mới.
- [ ] `npm run build` pass (lưu ý: môi trường này không có node_modules — user build ở máy).
