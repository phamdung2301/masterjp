# DOC-12 — Phase C: Nội dung & Gamification (Shadowing · Huy hiệu · Review queue)

**Ngày:** 2026-10-23 · **Trạng thái:** SPEC — chưa code, tài liệu này là source of truth cho implementer BE/FE
**Kế hoạch gốc:** `~/workspace/master-jp-v2/KE-HOACH-PHAT-TRIEN.md` Phần 5 (Phase C: C1/C2/C3)
**IA liên quan:** `docs/10-v2-ia-redesign.md` (Lesson Hub, tab Ôn tập `/on-tap`, tab Cá nhân `/ca-nhan`)
**Branch triển khai (dự kiến):** `v2/phase-c-noi-dung-gamification`
**⚠️ Code Java viết trong môi trường không-JDK: CHƯA COMPILE** — ghi rõ trong PR, dặn user đọc log build Render/Docker.

## 0. Quyết định đánh số (binding)

Kiểm tra `docs/03-functions.md` (2026-10-23): số cao nhất đang dùng là **F-037**
(F-038/039/040 của Phase B — DOC-11 — chưa được merge vào file này, không xung đột).
Theo operating rule của doc-writer (số mới nối tiếp số cuối, không sửa requirement đã duyệt),
3 function mới được đánh số:

| Phase | Số chính thức | Tên |
|---|---|---|
| C1 | **F-041** | Shadowing hội thoại |
| C2 | **F-042** | Gamification (huy hiệu · heatmap · bảng xếp hạng tuần) |
| C3 | **F-043** | Review queue thống nhất (SM-2 từ vựng + kanji + câu sai) |

FR tương ứng: **FR-041 / FR-042 / FR-043** (mirror F). **[Giả định]** BE/FE implementer
dùng đúng số này; orchestrator cập nhật `01-SRE.md`, `03-functions.md`, `02-ERD.md`,
`04-user-stories.md` sau khi spec này được duyệt (mục 11).

---

## 1. Tổng quan & vị trí trong IA

Phase C đưa Master JP gần hơn với core loop "Học → Luyện → Ôn → Xem tiến trình":

- **F-041 (Shadowing)** — entry point trong **Lesson Hub**, tab mới "Shadowing" cạnh
  Từ vựng/Ngữ pháp/Kanji (chỉ hiện khi bài có dữ liệu shadowing — mục 3.6).
  User xem video hội thoại có phụ đề song ngữ Nhật–Việt, tua từng câu, lặp đoạn,
  ghi âm giọng mình và so sánh với bản gốc.
- **F-042 (Gamification)** — tab **Cá nhân → Thành tích**: huy hiệu thật (thay 8
  badge tĩnh của Phase A), heatmap 12 tuần, bảng xếp hạng tuần (opt-in).
- **F-043 (Review queue)** — rework tab **Ôn tập** (`/on-tap`): 1 hàng đợi duy nhất
  trộn từ vựng đến hạn + kanji đến hạn + câu sai (từ quiz và từ Sensei AI),
  hiện số lượng + thời gian ước tính + streak như Phase A đã làm UI.

**Ràng buộc kiến trúc (binding):**
- Không thêm dependency Maven/npm mới. Ghi âm dùng **MediaRecorder API** của browser
  (built-in). Heatmap/badge render bằng CSS/SVG (không chart lib).
- Không emoji làm icon — huy hiệu dùng ký tự Nhật (ngữ 語 · thẻ 札 · …) như Phase A.
- Backend pattern theo Phase B: `@RestController`, DTO `record`, `@AuthenticationPrincipal
  UserPrincipal`, error shape `{code, message}` qua `ApiException` + `GlobalExceptionHandler`.
- **Giữ bề mặt backend MỚI ở mức tối thiểu** (xem từng function).

---

## 2. FR mới

- **FR-041** Shadowing: user xem video hội thoại theo bài học, có phụ đề song ngữ
  Nhật–Việt từng câu; bấm vào câu để nhảy video tới đúng thời điểm; lặp 1 câu/đoạn;
  ghi âm giọng đọc của mình, phát lại để so sánh với bản gốc. Hoàn thành 1 phiên
  shadowing được tính vào streak và huy hiệu như một phiên học.
- **FR-042** Gamification:
  - (a) Huy hiệu: hệ thống tự trao huy hiệu khi user đạt điều kiện; tab Thành tích
    hiện tất cả huy hiệu + trạng thái đã đạt/chưa đạt + ngày đạt.
  - (b) Heatmap: 12 tuần gần nhất, mỗi ngày 1 ô màu theo mức độ hoạt động học.
  - (c) Bảng xếp hạng tuần (ISO week): top 50 theo XP; **chỉ** user đã opt-in mới
    xuất hiện; hiển thị nickname tự chọn, không bao giờ lộ tên thật/email.
- **FR-043** Review queue thống nhất: 1 endpoint trả hàng đợi ôn tập gồm 3 loại item
  `{kind: "vocab" | "kanji" | "wrong"}` kèm số lượng từng loại + tổng thời gian ước tính;
  kanji có SM-2 riêng (như từ vựng); câu trả lời sai (quiz + Sensei AI) được ghi lại và
  xuất hiện trong queue cho tới khi user trả lời đúng.

