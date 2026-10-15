# Master JP — Website luyện tiếng Nhật (Minna no Nihongo)

Luyện từ vựng N5 (bài 1–25) + N4 (bài 26–50) theo giáo trình Minna no Nihongo:
flashcard, quiz 3 dạng (Nhật→Việt, Việt→Nhật, nghe→chọn), chế độ "Gõ phiên âm"
(romaji tự chuyển hiragana — wanakana), ôn ngắt quãng SM-2,
bộ thẻ tự chọn + chia sẻ công khai (gated theo gói), dashboard tiến trình.

**Stack:** Spring Boot 4.1.1 (Java 17) + MongoDB · React 19 + Vite
Tài liệu thiết kế (source of truth): `docs/01-SRE.md`, `02-ERD.md`, `03-functions.md`, `04-user-stories.md`

## Cấu trúc

```
master-jp/
├── backend/            # Spring Boot API (com.masterjp) — xem backend/README.md
├── frontend/           # React 19 + Vite — xem frontend/README.md
├── data/seed/          # seed-lessons-1-3.json + seed-lessons-4-50.json (2.692 từ, full 50 bài, nghĩa Việt thủ công)
├── data/templates/     # word-import-template.csv (mẫu import Excel cho admin)
├── scripts/            # seed-import.py (+ .ps1/.sh), generate-audio.py (edge-tts),
│                       # generate-audio-google.py (Google TTS), build-seed-1-3.py
├── docs/               # SRE / ERD / functions / user stories
└── research/           # minna-vocab-50.json (2.692 từ/50 bài, nguồn MinnaNoDS)
```

## Chạy local (Windows — Java 21, Node 25)

### 0. Chuẩn bị
- MongoDB đang chạy (service Automatic). Tên database `master_jp` cấu hình
  explicit trong `backend/.env`, không bao giờ rơi vào `test`.
- Java 21, Maven 3.9+, Node 25, Python 3 (+ `pip install pymongo`; chỉ cần thêm
  `edge-tts` nếu muốn sinh audio bằng giọng Nanami).

### 1. Backend
```powershell
cd backend
copy .env.example .env
# Mở .env điền đầy đủ 14 biến theo checklist bên dưới (bắt buộc:
# MONGODB_URI, JWT_SECRET ≥ 32 ký tự, MAIL_* app password, ADMIN_*).
mvn spring-boot:run   # API tại http://localhost:8080
```
Lần chạy đầu `AdminBootstrap` tạo tài khoản admin từ ENV (chỉ tạo 1 lần).

### 2. Import dữ liệu full 50 bài (2.692 từ)
```powershell
cd scripts
python seed-import.py
```
- Đọc cả 2 file `data/seed/seed-lessons-1-3.json` (151 từ) và
  `data/seed/seed-lessons-4-50.json` (2.541 từ), ghi collection `lessons`
  (upsert theo `(level, lessonNo)`) và `vocab_words` (upsert theo
  `(lessonId, kana, section)`).
- Script **tự tạo unique index** `(lessonId, kana, section)` trước khi import.
- **Idempotent:** chạy lại N lần không tạo trùng — chạy lại an toàn.
- ENV: `MONGODB_URI` (default `mongodb://localhost:27017`), `MONGODB_DATABASE`
  (default `master_jp` — khai báo explicit, driver không bao giờ rơi vào `test`).
- Khi deploy: chạy script này trỏ **thẳng vào MongoDB Atlas** của production
  (set 2 ENV trên thành URI Atlas) để nạp dữ liệu một lần — không cần seed thủ công trên server.

### 3. Audio phát âm (2.692 file MP3)
- File nằm ở `backend/src/main/resources/static/audio/`, tên
  `b{NN}-{iii}.mp3` (VD: `b01-001.mp3`) — khớp chính xác field `audioUrl`
  (`/audio/b{NN}-{iii}.mp3`) trong DB; backend serve tại `/audio/**`.
- Sinh bằng script **Google TTS miễn phí**:
```powershell
cd scripts
python generate-audio-google.py   # idempotent: bỏ qua file đã có, ngắt giữa chừng chạy lại an toàn
```
- **Ghi chú giọng đọc:** kế hoạch gốc là edge-tts giọng `ja-JP-NanamiNeural`
  (`scripts/generate-audio.py`), nhưng mạng tới Bing bị chặn trong môi trường build
  nên phase này dùng Google TTS. Nếu muốn giọng Nanami, chạy
  `python scripts/generate-audio.py` trên máy có internet mở — script ghi đè
  lên cùng thư mục, tên file và `audioUrl` không đổi.
- Spot-check sau khi sinh: mở vài file MP3 bất kỳ (>1KB mới hợp lệ) và
  kiểm tra `audioUrl` của vài từ trong DB đúng pattern `/audio/...`.

### 4. Frontend
```powershell
cd frontend
npm install        # lần đầu sau khi thêm dep wanakana
npm run build      # verify build pass (hoặc npm run dev để chạy thử)
# dev: http://localhost:5173 — proxy /api và /audio về backend :8080
```

