# Feature Pack 2 — Backend API Contract (Phase 1)

**Ngày:** 2026-10-15 · Backend: Spring Boot 4.1.1 + MongoDB
Tài liệu này là contract chính xác cho team frontend. Mọi field JSON đều case-sensitive —
copy đúng tên field như ghi dưới đây.

---

## F-A. Cụm chủ đề (topic cluster)

### Thay đổi DTO hiện có (additive — field cũ giữ nguyên)

`GET /api/lessons/{id}/words` trả về `WordDto` có thêm field **`cluster`** ở cuối:

```json
{
  "id": "...", "lessonId": "...", "section": "VOCAB",
  "kana": "つくえ", "kanji": "机", "romaji": "tsukue",
  "meaningVi": "cái bàn", "pos": "danh từ",
  "exampleJa": "...", "exampleVi": "...",
  "audioUrl": "/audio/b01-003.mp3", "orderInLesson": 3,
  "studyStatus": "NEW",
  "cluster": "do-dung"
}
```

> ⚠️ Dữ liệu cũ (chưa seed lại) có `cluster: null`. FE coi `null` như cụm `"khac"` ("Khác").
> Sau khi chạy seed Phase 2, mọi từ đều có cluster.

### `GET /api/lessons/{id}/words` — thêm param `cluster` (optional)

- Query: `?section=VOCAB&cluster=do-dung` (cả 2 optional, kết hợp được với nhau).
- `cluster` không phân biệt hoa/thường, trim tự động.
- Public, không cần đăng nhập.
- Lỗi: `404 LESSON_NOT_FOUND` (bài không tồn tại), `400 VALIDATION_ERROR` (section sai).

### `GET /api/lessons/{id}/clusters` — MỚI

- Query optional: `?section=VOCAB` (VOCAB | REFERENCE | PHRASE). Không param → toàn bài.
- Public, không cần đăng nhập.
- Response — mảng `{cluster, label, count}`, sắp theo `count` giảm dần, rồi `cluster` tăng dần:

```json
[
  { "cluster": "do-dung", "label": "Đồ dùng", "count": 12 },
  { "cluster": "gia-dinh", "label": "Gia đình", "count": 8 },
  { "cluster": "khac", "label": "Khác", "count": 2 }
]
```

- Label lấy từ bảng `ClusterLabels` trong backend (28 cụm + `khac` → "Khác"; khớp 100% với `data/seed/clusters.json`).
  Mã lạ (không có trong bảng) → label = chính mã đó. Từ chưa có cluster → gom vào `"khac"`.
- Lỗi: `404 LESSON_NOT_FOUND`, `400 VALIDATION_ERROR` (section sai).

### Admin — sửa cụm từng từ

- `PATCH /api/admin/words/{id}` (cần ROLE_ADMIN) nhận thêm field **`cluster`**
  (vd. `{"cluster": "do-dung"}`). Backend chuẩn hoá: trim + lowercase; ghi audit log.
- `GET /api/admin/words` (AdminWordDto) có thêm field **`cluster`** ở cuối.

---

## F-B. Kanji

### `GET /api/kanji` — danh sách chữ

- Query optional: `?lessonNo=1&jlpt=N5` (cả 2 optional, kết hợp được).
  Không param → **tất cả** chữ. Luôn sắp theo `strokes` tăng dần.
- Public, không cần đăng nhập. `jlpt` không phân biệt hoa/thường.

```json
[
  {
    "character": "私",
    "onYomi": ["シ"],
    "kunYomi": ["わたし", "わたくし"],
    "meaningVi": "tôi; riêng tư",
    "strokes": 7,
    "jlpt": "N5",
    "lessonNos": [1, 5]
  }
]
```

### `GET /api/kanji/{character}` — chi tiết chữ

- `character` URL-encoded, vd. `/api/kanji/%E7%A7%81`.
- Public, không cần đăng nhập.
- Response = KanjiDto + **`words`**: tối đa **50 từ** chứa chữ đó,
  sắp theo `lessonNo` rồi `orderInLesson`. `studyStatus` luôn `null` ở endpoint này.

```json
{
  "character": "私", "onYomi": ["シ"], "kunYomi": ["わたし"],
  "meaningVi": "tôi; riêng tư", "strokes": 7, "jlpt": "N5", "lessonNos": [1, 5],
  "words": [
    { "id": "...", "kana": "わたし", "kanji": "私", "...": "...", "cluster": "gia-dinh", "studyStatus": null }
  ]
}
```

- Lỗi: `404 KANJI_NOT_FOUND` (chữ chưa có trong bộ data kanji).

---

## F-C. Luyện tập nhanh — `POST /api/quiz/quick`

- **Auth: bắt buộc đăng nhập** (401 nếu chưa login), như `POST /api/quiz/generate`.
- Request:

```json
{ "wordIds": ["<id1>", "<id2>", "..."], "mode": "JP_VI" }
```

- `mode`: `JP_VI` | `VI_JP` | `LISTENING` (TYPING để phase sau — endpoint vẫn hỗ trợ kỹ thuật
  nhưng FE F-C chỉ dùng 3 mode trên). Validate bằng EnumUtils như quiz thường.
- Validate:
  - `wordIds`: 1–100 phần tử (Bean Validation `@Size`) — vi phạm → `400` validation error.
  - ids không tồn tại hoặc `active=false` → bỏ qua.
  - Sau khi lọc còn 0 từ → `400 QUIZ_NOT_ENOUGH_WORDS`.
  - Mode thường (`JP_VI`/`VI_JP`) cần ≥ 4 từ; `LISTENING` lọc từ có `audioUrl` rồi cần ≥ 4 từ —
    thiếu → `400 QUIZ_NOT_ENOUGH_WORDS` (cần đáp án nhiễu).
  - Đáp án nhiễu: cùng level, ưu tiên cùng loại từ — logic **giống hệt** quiz thường
    (tái dùng `buildQuestions`).