---

## 3. F-041 — Shadowing

### 3.1. Quyết định kiến trúc (binding): nội dung là static JSON, backend surface = 0 endpoint mới

Nội dung shadowing (video + phụ đề) là **nội dung tĩnh** — theo đúng nguyên tắc đã rút ra
từ repo IELTS (kế hoạch Phần 1, dòng 2: "nội dung tĩnh = file JSON, không qua DB").
Video và phụ đề nằm trong repo frontend, không đi qua MongoDB, không endpoint mới.

Hoàn thành phiên shadowing được ghi nhận bằng **`POST /api/sessions` hiện có**
(`mode=SHADOWING` mới, `scopeType=LESSON`, `scopeId=<lessonId>`) — nhờ đó streak F-029
và heatmap/badge tự động tính đúng mà không cần code riêng.

### 3.2. Seed format — `shadowing_videos` (static JSON)

File: `frontend/public/data/shadowing/index.json` — danh mục video theo bài:

```json
{
  "videos": [
    { "id": "sh01", "lessonNo": 1, "title": "はじめまして", "titleVi": "Rất vui được gặp bạn", "durationSec": 75 }
  ]
}
```

File chi tiết: `frontend/public/data/shadowing/sh01.json`:

| Field | Type | Bắt buộc | Rule |
|---|---|---|---|
| `id` | string | có | khớp `id` trong index, vd. `"sh01"` |
| `lessonNo` | integer | có | 1–50 |
| `title` / `titleVi` | string | có | tiêu đề hội thoại |
| `video` | object | có | xem bảng nguồn video bên dưới |
| `video.type` | string | có | `"local"` hoặc `"youtube"` |
| `video.src` | string | có | `"local"` → đường dẫn public asset, vd. `"/assets/shadowing/sh01.mp4"`; `"youtube"` → videoId, vd. `"dQw4w9WgXcQ"` (embed `https://www.youtube.com/embed/<id>`) |
| `lines` | array | có | phụ đề từng câu, ≥ 1 item |
| `lines[].start` / `lines[].end` | number | có | giây (float), `start < end`, không overlap |
| `lines[].ja` | string | có | câu tiếng Nhật |
| `lines[].kana` | string | không | cách đọc (nếu `ja` có kanji) |
| `lines[].vi` | string | có | dịch tiếng Việt |

**Quy tắc video asset:** file mp4 ≤ **25MB** đặt ở `frontend/public/assets/shadowing/`
(vượt → dùng `youtube` type). **[Giả định]** video do user cung cấp/upload tay sau
merge (như 8 ảnh mascot Phase A); code render đẹp khi video chưa có (hiện trạng thái
"Sắp có video").

### 3.3. Ghi âm — local-only (không backend, không upload)

- Dùng `MediaRecorder` API: nút "Ghi âm" → thu giọng user trong khi video phát câu
  đang chọn; dừng → phát lại bản ghi để so sánh (2 nút play: "Nghe gốc" / "Nghe mình").
- **Bản ghi KHÔNG upload server, KHÔNG persist** — giữ trong memory của tab, mất khi
  reload. Lý do: tránh chi phí lưu trữ + vấn đề pháp lý với dữ liệu giọng nói ở v1.
  Có nút "Tải bản ghi về máy" (download blob, client-side).
- Quyền microphone bị từ chối → hiện hướng dẫn bật quyền, các tính năng xem/phụ đề
  vẫn dùng bình thường.

### 3.4. Tính năng phát lại (FE)

- Bấm vào 1 dòng phụ đề → video seek tới `start` của dòng đó và phát.
- Nút "Lặp câu" (loop 1 dòng đang chọn) và "Lặp đoạn A–B" (đánh dấu 2 dòng).
- Tốc độ phát: 0.75x / 1x / 1.25x (giữ nguyên pitch nếu browser hỗ trợ).
- Highlight dòng phụ đề đang phát (đồng bộ theo `timeupdate`).

### 3.5. Ghi nhận phiên học (dùng API hiện có — KHÔNG endpoint mới)

Khi user bấm "Hoàn thành phiên" (sau khi đã xem ≥ 80% tổng thời lượng video — FE tự tính):

```
POST /api/sessions
{
  "mode": "SHADOWING",
  "scopeType": "LESSON",
  "scopeId": "<lessonId của bài>",
  "totalItems": 1,
  "correctCount": 1,
  "items": [{ "wordId": "shadow:sh01", "correct": true, "timeMs": <ms đã xem> }],
  "startedAt": "<ISO>",
  "finishedAt": "<ISO>"
}
```

**Thay đổi enum (binding):** thêm `SHADOWING` vào `SessionMode` (file
`domain/SessionMode.java`). `ScopeType.LESSON` + `scopeId=lessonId` đã đủ —
không thêm ScopeType mới. Streak F-029 ("mỗi POST /api/sessions thành công") tự
động tính shadowing — đây là hành vi mong muốn (shadowing là hoạt động học thật).

