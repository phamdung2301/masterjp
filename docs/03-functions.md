# Function Catalog — Website luyện tiếng Nhật

> Quy ước persistence (bắt buộc): mỗi function khai báo **Dữ liệu sống ở đâu** —
> server DB (entity + API + thời điểm đồng bộ) hoặc local-only + lý do.
> Dữ liệu user không ghi local-only thì mặc định persist server.

## Nhóm A — Auth (actor: khách / learner)

### F-001 Đăng ký: gửi mã xác thực
- Mục đích: bắt đầu đăng ký bằng email.
- Input: email. Output: thông báo "mã đã gửi" (không tiết lộ email tồn tại hay chưa).
- Business rules: mã 6 số ngẫu nhiên, hash SHA-256 rồi lưu; hiệu lực 5 phút;
  tối đa 5 lần nhập sai/mã; rate-limit 1 mã/phút/email; email đã đăng ký →
  vẫn trả "mã đã gửi" nhưng thực chất gửi mail "tài khoản đã tồn tại".
- Edge: nhập sai định dạng email → 400; spam gửi lại → 429.
- **Dữ liệu:** server DB — entity `otp_codes`, API `POST /api/auth/otp/request`;
  document tự xóa nhờ TTL index. Không lưu gì ở client.

### F-002 Xác thực mã & tạo tài khoản
- Input: email, mã 6 số, mật khẩu, tên hiển thị. Output: JWT + user.
- Rules: mã đúng + còn hạn + chưa quá 5 lần sai → tạo user
  (`emailVerified=true`, role LEARNER), xóa otp_codes của email đó.
- Edge: mã hết hạn → yêu cầu gửi lại; mật khẩu < 8 ký tự → 400.
- **Dữ liệu:** server DB — entities `users`, `otp_codes` (xóa sau khi dùng),
  API `POST /api/auth/register`. Client lưu JWT vào localStorage (token, không
  phải dữ liệu nghiệp vụ).

### F-003 Đăng nhập / F-004 Quên mật khẩu
- F-003: email + password → JWT 24h. Sai 5 lần → khóa 15 phút (pattern
  noi-that-shop).
- F-004: `POST /api/auth/otp/request` (purpose=RESET_PASSWORD) →
  `POST /api/auth/password/reset` với mã + mật khẩu mới.
- **Dữ liệu:** server DB — `users`, `otp_codes`. Không local.
- Edge cases F-004: RESET_PASSWORD cho email chưa đăng ký → no-op im lặng
  (không gửi mail, không lưu OTP), vẫn trả message chung chung để chống enumeration.

## Nhóm B — Nội dung & từ điển (actor: learner, public read)

### F-010 Xem danh sách bài học theo cấp độ
- Input: level (N5/N4). Output: 25 bài + % từ đã MASTERED của chính user.
- **Dữ liệu:** server DB — entity `lessons` (API `GET /api/lessons?level=N5`);
  % hoàn thành tính từ `word_progress` của user qua API
  `GET /api/progress/summary`. Client được cache vào memory (React Query),
  không phải source of truth.

### F-011 Xem từ vựng của bài, lọc theo phần
- Input: lessonId, section? (VOCAB/REFERENCE/PHRASE). Output: danh sách từ
  có thứ tự, mỗi từ kèm trạng thái học của user (chấm màu NEW/LEARNING/MASTERED).
- **Dữ liệu:** server DB — `vocab_words` + join `word_progress` của user,
  API `GET /api/lessons/{id}/words?section=VOCAB`.

### F-012 Tìm kiếm từ điển
- Input: từ khóa (kana/kanji/romaji/nghĩa Việt, không dấu vẫn được).
- Rules: dùng text index + normalize (bỏ dấu tiếng Việt, lowercase romaji);
  tối đa 50 kết quả, ưu tiên khớp kana/kanji trước.
- **Dữ liệu:** server DB — `vocab_words`, API `GET /api/words/search?q=`.
  Không lưu lịch sử tìm kiếm ở phase 1.

### F-013 Phát audio từ vựng
- Nút loa trên mỗi thẻ từ → phát file MP3.
- **Dữ liệu:** file tĩnh — `backend/src/main/resources/static/audio/{audioFile}.mp3`
  (`{audioFile}` deterministic: `b{lessonNo:02d}-{orderInLesson:03d}.mp3`,
  vd. `b01-001.mp3`; sau này S3); DB chỉ lưu `audioUrl` dạng `/audio/{audioFile}`.
  Client preload audio của câu hỏi kế tiếp trong chế độ quiz nghe.

## Nhóm C — Luyện tập (actor: learner)

### F-020 Flashcard lật thẻ
- Luồng: chọn phạm vi (cả bài / một phần / bộ thẻ) → server trả danh sách từ
  (đảo thứ tự ngẫu nhiên) → user lật thẻ, tự đánh giá Nhớ/Chưa nhớ → mỗi đánh
  giá gọi API cập nhật tiến trình (F-027).
