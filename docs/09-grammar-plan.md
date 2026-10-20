# Kế hoạch thêm tính năng Ngữ pháp — Master JP

**Ngày:** 2026-10-17
**Phạm vi:** 50 bài Minna no Nihongo (N5: bài 1–25, N4: bài 26–50)
**Nguyên tắc:** dữ liệu trước (theo yêu cầu user), UI sau, luyện tập cuối.

---

## 1. Phân tích codebase hiện tại

### 1.1. Kiến trúc dữ liệu
| Entity | Collection | Seed file | Ghi chú |
|---|---|---|---|
| Lesson | `lessons` | `seed-lessons-*.json` | 50 bài, `titleVi` không prefix |
| VocabWord | `vocab_words` | nhiều file | 2692 từ, field `cluster` |
| Kanji | `kanji` | `kanji-n5/n4.json` | 988 chữ, field `topic` |

- Seed scripts (`scripts/seed-*.py`): idempotent (upsert theo key ổn định), **fail-fast khi thiếu `MONGODB_DATABASE`** (bài học 2026-10-15).
- Convention field (bắt buộc theo đúng): `lessonId` (String hex, KHÔNG ObjectId), `lessonNo` denormalized, `active: true` (mọi query backend đều lọc active), `searchNorm` (tiếng Việt bỏ dấu để search), `orderInLesson`.
- **Tên index PHẢI khớp** `@CompoundIndex` trong backend — sai tên mà trùng keys → Atlas báo `IndexOptionsConflict` (code 85).
- Entity mới `Grammar` theo đúng pattern này: collection `grammar`, seed `data/seed/grammar-*.json` (4 file theo khoảng bài), script `scripts/seed-grammar.py` (copy pattern `seed-kanji.py`).

> Lưu ý scope: `docs/01-SRE.md` ghi ngữ pháp từng bị loại khỏi scope ("chỉ cần từ vựng đầy đủ") — đây là yêu cầu mở rộng scope mới của user (2026-10-17).

### 1.2. Backend patterns (Spring Boot + MongoDB)
- Domain: `@Document`, field đơn giản, getter/setter.
- Controller: `@GetMapping` với `@RequestParam` filter (theo mẫu `KanjiController`: `?lessonNo=`, `?topic=`).
- DTO: record/class lồng trong `*Dtos` (theo mẫu `KanjiDtos`).
- Service: `MongoTemplate` cho query linh hoạt.
- Search: `GET /api/words/search?q=` → `WordService.search(q)` — grammar làm tương tự.
- Security: `GET /api/kanji/**` permitAll — grammar read cũng public; admin CRUD cần auth.

### 1.3. Frontend patterns (React 19 + Vite)
- API layer: `frontend/src/api/*.js` (axios client).
- Trang bài học (`LessonDetail.jsx`): tab section, toggle chế độ xem, selection bar sticky, practice bar.
- Từ điển (`Dictionary.jsx`): search qua API.
- Practice pages: nhận `?quick=1` + sessionStorage cho tập chọn tay.

### 1.4. Integration points cho Grammar
1. **Collection** `grammar` — 1 document / 1 điểm ngữ pháp.
2. **API mới** (copy pattern `KanjiController`/`KanjiDtos`/`KanjiService`):
   - `GET /api/grammar?lessonNo=&level=` — danh sách theo bài (public)
   - `GET /api/grammar/{id}` — chi tiết (public)
   - `GET /api/grammar/search?q=` — tìm kiếm (theo mẫu `WordController`)
   - `SecurityConfig`: thêm 1 dòng permitAll GET `/api/grammar/**`
   - Admin CRUD (theo mẫu `AdminController`)
3. **Frontend** — 2 phương án hiển thị:
   - **(a)** Tab thứ 4 trong `LessonDetail` ("Ngữ pháp") — ít code nhất.
   - **(b)** Trang riêng `/ngu-phap` (như trang `/kanji`) — lọc theo bài/cấp độ + tìm kiếm.
   - `frontend/src/api/grammar.js` (copy `api/kanji.js`).
   - Có thể thêm `totalGrammar` vào `LessonDto` (như `totalWords`) để hiện số lượng trên card bài học.
4. **Không cần v1**: phân trang, progress/SM-2 (v1 read-only như kanji v1).

---

## 2. Nghiên cứu: logic ngữ pháp tối ưu cho người học

### 2.1. Cấu trúc ngữ pháp Minna no Nihongo
- Mỗi bài có **3–5 điểm ngữ pháp**, mỗi điểm gồm: **mẫu câu (pattern)** → **ý nghĩa** → **cách dùng** → **ví dụ**.
- N5 (bài 1–25): trợ từ cơ bản, thể ます/て/ない/た, so sánh, mong muốn.
- N4 (bài 26–50): thể khả năng, bị động, sai khiến, điều kiện, kính ngữ.
- Đặc thù: nhiều điểm ngữ pháp **dễ nhầm lẫn** với nhau (ておきます vs てあります, はず vs べき, たら vs ば) → cần **đối chiếu (contrast)**.