### 3.6. Entry point (FE)

- Lesson Hub: thêm tab thứ 5 **"Shadowing"** sau tab Kanji — **chỉ render khi**
  `index.json` có video cho `lessonNo` hiện tại (fetch 1 lần lúc mount Hub, cache
  trong memory). Bài chưa có video → tab ẩn hoàn toàn, không hiện placeholder.
- Không thêm route mới (shadowing sống trong Lesson Hub, không phải page độc lập —
  đúng nguyên tắc IA "practice là động từ").

### 3.7. Persistence contract — F-041

| Dữ liệu | Sống ở đâu |
|---|---|
| Video + phụ đề | **Static JSON + file mp4 trong repo frontend** (`public/data/shadowing/`, `public/assets/shadowing/`); không qua MongoDB, không API |
| Bản ghi âm của user | **Local-only** — memory của tab (mất khi reload); không upload, không persist |
| Hoàn thành phiên | **Server DB** — collection `study_sessions` hiện có, qua `POST /api/sessions` (mode `SHADOWING`) |

### 3.8. Acceptance criteria — F-041

- [ ] Bài có video → tab Shadowing hiện trong Lesson Hub; bài chưa có → tab ẩn.
- [ ] Video local và YouTube embed đều phát được; bấm dòng phụ đề nhảy đúng thời điểm (±0.5s).
- [ ] Loop 1 câu và loop A–B hoạt động; tốc độ 0.75x/1x/1.25x đổi được.
- [ ] Ghi âm → phát lại bản ghi được; từ chối quyền mic → các tính năng khác vẫn chạy.
- [ ] "Hoàn thành phiên" (≥80% thời lượng) → `POST /api/sessions` mode `SHADOWING` thành công; streak tăng.
- [ ] Không có endpoint backend mới nào cho shadowing (ngoài enum `SHADOWING`).

---

## 4. F-042 — Gamification

### 4.1. Badge catalog (binding — định nghĩa trong code backend, KHÔNG bảng DB riêng)

12 huy hiệu. Icon = ký tự Nhật (không emoji). Điều kiện được kiểm tra **sau mỗi
`POST /api/sessions` thành công** (hook trong `SessionService`, mục 4.3).

| # | code | icon | Tên | Điều kiện trao |
|---|---|---|---|---|
| 1 | `FIRST_SESSION` | 始 | Khởi động | Hoàn thành 1 phiên học bất kỳ |
| 2 | `VOCAB_100` | 語 | Vốn từ 100 | `word_progress` status=MASTERED ≥ 100 |
| 3 | `VOCAB_500` | 語 | Vốn từ 500 | MASTERED ≥ 500 |
| 4 | `STREAK_7` | 復 | Tuần rực lửa | streak ≥ 7 ngày |
| 5 | `STREAK_30` | 復 | Tháng bền bỉ | streak ≥ 30 ngày |
| 6 | `LESSONS_5` | 文 | Chạy đà | Hoàn thành 5 bài học (bài có ≥80% từ MASTERED — cùng định nghĩa "done" với Phase A `CaNhan.jsx`) |
| 7 | `LESSONS_25` | 文 | Nửa chặng N5 | Hoàn thành 25 bài |
| 8 | `LESSONS_50` | 漢 | Chinh phục 50 | Hoàn thành 50 bài |
| 9 | `SHADOW_1` | 影 | Luyện nói đầu tiên | Có ≥ 1 session `mode=SHADOWING` |
| 10 | `SENSEI_10` | 師 | Học trò Sensei | ≥ 10 lượt dùng Sensei AI (`ai_usage.used` cộng dồn — đọc tổng, không chỉ ngày hiện tại) |
| 11 | `REVIEW_100` | 復 | Bậc thầy ôn tập | Trả lời đúng ≥ 100 item trong review queue (`kind` bất kỳ — đếm từ `word_progress.correctCount + kanji_progress.correctCount` cộng dồn ≥ 100) **[Giả định]** |
| 12 | `NIGHT_OWL` | 月 | Cú đêm chăm chỉ | Hoàn thành 1 phiên sau 23:00 (giờ Asia/Ho_Chi_Minh) |

### 4.2. Collection mới `user_badges`

| Field | Type | Ghi chú |
|---|---|---|
| `_id` | ObjectId | |
| `userId` | ObjectId | index |
| `badgeCode` | String | 1 trong 12 code ở mục 4.1 |
| `earnedAt` | Date | |
| Unique index: `(userId, badgeCode)` | | chống trao trùng khi 2 session xong song song |

### 4.3. Award logic (backend)

- Trong `SessionService.recordSession()` sau khi lưu session thành công: gọi
  `BadgeService.checkAndAward(userId)` — đánh giá 12 điều kiện, insert bản ghi cho
  badge nào đạt mà chưa có (dùng unique index để idempotent).
- Điều kiện đếm từ dữ liệu hiện có (`word_progress`, `study_sessions`, `users.streakDays`,
  `ai_usage`) — không thêm collection phụ.
