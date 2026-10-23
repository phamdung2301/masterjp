# SRE — Master JP — Website luyện tiếng Nhật (Minna no Nihongo)

**Thương hiệu: Master JP** (chốt 2026-10-14).

## 1. Tầm nhìn
Website giúp người học tiếng Nhật trình độ N5–N4 luyện từ vựng theo đúng tiến
trình giáo trình Minna no Nihongo: học theo bài, luyện theo phần trong bài,
ôn bằng thẻ nhớ (flashcard), quiz nhiều dạng, và tự xây bộ thẻ riêng. Hệ thống
ghi nhớ tiến trình từng từ của từng người học và nhắc ôn đúng lúc (spaced
repetition).

## 2. Phạm vi

### 2.1 In scope
- Nội dung: **25 bài N5** (bài 1–25, quyển I) + **25 bài N4** (bài 26–50,
  quyển II) **[Giả định — chờ user xác nhận]**.
- Mỗi bài: danh sách từ vựng đầy đủ, chia theo **phần** (từ vựng chính / từ
  tham khảo / cụm từ).
- Mỗi từ: kana, kanji (nếu có), romaji, nghĩa tiếng Việt, loại từ, câu ví dụ,
  **audio phát âm**.
- Flashcard, quiz 3 dạng (Nhật→Việt, Việt→Nhật, nghe→chọn), bộ thẻ tự chọn,
  ôn tập ngắt quãng (SM-2), thống kê tiến trình, tìm kiếm từ điển.
- Đăng ký bằng email + mã xác thực; 1 tài khoản admin cấu hình qua ENV.

### 2.2 Out of scope (giai đoạn 1)
- Giải thích ngữ pháp chi tiết từng bài (user chốt: chỉ cần từ vựng đầy đủ).
- Luyện viết kanji theo nét (phase 2 — cần dữ liệu KanjiVG).
- App mobile native, thanh toán, mạng xã hội / bảng xếp hạng.

## 3. Vai trò người dùng & gói
| Role / Plan | Mô tả |
|------|-------|
| GUEST | Khách chưa đăng nhập: xem nội dung bài học/từ vựng (read-only). Không luyện tập, không lưu tiến trình |
| LEARNER + plan FREE | Học viên miễn phí: full luyện tập, bộ thẻ **riêng tư** |
| LEARNER + plan PREMIUM | Thêm quyền: **chia sẻ bộ thẻ công khai** cho cộng đồng |
| ADMIN | Mọi quyền: quản trị nội dung, gán/thu hồi gói PREMIUM, xem thống kê tổng hợp |

- Plan lưu ở `users.plan` (FREE/PREMIUM) + `users.planExpiresAt`. Phase 1 admin
  gán thủ công (FR-036); thanh toán online mua gói là phase 2 **[Giả định]**.
- Danh sách plan được chia sẻ công khai cấu hình qua ENV
  (`SHARING_ALLOWED_PLANS=PREMIUM,ADMIN`), không hard-code.

## 4. Yêu cầu chức năng (FR)

### Auth
- **FR-001** Đăng ký bằng email: nhập email → hệ thống gửi mã 6 số, mã có hiệu
  lực 5 phút, tối đa 5 lần nhập sai.
- **FR-002** Xác thực mã và đặt mật khẩu để hoàn tất đăng ký.
- **FR-003** Đăng nhập email + mật khẩu, cấp JWT (hết hạn 24h).
- **FR-004** Quên mật khẩu: gửi mã qua email → đặt lại mật khẩu mới.
- **FR-005** Tài khoản admin duy nhất được tạo từ ENV
  (`ADMIN_EMAIL`/`ADMIN_PASSWORD`) khi backend khởi động; không tự nâng quyền.

### Nội dung & từ điển
- **FR-010** Xem danh sách bài học theo cấp độ (N5: 1–25, N4: 26–50), kèm %
  hoàn thành của bản thân.
- **FR-011** Xem từ vựng của một bài, lọc theo phần (từ vựng chính / tham khảo /
  cụm từ); mỗi từ có nút phát audio.