- **Dữ liệu:** đọc từ `vocab_words`/`custom_sets`; ghi vào `word_progress`
  qua `PATCH /api/progress/answer` **ngay sau mỗi thẻ** (không chờ hết phiên —
  tránh mất dữ liệu khi thoát giữa chừng). Tổng kết phiên ghi `study_sessions`
  qua `POST /api/sessions` khi user bấm kết thúc / hết thẻ.

### F-021 Quiz Nhật→Việt / F-022 Quiz Việt→Nhật / F-023 Quiz nghe
- Luồng chung: client xin bộ câu hỏi (`POST /api/quiz/generate`: mode, scope,
  limit=20) → server trả 20 câu, mỗi câu gồm từ đúng + 3 đáp án nhiễu (cùng
  level, ưu tiên cùng loại từ) → user trả lời từng câu, client chấm tại chỗ,
  mỗi câu gọi `PATCH /api/progress/answer` → hết 20 câu → `POST /api/sessions`.
- F-023: thay vì hiện chữ, câu hỏi phát audio (preload trước).
- Business rules: đáp án nhiễu không trùng từ đúng, không trùng nhau; quiz nghe
  chỉ dùng từ có audioUrl hợp lệ.
- Edge: phạm vi < 4 từ → báo "không đủ từ để tạo quiz", gợi ý mở rộng phạm vi.
- **Dữ liệu:** như F-020 — `word_progress` (ghi ngay mỗi câu), `study_sessions`
  (ghi khi kết thúc). Đáp án đúng của phiên hiện tại chỉ giữ trong memory của
  tab, không persist.

### F-037 Gõ phiên âm (typing practice)
- Mục đích: luyện nhớ cách đọc (phiên âm) của từ vựng qua thao tác gõ.
- Actor: learner (đăng nhập).
- Luồng: chọn phạm vi (bài / phần / bộ thẻ) → `POST /api/quiz/generate`
  (`mode=TYPING`) trả danh sách từ (không cần đáp án nhiễu) → mỗi lượt hiện
  1 từ, user gõ cách đọc vào ô nhập → Enter/"Kiểm tra" để chấm → phản hồi
  đúng (xanh) / sai (đỏ + hiện đáp án đúng) → "Tiếp" → hết phiên → màn kết quả.
- Inputs: `mode=TYPING`, scopeType, scopeId, limit (mặc định 20).
  Outputs: danh sách từ `{ wordId, kanji, kana, meaningVi, romaji? }`
  (FE chấm local, chỉ gửi `correct` lên server).
- Business rules:
  - Mặt trước: nếu từ có kanji → hiện **kanji + nghĩa Việt**; nếu không có
    kanji → chỉ hiện **nghĩa Việt** (không gợi ý romaji — phải nhớ cách đọc).
  - Ô nhập chấp nhận romaji, tự chuyển thành hiragana **realtime** (thư viện
    wanakana); ký tự Nhật user gõ trực tiếp được giữ nguyên.
  - Chuẩn hóa khi chấm: trim, lowercase phần romaji còn sót, chuyển toàn bộ
    input về hiragana rồi so với kana chuẩn (đã bỏ khoảng trắng hai phía);
    katakana trong kana chuẩn được chấp nhận ở cả dạng hiragana tương đương
    (vd. コーヒー chấp nhận こうひー).
  - Mỗi câu trả lời → `PATCH /api/progress/answer` ngay (đúng/sai → SM-2);
    hết phiên → `POST /api/sessions` với `mode=TYPING`.
- Edge cases:
  - Từ có kana chứa khoảng trắng hoặc ký tự 〜/・ (vd. "あの ひと", "〜さん"):
    chuẩn hóa bỏ mọi khoảng trắng trước khi so sánh; 〜 chấp nhận cả dạng
    đã thay thế.
  - Từ katakana dài (rōmaji có trường âm): wanakana chuyển "-" thành "ー" —
    so sánh sau chuẩn hóa nên không lệch.
  - Phạm vi < 1 từ → báo "không đủ từ", gợi ý mở rộng (typing không cần
    tối thiểu 4 từ như quiz trắc nghiệm).
- **Dữ liệu:** đọc `vocab_words` (qua `/api/quiz/generate`); ghi `word_progress`
  qua `PATCH /api/progress/answer` **ngay sau mỗi câu**; tổng kết ghi
  `study_sessions` qua `POST /api/sessions` (`mode=TYPING`). Đáp án đúng chỉ
  giữ trong memory của tab, không persist.