- Response của `POST /api/sessions` mở rộng thêm field **`newBadges`: string[]**
  (danh sách code huy hiệu vừa đạt trong phiên này, có thể rỗng `[]`) — FE dùng để
  hiện toast/confetti "Nhận huy hiệu mới!". **[Giả định]** implementer thêm field này
  vào DTO response hiện có (không đổi field cũ).

### 4.4. Heatmap 12 tuần

**`GET /api/gamification/heatmap?weeks=12`** — cần JWT.

- `weeks`: integer tùy chọn, 1–52, mặc định `12`.
- Aggregation trên `study_sessions`: `match {userId, finishedAt >= <weeks*7 ngày trước>}`,
  group theo ngày (string `yyyy-MM-dd`, múi giờ **Asia/Ho_Chi_Minh**), tính
  `minutes = round(sum(finishedAt-startedAt)/60000)`.
- Response `200`:

```json
{
  "weeks": 12,
  "days": [
    { "date": "2026-08-03", "minutes": 25, "sessions": 2 },
    { "date": "2026-08-04", "minutes": 0, "sessions": 0 }
  ]
}
```

- `days`: đủ `weeks*7` ngày liên tiếp, kết thúc ở hôm nay, ngày không học → `minutes: 0`.
- FE map `minutes` → 4 mức màu washi (0 / 1–15 / 16–45 / >45) bằng CSS.

### 4.5. Bảng xếp hạng tuần — opt-in + nickname ẩn danh (privacy binding)

**Công thức XP (binding):** `XP_session = correctCount * 10 + totalItems * 2`.
XP tuần = tổng XP các session có `finishedAt` trong ISO week. Tuần tính theo
**Asia/Ho_Chi_Minh** (thứ Hai 00:00 → Chủ nhật 23:59).

**`GET /api/gamification/leaderboard?week=`** — cần JWT.

- `week`: string tùy chọn, format ISO `"YYYY-Www"` (vd. `"2026-W43"`); mặc định tuần hiện tại;
  sai format → `400 VALIDATION_ERROR`.
- Chỉ user có `users.leaderboardOptIn = true` mới được aggregate.
- Response `200`:

```json
{
  "week": "2026-W43",
  "weekStart": "2026-10-19T00:00:00+07:00",
  "weekEnd": "2026-10-25T23:59:59+07:00",
  "entries": [
    { "rank": 1, "nickname": "Tanu-9f3a", "xp": 1240, "sessions": 14, "isMe": false },
    { "rank": 7, "nickname": "Hana-chan", "xp": 620, "sessions": 8, "isMe": true }
  ],
  "myRank": 7,
  "totalParticipants": 132
}
```

- Tối đa **50** entries (`limit` hard-code, không ENV).
- **Privacy (binding):** response KHÔNG BAO GIỜ chứa email, tên thật, userId.
  `nickname` do user tự đặt; `isMe` chỉ true cho chính caller.
- User chưa opt-in gọi endpoint vẫn được (xem bảng) nhưng `myRank` = `null`,
  không có entry `isMe`.

**`PATCH /api/gamification/profile`** — cần JWT. Cài đặt hiển thị xếp hạng:

| Field | Type | Rule |
|---|---|---|
| `nickname` | string | tùy chọn; sau `trim()`: 1–20 ký tự; không chứa email/khoảng trắng đầu-cuối |
| `leaderboardOptIn` | boolean | tùy chọn |

Response `200`: `{ "nickname": "Hana-chan", "leaderboardOptIn": true }`.
- Mặc định: `leaderboardOptIn=false`, `nickname="Tanu-" + 4 ký tự cuối userId`.
- Tắt opt-in → biến mất khỏi bảng ngay ở lần aggregate tiếp theo (không cần xóa dữ liệu).

**Thay đổi collection `users` (không collection mới):** thêm 2 field
`leaderboardOptIn` (Boolean, default `false`), `nickname` (String).

### 4.6. Contract `GET /api/gamification/badges` — cần JWT

Response `200` — trả **cả 12 badge** kèm trạng thái (FE render tab Thành tích):

```json
{
  "badges": [
    { "code": "FIRST_SESSION", "icon": "始", "name": "Khởi động",
      "description": "Hoàn thành 1 phiên học bất kỳ",
      "earned": true, "earnedAt": "2026-10-20T08:12:00+07:00" },
    { "code": "VOCAB_100", "icon": "語", "name": "Vốn từ 100",
      "description": "Thuộc 100 từ vựng",
      "earned": false, "earnedAt": null }
  ],
  "earnedCount": 3,
  "totalCount": 12
}
```

- `icon`/`name`/`description` lấy từ catalog mục 4.1 (backend hard-code, FE không
  tự bịa text).
- Endpoint này **thay thế** logic badge tĩnh trong `CaNhan.jsx` (Phase A, dòng 92–98) —
  FE xóa mảng badge hard-code, gọi API.

### 4.7. Persistence contract — F-042