- **FR-012** Tìm kiếm toàn bộ từ vựng theo kana / kanji / romaji / nghĩa Việt
  (gõ không dấu vẫn ra).
- **FR-013** Phát audio phát âm của từng từ (file MP3 tạo sẵn).

### Luyện tập
- **FR-020** Chế độ flashcard: lật thẻ (mặt trước → mặt sau), tự đánh giá
  Nhớ / Chưa nhớ; phạm vi: cả bài, một phần, hoặc bộ thẻ tự chọn.
- **FR-021** Quiz Nhật→Việt: hiện từ tiếng Nhật, chọn 1 trong 4 nghĩa.
- **FR-022** Quiz Việt→Nhật: hiện nghĩa Việt, chọn 1 trong 4 từ.
- **FR-023** Quiz nghe: phát audio, chọn 1 trong 4 từ đúng.
- **FR-037** Gõ phiên âm: hiện kanji (+ nghĩa Việt) của từ, người học gõ cách
  đọc; ô nhập chấp nhận romaji và tự chuyển thành hiragana realtime; hệ thống
  chấm đúng/sai từng từ, cập nhật SM-2 và lưu phiên học như các mode quiz khác.
- **FR-038** Sensei AI chat: hỏi-đáp ngữ pháp tiếng Nhật, AI giải thích bằng
  tiếng Việt (chi tiết: `docs/11-phase-b-sensei-ai.md`).
- **FR-039** AI chấm câu viết: học viên viết câu theo điểm ngữ pháp đang học,
  AI chấm điểm/sửa lỗi/giải thích tiếng Việt.
- **FR-040** AI sinh bài tập ngữ pháp từ một điểm ngữ pháp (trắc nghiệm/điền từ).
- **FR-041** Shadowing: video hội thoại theo bài + phụ đề song ngữ, loop từng
  câu, ghi âm giọng học viên để tự so sánh (ghi âm chỉ lưu local).
  (chi tiết: `docs/12-phase-c-noi-dung-gamification.md`)
- **FR-042** Gamification: 12 huy hiệu, bản đồ học tập 12 tuần, bảng xếp hạng
  tuần (opt-in, chỉ hiện biệt danh).
- **FR-043** Hàng đợi ôn tập duy nhất: trộn từ vựng SM-2 đến hạn + kanji đến
  hạn + câu trả lời sai; hiện số lượng + thời gian ước tính.
- **FR-024** Tạo / sửa / xóa **bộ thẻ tự chọn** (đặt tên, mô tả). Chia sẻ công
  khai xem FR-035.
- **FR-035** Chia sẻ bộ thẻ công khai: chỉ plan/role nằm trong
  `SHARING_ALLOWED_PLANS` (mặc định PREMIUM, ADMIN) được bật `isPublic=true`.
  FREE/GUEST không thấy nút chia sẻ. Có trang "Khám phá" liệt kê bộ thẻ công
  khai (tìm kiếm theo tên) — học viên đã đăng nhập được **sao chép** bộ công
  khai về tài khoản mình (bản sao riêng tư).
- **FR-036** Admin gán/thu hồi gói PREMIUM cho user (có/không thời hạn).
  Phase 1 làm thủ công; thanh toán online phase 2.
- **FR-025** Thêm từ vào bộ thẻ: từ kết quả tìm kiếm, từ trang bài học
  (chọn nhiều), hoặc toàn bộ một phần.
- **FR-026** Ôn tập đến hạn (spaced repetition SM-2): hệ thống gom các từ đến
  kỳ ôn của user, học viên ôn theo thứ tự ưu tiên.
- **FR-027** Sau mỗi câu trả lời, hệ thống cập nhật trạng thái từ
  (NEW → LEARNING → MASTERED) và lịch ôn tiếp theo.
- **FR-028** Dashboard tiến trình: số từ đã thuộc theo bài/cấp độ, streak ngày
  học liên tiếp, lịch sử các phiên học.
- **FR-029** Streak: học ít nhất 1 phiên/ngày được tính 1 ngày streak.