### F-024 CRUD bộ thẻ tự chọn
- Tạo/sửa/xóa bộ thẻ: tên (bắt buộc, ≤100 ký tự), mô tả, công khai/tạm riêng tư.
- **Quyền chia sẻ:** bật `isPublic=true` chỉ khi `users.plan` (hoặc role ADMIN)
  nằm trong `SHARING_ALLOWED_PLANS` (ENV, mặc định `PREMIUM,ADMIN`). Backend
  enforce ở API — FE chỉ ẩn nút. FREE cố tình gọi API → 403 "Nâng cấp gói để
  chia sẻ công khai".
- **Dữ liệu:** server DB — entity `custom_sets`, API CRUD `/api/sets`.
  Giới hạn 500 từ/bộ, 50 bộ/user (chống spam).

### F-025 Thêm từ vào bộ thẻ
- Từ trang bài học: tick chọn nhiều từ → "Thêm vào bộ thẻ" → chọn bộ có sẵn
  hoặc tạo bộ mới. Từ trang tìm kiếm: nút "+" trên từng kết quả.
- Rules: bỏ qua từ đã có trong bộ (báo "3 từ đã có sẵn, đã thêm 5 từ mới").
- **Dữ liệu:** server DB — `custom_sets.wordIds`, API
  `POST /api/sets/{id}/words` (nhận mảng wordIds).

### F-026 Ôn tập đến hạn (SM-2)
- `GET /api/progress/due` trả các từ có `nextReviewAt <= now`, sắp xếp theo
  độ trễ (quá hạn lâu ôn trước), tối đa 50 từ/phiên.
- Thuật toán SM-2: đúng → repetitions+1, interval = 1 / 6 / ×easeFactor ngày;
  sai → repetitions=0, interval=1 ngày, lapses+1, easeFactor −0.2 (min 1.3).
  MASTERED khi repetitions ≥ 4 và interval ≥ 30 ngày.
- **Dữ liệu:** server DB — `word_progress`, đọc/ghi qua API. Thuật toán chạy
  ở backend (FE chỉ gửi đúng/sai) để mọi client tính nhất quán.
- Kết thúc phiên ôn → `POST /api/sessions` với `mode=REVIEW_DUE`,
  `scopeType=REVIEW`, `scopeId="due"` (để streak F-029 được tính).

### F-027 Cập nhật tiến trình sau mỗi câu trả lời (dùng chung bởi F-020–F-023, F-026, F-037)
- Input: wordId, correct (Boolean), timeMs. Output: status mới + nextReviewAt.
- **Dữ liệu:** server DB — `word_progress` (upsert theo unique(userId, wordId)),
  API `PATCH /api/progress/answer`. Gọi ngay sau mỗi câu — đây là cam kết
  persistence cốt lõi: thoát app giữa chừng không mất tiến trình.

### F-028 Dashboard tiến trình & F-029 Streak
- F-028: `GET /api/progress/summary` → tổng từ NEW/LEARNING/MASTERED theo
  level, % hoàn thành từng bài, 10 phiên học gần nhất.
- F-029: mỗi `POST /api/sessions` thành công → backend so `lastStudyDate` với
  hôm nay: khác ngày → streakDays+1 (nếu liên tiếp) hoặc reset về 1.
- **Dữ liệu:** server DB — `word_progress` (aggregate), `study_sessions`,
  `users.streakDays/lastStudyDate`. Client không tự tính streak.

## Nhóm D — Quản trị (actor: ADMIN)

### F-030 CRUD bài học / F-031 CRUD từ vựng
- Admin sửa title, thêm/xóa/sửa từ (kana, kanji, romaji, nghĩa, loại từ, ví dụ,
  section, thứ tự). Sửa dùng `PATCH /api/admin/lessons/{id}` và
  `PATCH /api/admin/words/{id}` (không phải PUT — BE không có route PUT).
  Xóa từ → xóa mềm (đánh dấu inactive) để không vỡ
  `word_progress`/`custom_sets` đang tham chiếu. Đổi `orderInLesson` →
  `audioUrl` đổi theo tên file deterministic và tự tạo lại audio.
- **Dữ liệu:** server DB — `lessons`, `vocab_words`; mọi thao tác ghi
  `audit_logs` (ai, làm gì, before/after). API `/api/admin/**` yêu cầu
  ROLE_ADMIN.

### F-032 Import Excel từ vựng
- Admin tải file theo mẫu (cột: lessonNo, section, kana, kanji, romaji,
  meaningVi, pos, exampleJa, exampleVi) → server validate từng dòng →
  trả báo cáo "import 48/50, 2 lỗi ở dòng 12, 37" → admin xác nhận mới ghi DB.