### 2.2. Phương pháp học tối ưu (tổng hợp từ SLA + Bunpro + thực tế)
1. **Học theo ngữ cảnh, không học vẹt công thức**: mỗi điểm ngữ pháp cần 2–3 câu ví dụ dùng từ vựng **của chính bài đó** (người học đã biết từ → chỉ tập trung vào ngữ pháp).
2. **Hiểu qua đối chiếu**: với điểm dễ nhầm, hiển thị bảng so sánh trực tiếp.
3. **Nhận diện trước, vận dụng sau**: 
   - Bước 1 (nhận diện): đọc câu → chọn mẫu ngữ pháp đúng (quiz trắc nghiệm).
   - Bước 2 (vận dụng): điền từ vào chỗ trống — phase sau.
4. **Ôn ngắt quãng**: điểm ngữ pháp nào hay sai → đưa vào review như từ vựng.
5. **Tra cứu nhanh**: search theo từ khóa tiếng Việt ("nhờ vả" → てください) hoặc pattern ("〜たい").

### 2.3. Schema dữ liệu (theo convention codebase, hỗ trợ cả 3 phase)
```json
{
  "_id": "g14-01",
  "lessonId": "<hex string>",
  "lessonNo": 14,
  "level": "N5",
  "orderInLesson": 1,
  "title": "V-てください",
  "titleVi": "Hãy làm ~ (nhờ vả, yêu cầu)",
  "pattern": "V-て + ください",
  "meaningVi": "Dùng khi nhờ vả hoặc yêu cầu ai đó làm gì một cách lịch sự.",
  "explanation": "Giải thích chi tiết bằng tiếng Việt...",
  "formation": "Cách chia động từ sang thể て + ください...",
  "examples": [
    {"ja": "ちょっと待ってください。", "kana": "ちょっとまってください。", "vi": "Xin hãy đợi một chút."}
  ],
  "notes": "Lỗi thường gặp / điểm cần lưu ý...",
  "compareWith": ["g15-01"],
  "tags": ["yeu-cau", "te-form"],
  "active": true,
  "searchNorm": "v-tekudasai hay lam nho va yeu cau"
}
```

---

## 3. Kế hoạch triển khai theo phase

### Phase 1 — Import dữ liệu (làm trước, theo yêu cầu)
- **Nội dung**: ~50 bài × 3–4 điểm = **150–200 điểm ngữ pháp**, mỗi điểm đủ các field trong schema trên.
- **Cách làm**: AI soạn thảo toàn bộ (dựa trên cấu trúc chuẩn Minna no Nihongo), user review spot-check theo bài.
- **Chất lượng**: ví dụ dùng từ vựng trong bài đó; giải thích tiếng Việt chính xác, ngắn gọn; đánh dấu điểm dễ nhầm để làm `compareWith`.
- **Kỹ thuật**: `data/seed/grammar-*.json` (4 file), `scripts/seed-grammar.py` (idempotent, fail-fast DB), backend domain + API read-only.
- **Output**: DB có đủ dữ liệu ngữ pháp 50 bài, API đọc được.

### Phase 2 — UI xem ngữ pháp
- Tab "Ngữ pháp" trong trang chi tiết bài học: danh sách điểm ngữ pháp → bấm vào xem chi tiết (pattern, giải thích, ví dụ, lưu ý, so sánh).
- Trang `/ngu-phap`: duyệt 50 bài (accordion) + ô tìm kiếm (theo nghĩa Việt hoặc pattern).
- Component dùng chung `GrammarCard`.

### Phase 3 — Luyện tập ngữ pháp
- Quiz nhận diện: cho câu có chỗ trống → chọn mẫu ngữ pháp đúng (4 lựa chọn).
- Tích hợp vào thanh luyện tập: khi đã chọn bài, thêm nút "Luyện ngữ pháp".
- Ôn tập: điểm nào làm sai nhiều → đưa vào review.

---

## 4. Rủi ro & giảm thiểu
- **Khối lượng nội dung lớn** (~200 điểm): chia nhỏ theo 10 bài/lượt, user duyệt từng lượt.
- **Độ chính xác giải thích**: mọi ví dụ dùng từ đã có trong bài; tránh ví dụ chứa ngữ pháp bài sau.
- **Backend chưa compile ở đây**: ghi rõ CHƯA COMPILE như mọi lần.

---

## 5. Bước tiếp theo (chờ user duyệt)
1. Duyệt schema + phase plan này.
2. Soạn data 10 bài đầu (N5: bài 1–10) → user review → điều chỉnh → làm tiếp 40 bài còn lại.
3. Import + API + UI theo phase.