| Dữ liệu | Sống ở đâu |
|---|---|
| Huy hiệu đã đạt | **Server DB** — collection mới `user_badges` (mục 4.2); catalog 12 badge hard-code trong backend |
| Heatmap | **Suy ra từ** `study_sessions` hiện có (aggregate lúc request, không lưu riêng) |
| Xếp hạng tuần | **Suy ra từ** `study_sessions` + field mới `users.leaderboardOptIn`/`users.nickname` (aggregate lúc request) |
| XP | Công thức mục 4.5, tính lúc aggregate — không lưu |

### 4.8. Acceptance criteria — F-042

- [ ] Xong 1 phiên học → `POST /api/sessions` trả `newBadges` chứa `FIRST_SESSION` (lần đầu).
- [ ] Đạt streak 7 → badge `STREAK_7` xuất hiện đúng 1 bản ghi trong `user_badges` (2 session song song không tạo trùng).
- [ ] `GET /api/gamification/badges` trả đủ 12 badge, `earned` đúng trạng thái thật.
- [ ] `GET /api/gamification/heatmap?weeks=12` trả đủ 84 ngày, ngày không học `minutes: 0`.
- [ ] Leaderboard: user chưa opt-in không xuất hiện; response không có email/userId; `week` sai format → 400.
- [ ] `PATCH /api/gamification/profile` đổi nickname/opt-in có hiệu lực ngay.
- [ ] Tab Thành tích ở `/ca-nhan` không còn badge hard-code, render từ API.

---

## 5. F-043 — Review queue thống nhất

### 5.1. Thiết kế tổng thể

1 endpoint đọc duy nhất **`GET /api/review/queue`** gộp 3 nguồn:
- **vocab**: từ đến hạn SM-2 — reuse logic `GET /api/progress/due` hiện có (F-026).
- **kanji**: từ đến hạn SM-2 — collection mới **`kanji_progress`** (mirror `word_progress`).
- **wrong**: câu trả lời sai — collection mới **`wrong_answers`**, ghi lúc session/AI-grading
  xong, resolve khi user trả lời đúng trong queue.

Thứ tự trộn (binding): **wrong trước** (sửa lỗi nóng), rồi vocab/kanji đến hạn xen kẽ
theo `overdueDays` giảm dần (quá hạn lâu ôn trước). FE render 1 phiên duy nhất.

### 5.2. Collection mới `kanji_progress` (SM-2 cho kanji)

Mirror `word_progress` (mục đã đọc từ code), thay `wordId` bằng `character`:

| Field | Type | Ghi chú |
|---|---|---|
| `_id` | ObjectId | |
| `userId` | ObjectId | index |
| `character` | String | 1 ký tự kanji, vd. `"日"` |
| `status` | String | `NEW` / `LEARNING` / `MASTERED` (reuse enum `ProgressStatus`) |
| `easeFactor` | Number | SM-2, default 2.5, min 1.3 |
| `intervalDays` | Number | default 0 |
| `repetitions` | Number | số lần đúng liên tiếp |
| `lapses` | Number | số lần quên |
| `nextReviewAt` | Date | index |
| `lastReviewedAt` | Date | |
| `correctCount` / `wrongCount` | Number | |
| Unique index: `(userId, character)` | | |

- Dùng chung `Sm2Calculator` hiện có (không viết lại công thức).
- **Ghi progress kanji khi nào:** sau mỗi câu trả lời trong KanjiFlashcard/KanjiQuiz,
  FE gọi `PATCH /api/progress/answer` mở rộng (mục 5.4) với `kind: "kanji"`.
  (Hiện tại kanji quiz "v1: không lưu SM-2" — F-043 thay đổi quyết định này.)

### 5.3. Collection mới `wrong_answers`

| Field | Type | Ghi chú |
|---|---|---|
| `_id` | ObjectId | |
| `userId` | ObjectId | index |
| `kind` | String | `"vocab"` (quiz/flashcard sai) hoặc `"ai_sentence"` (Sensei chấm < 60 điểm) |
| `wordId` | String | khi `kind="vocab"` — id từ vựng (để FE render lại thẻ/câu hỏi) |
| `aiGradeId` | ObjectId | khi `kind="ai_sentence"` — ref sang `ai_grades` (FE hiện lại câu gốc + câu sửa) |
| `sessionId` | ObjectId | session gây ra lỗi (trace, tùy chọn) |
| `resolved` | Boolean | default `false` |
| `createdAt` | Date | index |
| Index: `(userId, resolved, createdAt)` | | query queue |

**Ghi khi nào (backend hook, không endpoint riêng):**
1. `SessionService.recordSession()`: với mỗi `SessionItem {correct: false}` có `wordId`
   là id từ vựng thật (bỏ qua item `wordId` dạng `"shadow:..."`) → insert
   `wrong_answers {kind: "vocab", wordId, sessionId}`. **[Giả định]** dedupe: nếu đã có
   bản ghi `resolved=false` cho cùng `(userId, kind, wordId)` thì không insert trùng.
2. `AiService.gradeSentence()` (F-039): nếu `score < 60` → insert
   `wrong_answers {kind: "ai_sentence", aiGradeId}` (không dedupe — mỗi lần chấm là
   1 câu khác nhau).