- Contract (stateless, không importToken): `POST /api/admin/words/import/validate`
  trả `{ totalRows, validRows, errors: [{row, message}], rows: [{lessonNo, section,
  kana, kanji, romaji, meaningVi, pos, exampleJa, exampleVi}] }`; FE giữ `rows`
  và gửi lại ở `POST /api/admin/words/import/confirm` với body `{ rows: [...] }`
  → trả `{ imported, skipped }`. Từ mới được gán `orderInLesson` nối tiếp sau
  từ cuối của bài và `audioUrl` deterministic (`/audio/b{lessonNo}-{order}.mp3`).
- **Dữ liệu:** server DB — `vocab_words` (upsert theo kana+lessonId+section),
  `audit_logs` ghi IMPORT_EXCEL. File Excel chỉ tồn tại trong request, không lưu.

### F-033 Regenerate audio
- Admin bấm "tạo lại audio" cho từ mới/sửa → job chạy nền (edge-tts) →
  ghi đè file MP3, cập nhật `audioUrl` nếu đổi tên.
- Contract: `POST /api/admin/audio/regenerate { wordIds: [] }` →
  `{ jobId, status }`; `GET /api/admin/audio/jobs/{jobId}` →
  `{ jobId, status, total, done, failed }`; `status`: QUEUED / RUNNING / DONE / FAILED.
- **Dữ liệu:** file tĩnh + `vocab_words.audioUrl`. Job chạy async, admin xem
  trạng thái qua API (không cần realtime socket phase 1 — poll 5s).

### F-035 Khám phá & sao chép bộ thẻ công khai
- `GET /api/sets/public?q=` — liệt kê bộ thẻ `isPublic=true` (tên, tác giả,
  số từ, lượt sao chép), không yêu cầu đăng nhập (GUEST xem được).
- `POST /api/sets/{id}/fork` (cần đăng nhập) — sao chép thành bộ riêng tư của
  mình (giữ nguyên wordIds, tên + " (bản sao)").
- Rules: chủ bộ tắt `isPublic` → bản sao của người khác không bị ảnh hưởng;
  xóa bộ gốc → các bản sao vẫn giữ nguyên.
- **Dữ liệu:** server DB — `custom_sets` (đọc public không cần auth, fork ghi
  document mới với userId của người sao chép).

### F-036 Admin gán/thu hồi gói PREMIUM
- `PATCH /api/admin/users/{id}/plan` — body `{ plan: PREMIUM|FREE, expiresAt? }`.
  Chỉ ADMIN. Ghi `audit_logs`.
- Hết hạn (`planExpiresAt < now`, check lúc login + job mỗi giờ) → tự về FREE;
  các bộ đã chia sẻ công khai **giữ nguyên** (không giật lại — tránh mất mặt
  cộng đồng), chỉ chặn bật mới.
- **Dữ liệu:** server DB — `users.plan/planExpiresAt`, `audit_logs`.
- Số user, số từ/bài, tổng phiên học/ngày, từ bị sai nhiều nhất (để admin ưu
  tiên duyệt lại nghĩa/ví dụ).
- Contract: `GET /api/admin/stats` → `{ totalUsers, totalWords, totalLessons,
  totalSessions, sessionsToday, sessionsByDay: [{date, count}],
  topWrongWords: [{wordId, kana, kanji, romaji, meaningVi, lessonNo, wrongCount}] }`;
  các list admin (`/api/admin/users`, `/api/admin/words`, `/api/admin/audit-logs`)
  dùng shape chung `{ items: [...], total, page, size }`.
- **Dữ liệu:** aggregate server-side từ `users`, `study_sessions`,
  `word_progress`. Không trả dữ liệu cá nhân của learner.

## Ma trận truy vết FR → Function → Entity
| FR | Function | Entities |
|----|----------|----------|
| FR-001 | F-001 | otp_codes |
| FR-002 | F-002 | users, otp_codes |
| FR-003/004 | F-003/F-004 | users, otp_codes |
| FR-005 | (bootstrap, không qua API) | users |
| FR-010/011 | F-010/F-011 | lessons, vocab_words, word_progress |
| FR-012/013 | F-012/F-013 | vocab_words (+ file audio) |
| FR-020 | F-020, F-027 | vocab_words, custom_sets, word_progress, study_sessions |
| FR-021/022/023 | F-021/F-022/F-023, F-027 | vocab_words, word_progress, study_sessions |
| FR-037 | F-037, F-027 | vocab_words, word_progress, study_sessions |
| FR-024/025 | F-024/F-025 | custom_sets, vocab_words |
| FR-035 | F-035 | custom_sets |
| FR-036 | F-036 | users, audit_logs |
| FR-026/027 | F-026/F-027 | word_progress |
| FR-028/029 | F-028/F-029 | word_progress, study_sessions, users |
| FR-030/031/032/033 | F-030/F-031/F-032/F-033 | lessons, vocab_words, audit_logs (+ file audio) |
| FR-034 | F-034 | users, study_sessions, word_progress |