### Quản trị
- **FR-030** Admin CRUD bài học (thêm/sửa/xóa, sắp xếp).
- **FR-031** Admin CRUD từ vựng (sửa nghĩa, kana/kanji, loại từ, câu ví dụ).
- **FR-032** Admin import từ vựng từ file Excel theo mẫu (kiểm tra trùng,
  báo lỗi theo dòng).
- **FR-033** Admin regenerate audio cho từ mới/sửa (chạy batch, không chặn UI).
- **FR-034** Admin xem thống kê tổng hợp (số user, số từ, lượt học) — không xem
  dữ liệu cá nhân chi tiết của học viên.

## 5. Yêu cầu phi chức năng (NFR)
- **NFR-001 Hiệu năng:** trang bài học < 2s (mạng thường); audio preload khi
  vào chế độ luyện; quiz không chờ server giữa các câu (tải cả bộ câu hỏi 1 lần).
- **NFR-002 Bảo mật:** mật khẩu BCrypt; mã OTP hash trong DB, TTL 5 phút,
  rate-limit gửi lại (tối đa 1 mã/phút/email); JWT 24h; mọi API /admin/**
  yêu cầu ROLE_ADMIN; CORS khóa theo domain deploy.
- **NFR-003 Chất lượng dữ liệu:** 100% từ có nghĩa tiếng Việt do người
  kiểm duyệt (không machine-translate thô rồi publish); audio rõ, giọng chuẩn.
- **NFR-004 Mở rộng:** MongoDB Atlas (M0 lúc đầu); audio lưu static kèm backend,
  chuyển S3 khi vượt ~500MB.
- **NFR-005 Triển khai:** Render (backend Docker + frontend static site) theo
  mô hình đã vận hành ở noi-that-shop.

## 6. Chiến lược dữ liệu từ vựng
- Nguồn: word list cộng đồng cho Minna no Nihongo bài 1–50 (~2.156 từ:
  kana/kanji/romaji/loại từ theo từng bài) — **từ đơn lẻ là dữ kiện, không sao
  chép nội dung sách**.
- Nghĩa tiếng Việt + câu ví dụ: biên soạn/kiểm chứng thủ công theo từng bài
  trước khi import (không publish bản dịch máy chưa duyệt).
- Audio: pre-generate toàn bộ bằng edge-tts (miễn phí, giọng `ja-JP-NanamiNeural`),
  lưu `backend/src/main/resources/static/audio/{audioFile}.mp3` (tên file deterministic:
  `b{lessonNo:02d}-{orderInLesson:03d}.mp3`, vd. `b01-001.mp3`), DB chỉ lưu `audioUrl`
  dạng `/audio/{audioFile}`. Regenerate khi từ bị sửa.
- Admin import Excel là đường bổ sung/sửa dữ liệu sau này (FR-032).

## 7. Tech stack (đề xuất)
**Spring Boot 4.1.1 + MongoDB + React 19 + Vite** — đúng stack team đã build
2 project trước (insuren, noi-that-shop): JWT + OTP + ENV-admin đã có mẫu chạy
ổn định; document DB hợp tự nhiên với dữ liệu từ vựng / bộ thẻ / tiến trình
học (không cần join phức tạp); không có lý do kỹ thuật nào buộc đổi stack cho
bài toán này.

## 8. Câu hỏi mở — ĐÃ CHỐT (2026-10-14)
1. N4 = 25 bài (bài 26–50, quyển II) — ✅ user xác nhận.
2. SMTP Gmail (app password) qua ENV — ✅ OK.
3. Pre-generate ~2.156 file MP3 (~100–150MB) kèm backend — ✅ OK.
4. Bộ thẻ công khai: **theo role cấu hình** — PREMIUM + ADMIN được chia sẻ
   (ENV `SHARING_ALLOWED_PLANS`); GUEST/FREE không — ✅ (→ FR-035).
5. Thương hiệu: **Master JP** — ✅.
6. [Giả định] Phase 1 admin gán gói PREMIUM thủ công; thanh toán online phase 2.