**Retention:** `resolved=true` quá 30 ngày → job dọn (hoặc xóa lazy khi query);
`resolved=false` giữ tối đa **100** bản ghi/user (xóa cũ nhất khi vượt — chống phình).

### 5.4. Mở rộng `PATCH /api/progress/answer` (không endpoint mới)

Request hiện có `{wordId, correct, timeMs}` được mở rộng thêm field tùy chọn:

| Field | Type | Bắt buộc | Rule |
|---|---|---|---|
| `kind` | string | không | `"vocab"` (mặc định) hoặc `"kanji"` |
| `wordId` | string | có khi `kind="vocab"` | id từ vựng (như hiện tại) |
| `character` | string | có khi `kind="kanji"` | đúng 1 ký tự kanji |

- `kind="kanji"` → đọc/ghi `kanji_progress` (upsert theo `(userId, character)`),
  áp `Sm2Calculator`, trả cùng shape `AnswerResponse` hiện có
  `{status, nextReviewAt, easeFactor, intervalDays, repetitions}`.
- `kind` sai giá trị, hoặc thiếu `wordId`/`character` tương ứng → `400 VALIDATION_ERROR`.
- Response của answer khi `correct=true` và item đến từ queue wrong → **tự động**
  resolve `wrong_answers` phù hợp? KHÔNG — resolve là hành động riêng (mục 5.6),
  vì 1 câu trả lời đúng trong quiz bài học chưa chắc user đã "sửa được lỗi cũ".
  **[Giả định, binding]**

### 5.5. Contract `GET /api/review/queue` — cần JWT

Query: `?limit=` (tùy chọn, 1–100, mặc định 50 — tổng cả 3 loại).

Response `200` — field names CHÍNH XÁC:

```json
{
  "items": [
    { "kind": "wrong", "id": "671f...", "refKind": "vocab",
      "wordId": "w123",
      "kana": "たべる", "kanji": "食べる", "romaji": "taberu",
      "meaningVi": "ăn", "audioUrl": "/audio/...", "createdAt": "2026-10-22T..." },
    { "kind": "wrong", "id": "671f...", "refKind": "ai_sentence",
      "sentence": "毎日日本語を勉強します。",
      "corrected": "毎日日本語を勉強しています。",
      "errors": [{ "span": "勉強します", "issue": "...", "fix": "..." }] },
    { "kind": "vocab", "wordId": "w456",
      "kana": "のむ", "kanji": "飲む", "meaningVi": "uống",
      "audioUrl": "/audio/...", "overdueDays": 3 },
    { "kind": "kanji", "character": "日",
      "meaningVi": "mặt trời, ngày", "onYomi": ["ニチ", "ジツ"], "kunYomi": ["ひ", "か"],
      "overdueDays": 1 }
  ],
  "counts": { "vocab": 18, "kanji": 7, "wrong": 5, "total": 30 },
  "estimatedMinutes": 6
}
```

Rules:
- `items`: tối đa `limit` items, thứ tự = wrong (mới nhất trước) → vocab/kanji
  (overdueDays giảm dần). `counts.total` là tổng **chưa cắt limit**.
- `kind="wrong"`: `id` = `_id` của `wrong_answers`; `refKind` phân biệt payload.
  Chỉ lấy `resolved=false`.
- `kind="vocab"`: các field hiển thị copy từ `DueWordDto` hiện có (không cần đủ 15 field —
  tối thiểu: `wordId, kana, kanji, romaji, meaningVi, audioUrl, overdueDays`).
- `kind="kanji"`: join collection `kanji` để lấy `meaningVi/onYomi/kunYomi`.
- `estimatedMinutes = ceil((wrong*15 + vocab*8 + kanji*8) / 60)` giây/item theo counts
  **chưa cắt limit** (8s/từ như ReviewDue hiện tại; câu sai 15s vì phải làm lại).
- Queue rỗng → `items: []`, counts 0, FE hiện empty-state "復" như hiện tại.

### 5.6. Resolve câu sai — `PATCH /api/review/wrong/{id}` — cần JWT

Khi user trả lời **đúng** 1 item `kind="wrong"` trong phiên ôn tập:

Request: `{ "resolved": true }` (chỉ nhận `true`; `false` → 400).
Response `200`: `{ "id": "671f...", "resolved": true }`.

- `{id}` không tồn tại hoặc không thuộc user → `404 NOT_FOUND`.
- Đã `resolved=true` → idempotent, trả 200 như thường.

### 5.7. FE rework tab Ôn tập (`ReviewDue.jsx` → `/on-tap`)

- Mount: `GET /api/review/queue` → header hiện `counts` + `estimatedMinutes` + streak
  (giữ UI "số từ đến hạn + thời gian ước tính + streak" của Phase A).