- Response: **đúng shape `QuizResponse`** như quiz thường — FE tái dùng component Quiz nguyên vẹn:

```json
{
  "questions": [
    {
      "wordId": "...", "promptText": "机", "promptAudioUrl": "/audio/b01-003.mp3",
      "options": [ { "wordId": "...", "text": "cái bàn" }, ... ],
      "answerWordId": "...",
      "kana": null, "kanji": null, "meaningVi": null
    }
  ]
}
```

- **KHÔNG tạo CustomSet.** **KHÔNG tạo StudySession trong endpoint này.**
  Luồng ghi lịch sử giữ nguyên như quiz thường, FE làm sau khi user làm bài xong:
  1. Mỗi câu trả lời → `PATCH /api/progress/answer {wordId, correct}` (SM-2 cập nhật ngay).
  2. Kết thúc phiên → `POST /api/sessions` với `scopeType: "QUICK"`, `scopeId: "quick"`,
     `mode` = `QUIZ_JP_VI` | `QUIZ_VI_JP` | `LISTENING`, `items: [{wordId, correct, timeMs}]`
     → trả `{id, streakDays}`. (`QUICK` đã được thêm vào enum `ScopeType` của backend.)
- 3 nút sau kết quả (luyện lại từ sai / luyện lại toàn bộ / chọn lại từ):
  FE tự lọc `wordIds` từ kết quả và gọi lại `POST /api/quiz/quick` — không cần API mới.

---

## F-D. Quiz kanji — `POST /api/kanji/quiz` (Track 3, R1-R3 rebuild)

- **Auth: bắt buộc đăng nhập** (401 nếu chưa login), như `POST /api/quiz/quick`.
  `SecurityConfig` chỉ `permitAll` cho `GET /api/kanji/**` nên `POST` này rơi vào
  rule `/api/**` → `authenticated()` — không cần sửa SecurityConfig.
- Request:

```json
{ "characters": ["私", "人", "..."], "mode": "KANJI_MEANING" }
```

- `mode`: `KANJI_MEANING` | `MEANING_KANJI` | `KANJI_TYPING` — enum **riêng**
  `KanjiQuizMode` (không tái dùng `QuizMode` của quiz từ vựng để tránh vỡ
  switch exhaustive ở `QuizService`). Validate bằng `EnumUtils` như quiz thường.
- Validate:
  - `characters`: 1–100 phần tử (Bean Validation `@Size`) — vi phạm → `400`
    validation error. Null/rỗng/trùng được chuẩn hoá, chữ không có trong DB → bỏ qua.
  - Sau khi lọc còn 0 chữ → `400 QUIZ_NOT_ENOUGH_KANJI`.
  - Mode trắc nghiệm (`KANJI_MEANING`/`MEANING_KANJI`) cần ≥ 4 đáp án (1 đúng +
    3 nhiễu) — chữ chưa có nghĩa Việt thì bị loại khỏi lượt thay vì vỡ cả quiz.
  - Đáp án nhiễu: **ưu tiên chữ khác trong tập `characters` user chọn →
    fallback chữ cùng JLPT** (query `KanjiRepository.findByJlptOrderByStrokesAsc`) →
    **loại đáp án mơ hồ** (nghĩa Việt trùng nhau / nghĩa rỗng / trùng chữ).
    Thứ tự đáp án được shuffle. Thiếu nhiễu → `400 QUIZ_NOT_ENOUGH_KANJI`.
- `KANJI_TYPING`: hiện chữ + nghĩa → gõ cách đọc; không đáp án nhiễu.
  Response dùng các field của shape typing sẵn có: `options: []`,
  `kana` = chuỗi nối **mọi on/kun** (vd. `"シ, わたし, わたくし"`) để FE chấm local.
- Response: **đúng shape `QuizResponse`** như quiz thường — `wordId`/`answerWordId`
  = `character`, `promptAudioUrl: null` (kanji chưa có audio):

```json
{
  "questions": [
    {
      "wordId": "私", "promptText": "私", "promptAudioUrl": null,
      "options": [ { "wordId": "私", "text": "tôi" }, { "wordId": "人", "text": "người" }, ... ],
      "answerWordId": "私",
      "kana": null, "kanji": null, "meaningVi": null
    }
  ]
}
```

- **V1 KHÔNG lưu SM-2 / progress kanji** — endpoint chỉ sinh câu hỏi, không tạo
  StudySession, không cập nhật WordProgress. Kanji progress để phase 2.
  (Vì vậy luồng "ghi lịch sử như quiz thường" của F-C KHÔNG áp dụng cho kanji ở v1.)

---

## Ghi chú cho Phase 2 (seed scripts)

1. Index mới `idx_lesson_cluster` (`{'lessonId': 1, 'cluster': 1}`) trên `vocab_words`
   được Spring tự tạo lúc startup (`auto-index-creation: true`). `scripts/seed-import.py`
   khi sửa theo PR #7-style (dùng đúng tên index) cần biết tên này.
2. `kanji`: `id` = `character` (vd. `"_id": "私"`) → import idempotent bằng upsert theo `id`.
   Index `idx_kanji_lesson` (`lessonNos`), `idx_kanji_jlpt` (`jlpt`) — Spring tự tạo.
3. `clusters.json` gán `cluster` cho từng từ (slug lowercase, vd. `do-dung`); từ không xếp
   được → `"khac"`.