## Checklist ENV (`backend/.env`)

14 biến trong `backend/.env.example` (12 biến nghiệp vụ + 2 biến dev/timezone):

| Nhóm | Biến | Ví dụ / ghi chú |
|------|------|-----------------|
| Mongo | `MONGODB_URI` | `mongodb+srv://<user>:<pass>@<cluster>.mongodb.net/...` (Atlas lúc deploy) |
| Mongo | `MONGODB_DATABASE` | `masterjp` — explicit, không rơi vào `test` |
| JWT | `JWT_SECRET` | chuỗi ngẫu nhiên **≥ 32 ký tự** — app từ chối khởi động nếu thiếu/ngắn |
| Mail | `MAIL_HOST` / `MAIL_PORT` | `smtp.gmail.com` / `587` |
| Mail | `MAIL_USERNAME` / `MAIL_PASSWORD` | email + **app password Gmail** (không phải mật khẩu Gmail) |
| Mail (dev) | `MAIL_DEV_LOG_CODE` | `false` — chỉ `true` ở dev để in OTP ra console, **LUÔN false ở production** |
| Admin | `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_NAME` | tài khoản admin khởi tạo duy nhất (không reset khi restart) |
| Sharing | `SHARING_ALLOWED_PLANS` | `PREMIUM,ADMIN` (FR-035) |
| CORS | `FRONTEND_URL` | domain frontend deploy, nhiều domain cách nhau dấu phẩy |
| Timezone | `APP_TIMEZONE` | `Asia/Ho_Chi_Minh` — múi giờ tính streak |

## Checklist trước deploy (chạy trên PC)

1. **Backend test:** `cd backend && mvn test` — đặc biệt kiểm tra lại sau 3 fix mới nhất:
   `OtpService` (no-op khi email chưa đăng ký), `SetService` (fork limit),
   `QuizService` (thêm case `TYPING` vào 2 switch `promptText`/`optionText`).
2. **Frontend build:** `cd frontend && npm install && npm run build` — build phải pass
   với dependency mới `wanakana` (chế độ Gõ phiên âm).
3. **Seed dữ liệu:** chạy `python scripts/seed-import.py` trỏ thẳng Atlas production,
   verify đủ 50 bài / 2.692 từ (`db.vocab_words.countDocuments()`).
4. **Audio:** sinh đủ 2.692 MP3 bằng `generate-audio-google.py`, spot-check vài
   `audioUrl` mở được file thật qua `/audio/...`.
5. **ENV:** điền đủ 14 biến trên Render (đặc biệt `JWT_SECRET` ≥ 32 ký tự,
   `MAIL_DEV_LOG_CODE=false`, `ADMIN_PASSWORD` mạnh) — OS env trên Render
   luôn ưu tiên hơn `.env`.

## Lưu ý kỹ thuật (đọc trước khi chạy)

1. **Code chưa được compile ở môi trường build** (không có JDK) — đã review thủ công kỹ
   + QA gate fix 10 mismatch BE↔FE + 3 fix sau QA, nhưng hãy chạy `mvn test` và
   `npm run build` trước khi dùng.
2. Backend đăng ký `DotenvEnvironmentListener` qua `META-INF/spring.factories`
   (deprecated từ Boot 2.7) — **cần verify `.env` có được nạp khi chạy trên Boot 4.1.1**;
   nếu không, chuyển sang `ApplicationListener` như mẫu noi-that-shop.
3. `audioUrl` chốt pattern `/audio/{audioFile}` deterministic
   (`b{lessonNo:02d}-{orderInLesson:03d}.mp3`) — đã đồng bộ ERD + seed + script audio.
4. Quiz phạm vi SECTION dùng `scopeId = "lessonId:SECTION"` (FE/BE đã thống nhất).
5. ScopeType có thêm `REVIEW` cho phiên ôn đến hạn (đã vá vào ERD + F-026).
6. **Finding còn mở — chờ quyết định của user:**
   - (a) `JwtAuthFilter` query DB (`userRepository.findById`) **mỗi request**
     để xác thực user còn tồn tại/active — bảo mật tốt (token của user bị xóa/
     khóa sẽ hết hiệu lực ngay) nhưng tốn 1 read/request. Giữ nguyên hay cache
     ngắn hạn?
   - (b) Race condition `PATCH /api/progress/answer`: 2 request đồng thời cùng
     `(userId, wordId)` khi chưa có progress doc → cả hai `find` đều trống →
     cả hai `insert` → một bên dính `DuplicateKeyException` (unique
     `(userId, wordId)`). Hiện chưa có retry. Có implement retry/catch không?

## Phase 2 (chưa làm)

- Deploy Render (backend Docker + frontend static + MongoDB Atlas) theo mô hình noi-that-shop.
- Thanh toán online mua gói PREMIUM (hiện admin gán thủ công — FR-036).
- Luyện viết kanji (cần dữ liệu KanjiVG), ngữ pháp chi tiết — out of scope đã chốt.