- Render item theo `kind`:
  - `vocab`/`kanji`: flashcard 3D hiện có (kanji: mặt trước hiện chữ kanji, mặt sau
    nghĩa + âm đọc). Trả lời → `PATCH /api/progress/answer` với `kind` tương ứng.
  - `wrong` + `refKind="vocab"`: flashcard như vocab; trả lời đúng →
    `PATCH /api/review/wrong/{id} {resolved: true}` (sau khi answer progress xong).
  - `wrong` + `refKind="ai_sentence"`: hiện câu gốc + câu sửa của Sensei + nút
    "Tôi đã hiểu, đánh dấu xong" → resolve; hoặc cho viết lại câu → so sánh đơn giản
    với `corrected` (so khớp chuỗi sau normalize — **[giả định]** FE tự làm, không AI).
- Hết queue → `POST /api/sessions` với `mode=REVIEW_DUE`, `scopeType=REVIEW`,
  `scopeId="due"` như hiện tại (giữ nguyên để streak/badge/heatmap không vỡ) +
  confetti như hiện tại.
- Route `/on-tap` giữ nguyên; component đổi tên nội bộ tùy implementer.

### 5.8. Persistence contract — F-043

| Dữ liệu | Sống ở đâu |
|---|---|
| Từ đến hạn | **Server DB** — `word_progress` hiện có (F-026, không đổi) |
| Kanji đến hạn | **Server DB** — collection mới `kanji_progress` (mục 5.2), SM-2 chung `Sm2Calculator` |
| Câu sai | **Server DB** — collection mới `wrong_answers` (mục 5.3); ghi bởi hook session/AI-grading, resolve qua endpoint riêng |
| Queue | **Không persist** — aggregate lúc request từ 3 nguồn trên |

### 5.9. Acceptance criteria — F-043

- [ ] `GET /api/review/queue` trộn đúng thứ tự wrong → vocab/kanji theo overdueDays; counts và estimatedMinutes đúng công thức.
- [ ] Trả lời sai 1 từ trong quiz → `wrong_answers {kind:"vocab", resolved:false}` xuất hiện; lần sai thứ 2 cùng từ không tạo trùng.
- [ ] Sensei chấm câu < 60 điểm → `wrong_answers {kind:"ai_sentence"}` xuất hiện trong queue.
- [ ] Trả lời đúng item wrong → `PATCH /api/review/wrong/{id}` resolve; lần sau queue không còn item đó.
- [ ] `PATCH /api/progress/answer {kind:"kanji", character:"日", correct:true}` → ghi `kanji_progress`, SM-2 cập nhật `nextReviewAt`.
- [ ] `kind` sai / thiếu `character` → 400; resolve id của user khác → 404.
- [ ] Hết queue → session `REVIEW_DUE` ghi thành công, streak/badge tính như cũ.
- [ ] Quá 100 wrong chưa resolve/user → xóa cũ nhất, không phình DB.

---

## 6. ENV vars

| ENV | Bắt buộc? | Default | Mô tả |
|---|---|---|---|
| *(không có ENV mới)* | — | — | Phase C không cần ENV mới: shadowing là static JSON, XP/heatmap/badge hard-code trong spec, leaderboard limit 50 hard-code |

**Đặt ở đâu (nhắc lại từ DOC-11):** Production → Render Dashboard → Environment;
local → `backend/.env` (gitignored). Không commit secret vào git.

---

## 7. Error contract (bổ sung cho Phase C)

| HTTP | `code` | `message` (tiếng Việt) | Khi nào |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | vd. "week phải có dạng YYYY-Www." / "kind chỉ nhận vocab hoặc kanji." / "character phải là 1 ký tự kanji." | `week` sai format, `kind` sai, thiếu `wordId`/`character`, `resolved` ≠ true, nickname quá 20 ký tự |
| 404 | `NOT_FOUND` | "Không tìm thấy mục ôn tập." | resolve `wrong_answers` id không tồn tại / không thuộc user |
| 401 | (filter JWT hiện có) | — | mọi endpoint Phase C đều cần đăng nhập |

Shape tuân thủ `{code, message}` hiện có; không lộ chi tiết DB/aggregation ra client.

---

## 8. Security & privacy

1. **Leaderboard ẩn danh (binding):** response không bao giờ chứa email, tên thật,
   userId, avatar. Chỉ `nickname` tự đặt + `isMe`. Mặc định opt-out.
2. Nickname 1–20 ký tự; copy UI nhắc "đừng dùng tên thật" (copy-ux-linter duyệt câu chữ).
3. **Bản ghi âm shadowing không rời khỏi máy user** — không upload, không log, không
   analytics. Nút download là client-side blob.
4. `userId` lấy từ `@AuthenticationPrincipal` — không tin field user trong request.
5. `wrong_answers.sentence`/`aiGradeId` là dữ liệu user — chỉ trả cho chính user đó.
6. Aggregation leaderboard/heatmap giới hạn 50 entries + 52 tuần — chống query nặng.

---

## 9. FE integration (tổng hợp)

- Module mới **`frontend/src/api/gamification.js`**: `fetchBadges()`, `fetchHeatmap(weeks)`,
  `fetchLeaderboard(week?)`, `updateGamificationProfile({nickname?, leaderboardOptIn?})`.
