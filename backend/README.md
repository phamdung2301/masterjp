# Master JP — Backend

Website luyện tiếng Nhật theo giáo trình Minna no Nihongo (N5: bài 1–25, N4: bài 26–50).
Backend: **Spring Boot 4.1.1 + Java 17 + MongoDB**, JWT auth, OTP email, SM-2 spaced repetition.

## Chạy

```bash
cd backend
cp .env.example .env   # điền giá trị thật (xem bảng ENV bên dưới)
mvn spring-boot:run
```

Test (không cần MongoDB — chỉ test logic thuần):

```bash
mvn test
```

Build Docker (Render):

```bash
mvn package -DskipTests
java -jar target/master-jp-backend-0.1.0.jar
```

## ENV bắt buộc

| Biến | Mô tả |
|------|-------|
| `MONGODB_URI` | URI MongoDB (Atlas). Tên DB trong URI là optional |
| `MONGODB_DATABASE` | **Tên database EXPLICIT** (vd. `masterjp`). Driver sẽ lặng lẽ dùng DB `test` nếu thiếu — `MongoConfig` sẽ fail-fast thay vì để điều đó xảy ra |
| `JWT_SECRET` | Chuỗi ngẫu nhiên **≥ 32 ký tự**. Thiếu/ngắn → app từ chối khởi động |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | Gmail SMTP (dùng App Password). Gửi OTP |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_NAME` | Tài khoản admin duy nhất — tạo 1 lần lúc khởi động, **không reset mật khẩu khi restart**, không tự nâng quyền user thường |
| `SHARING_ALLOWED_PLANS` | Role/plan được chia sẻ bộ thẻ công khai (mặc định `PREMIUM,ADMIN`) |
| `FRONTEND_URL` | Domain frontend (CORS khóa theo domain này; nhiều domain cách nhau dấu phẩy) |
| `APP_TIMEZONE` | Múi giờ tính streak (mặc định `Asia/Ho_Chi_Minh`) |
| `MAIL_DEV_LOG_CODE` | Dev-only: in OTP ra console thay vì gửi mail. **Luôn `false` ở production** |

File `backend/.env` được tự động nạp lúc khởi động (không cần lib ngoài);
biến môi trường OS thật luôn override `.env`.

## API (tóm tắt)

Mọi lỗi trả `{code, message}` tiếng Việt, không lộ stack trace.
Response auth **luôn dùng field `accessToken`** (không dùng `token`).

| Method | Path | Auth | Mô tả |
|--------|------|------|-------|
| POST | `/api/auth/otp/request` | public | Gửi OTP 6 số `{email, purpose: REGISTER\|RESET_PASSWORD}` |
| POST | `/api/auth/register` | public | `{email, code, password, name}` → `{accessToken, user}` |
| POST | `/api/auth/login` | public | `{email, password}` → `{accessToken, user}` (sai 5 lần → khóa 15') |
| POST | `/api/auth/password/reset` | public | `{email, code, newPassword}` |
| GET | `/api/auth/me` | auth | `{id,email,name,role,plan,planExpiresAt,streakDays}` |
| GET | `/api/lessons?level=N5\|N4` | public | Kèm `totalWords`, `masteredWords` của user |
| GET | `/api/lessons/{id}` | public | Chi tiết bài |
| GET | `/api/lessons/{id}/words?section=` | public | Từ vựng + `studyStatus` (null nếu guest) |
| GET | `/api/words/search?q=` | public | Tìm kiếm (gõ không dấu vẫn ra), tối đa 50 |
| POST | `/api/quiz/generate` | auth | `{mode, scopeType, scopeId, limit}` → bộ câu hỏi (scope < 4 từ → 400). `scopeId` của SECTION có dạng `"lessonId:SECTION"` |
| PATCH | `/api/progress/answer` | auth | `{wordId,correct,timeMs}` → SM-2 ở backend. **Gọi sau MỖI câu** |
| GET | `/api/progress/due?limit=50` | auth | Từ đến hạn, quá hạn lâu xếp trước |
| GET | `/api/progress/summary` | auth | Dashboard: theo level/bài, streak, 10 phiên gần nhất |
| POST | `/api/sessions` | auth | Kết thúc phiên → `{id, streakDays}` (backend tự tính streak) |
| GET/POST | `/api/sets` | auth | Danh sách / tạo bộ thẻ (tối đa 50 bộ/user) |
| GET/PATCH/DELETE | `/api/sets/{id}` | auth | Chi tiết (owner hoặc public) / sửa / xóa |
| POST | `/api/sets/{id}/words` | auth | `{wordIds:[]}` → `{added,skipped}` (tối đa 500 từ/bộ) |
| DELETE | `/api/sets/{id}/words/{wordId}` | auth | Xóa từ khỏi bộ |
| PATCH | `/api/sets/{id}/share` | auth | `{isPublic}` → 403 "Nâng cấp gói để chia sẻ công khai" nếu không đủ quyền |
| GET | `/api/sets/public?q=&page=` | public | Trang Khám phá → `{items:[...], total}` |
| POST | `/api/sets/{id}/fork` | auth | Sao chép về tài khoản mình → `{id, name}` |
| * | `/api/admin/**` | **ROLE_ADMIN** | CRUD lessons/words, import Excel 2 bước, audio jobs, users/plan, stats, audit-logs |

### Admin endpoints chi tiết

- `GET/POST/PATCH/DELETE /api/admin/lessons`
- `GET /api/admin/words?lessonId=&q=&page=` · `POST/PATCH /api/admin/words` · `DELETE /api/admin/words/{id}` (**xóa mềm** `active=false`)
- `POST /api/admin/words/import/validate` (multipart `file`, cột: `lessonNo,section,kana,kanji,romaji,meaningVi,pos,exampleJa,exampleVi`) → `{totalRows, validRows, errors:[{row,message}]}`
- `POST /api/admin/words/import/confirm` `{rows:[...]}` → `{imported,skipped}` (upsert theo kana+lessonId+section)
- `POST /api/admin/audio/regenerate` `{wordIds:[]}` → `{jobId,status}` · `GET /api/admin/audio/jobs/{jobId}` (job async, FE poll)
- `GET /api/admin/users?q=&page=` · `PATCH /api/admin/users/{id}/plan` `{plan: FREE|PREMIUM, expiresAt?}`
- `GET /api/admin/stats` → `{totalUsers,totalWords,totalLessons,sessionsToday,sessionsByDay,topWrongWords}`
- `GET /api/admin/audit-logs?page=`

## Ghi chú kỹ thuật

- **MongoDB explicit DB**: `MongoConfig` resolve theo thứ tự URI-db → `MONGODB_DATABASE` → fail-fast. Không bao giờ lặng lẽ dùng `test`.
- **Preflight**: `.requestMatchers(OPTIONS, "/**").permitAll()` đặt **trước** mọi rule — preflight không mang token.
- **JWT**: claim `role` lưu dạng trần (`ADMIN`/`LEARNER`); filter dựng authority `ROLE_*` khớp `hasRole()`. Hết hạn 24h.
- **Jackson**: giữ mặc định `FAIL_ON_UNKNOWN_PROPERTIES=true`. Tên field DTO là hợp đồng với FE — không đổi tùy tiện.
- **SM-2** (`Sm2Calculator`, có unit test): đúng → repetitions+1, interval 1/6/×easeFactor ngày; sai → repetitions=0, interval=1, lapses+1, easeFactor=max(1.3, ef−0.2); MASTERED khi repetitions≥4 và interval≥30 ngày.
- **Streak**: mỗi `POST /api/sessions` so `lastStudyDate` với hôm nay (múi giờ `APP_TIMEZONE`).
- **Hết hạn PREMIUM**: check lúc login + `GET /me` + job mỗi giờ → về FREE; bộ thẻ đã public **giữ nguyên**.
- **Audio**: file tĩnh `src/main/resources/static/audio/{wordId}.mp3`, serve tại `/audio/{wordId}.mp3`; DB chỉ lưu URL. Pre-generate bằng edge-tts (`ja-JP-NanamiNeural`) trước khi deploy. Job regenerate phase 1 chạy nền in-memory, giữ nguyên shape API; chỗ gọi edge-tts thật đã đánh dấu `TODO(audio)` trong `AudioJobService`.
- **Mọi thao tác admin** ghi `audit_logs` (ai, action, target, before/after rút gọn).