- Module **`frontend/src/api/study.js`** (mở rộng, không file mới):
  - `fetchReviewQueue(limit?)` → `GET /api/review/queue`
  - `answerProgress({kind?, wordId?, character?, correct, timeMs})` — thêm `kind`/`character`
  - `resolveWrong(id)` → `PATCH /api/review/wrong/{id}`
  - `recordSession` — đọc thêm `newBadges` từ response để hiện toast huy hiệu.
- **Lesson Hub:** tab "Shadowing" (điều kiện hiện — mục 3.6); component
  `ShadowingPlayer.jsx` mới (video + subtitle list + recorder).
- **`/on-tap`:** rework `ReviewDue.jsx` theo mục 5.7 (giữ route, giữ confetti, giữ
  empty-state 復).
- **`/ca-nhan` tab Thành tích:** xóa badge hard-code (dòng 92–98 `CaNhan.jsx`),
  render từ `GET /api/gamification/badges` + heatmap (CSS grid 12×7) + leaderboard
  (bảng top 50 + toggle opt-in + input nickname).
- Không thêm dependency npm mới (MediaRecorder/CSS/SVG có sẵn).

---

## 10. Ma trận truy vết FR → Function → Entity

| FR | Function | Entities |
|----|----------|----------|
| FR-041 | F-041 | `study_sessions` (write, mode `SHADOWING` mới); nội dung = static JSON (không entity) |
| FR-042 | F-042 | `user_badges` (mới, write), `study_sessions` (read), `users` (+2 field mới), `word_progress` (read), `ai_usage` (read) |
| FR-043 | F-043 | `word_progress` (read/write), `kanji_progress` (mới), `wrong_answers` (mới), `ai_grades` (read, ref), `study_sessions` (write hook) |

Entity mới so với `docs/02-ERD.md`: `user_badges`, `kanji_progress`, `wrong_answers`;
`users` thêm `leaderboardOptIn`, `nickname`; `SessionMode` thêm `SHADOWING`.

---

## 11. Việc đồng bộ docs còn lại (cho orchestrator)

Spec này là DOC-12 độc lập. Sau khi duyệt, cần cập nhật để không lệch doc/code:
1. `docs/01-SRE.md`: thêm FR-041/042/043 vào danh sách FR.
2. `docs/03-functions.md`: thêm F-041/042/043 + nối vào ma trận truy vết (lưu ý file này
   hiện mới tới F-037 — F-038/039/040 của DOC-11 cũng chưa được thêm).
3. `docs/02-ERD.md`: thêm entity `user_badges`, `kanji_progress`, `wrong_answers`;
   thêm field `users.leaderboardOptIn`, `users.nickname`; enum `SessionMode.SHADOWING`.
4. `docs/04-user-stories.md`: thêm US cho 3 function (gợi ý: "là learner học bài 1,
   tôi muốn luyện nói theo video hội thoại có phụ đề để phát âm tự nhiên hơn";
   "là learner có streak 6 ngày, tôi muốn thấy mình trên bảng xếp hạng tuần để có
   động lực giữ chuỗi"; "là learner vừa làm sai 5 từ trong quiz, tôi muốn chúng xuất
   hiện trong Ôn tập ngày mai để không quên").

---

## 12. Acceptance criteria tổng (reviewer check)

**F-041 Shadowing**
- [ ] Tab Shadowing chỉ hiện khi bài có video; ẩn hoàn toàn khi chưa có.
- [ ] Bấm phụ đề nhảy đúng thời điểm; loop câu/đoạn; đổi tốc độ phát.
- [ ] Ghi âm + phát lại so sánh được; từ chối mic không vỡ các tính năng khác.
- [ ] Hoàn thành phiên (≥80%) → session `SHADOWING` ghi thành công, streak tăng.
- [ ] 0 endpoint backend mới cho nội dung shadowing.

**F-042 Gamification**
- [ ] `newBadges` trả về đúng khi đạt điều kiện; không trùng bản ghi khi race.
- [ ] Badges API trả đủ 12 mục, trạng thái đúng; tab Thành tích không còn hard-code.
- [ ] Heatmap đủ 84 ô, ngày trống = 0.
- [ ] Leaderboard ẩn danh tuyệt đối (không email/userId); opt-in/out có hiệu lực ngay; week sai → 400.

**F-043 Review queue**
- [ ] Queue trộn đúng thứ tự + counts + estimatedMinutes đúng công thức.
- [ ] Câu sai từ quiz và Sensei (<60đ) chảy vào queue; dedupe đúng rule.
- [ ] Kanji có SM-2 riêng qua `PATCH /api/progress/answer {kind:"kanji"}`.
- [ ] Resolve wrong idempotent; id user khác → 404.
- [ ] Hết queue → session REVIEW_DUE + streak + badge như cũ, không regression.

**Chung**
- [ ] Không dependency Maven/npm mới.
- [ ] Không emoji làm icon trong code mới.
- [ ] Mọi field FE đọc/gửi khớp DTO backend từng ký tự (chạy `api-contract-guard` sau implement).
- [ ] Bug phát hiện → log đầy đủ vào `~/workspace/bug-log/`.
