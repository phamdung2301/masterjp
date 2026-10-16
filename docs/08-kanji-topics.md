# Kanji theo chủ đề (topics)

**Ngày:** 2026-10-16
**Trạng thái:** đã build (branch `feature/kanji-topics`) — ⚠️ code Java viết trong
môi trường không-JDK, **CHƯA COMPILE**. Lần build đầu (Render/Docker) có thể lộ
lỗi import/package — đọc log build thay vì hoảng.

## Taxonomy (17 chủ đề, 988 chữ)

Phân loại thủ công toàn bộ 988 chữ kanji N5/N4 theo **nghĩa của chữ**
(`work/mapping.tsv`: character, meaningVi, jlpt, topic, why — đã spot-check).
Không tái dùng 29 cụm từ vựng (cụm đó nhóm *từ*, không nhóm *chữ*).

| Code | Label | Số chữ |
|---|---|---|
| sinh_hoat | Sinh hoạt | 166 |
| truu_tuong | Trừu tượng | 116 |
| tinh_chat | Tính chất | 102 |
| con_nguoi_co_the | Con người & cơ thể | 75 |
| gia_dinh_xa_hoi | Gia đình & xã hội | 72 |
| noi_chon_huong | Nơi chốn & phương hướng | 69 |
| giao_tiep | Giao tiếp | 67 |
| hoc_tap_cong_viec | Học tập & công việc | 63 |
| thoi_gian | Thời gian | 53 |
| thien_nhien | Thiên nhiên | 44 |
| an_uong | Ăn uống | 33 |
| kinh_te | Kinh tế | 30 |
| so_dem | Số đếm | 28 |
| dong_thuc_vat | Động & thực vật | 26 |
| cam_xuc | Cảm xúc | 26 |
| mau_sac | Màu sắc | 11 |
| khac | Khác | 7 |

Single source of truth cho label: `KanjiService.TOPIC_LABELS` (backend).
Data mapping: `data/seed/kanji-topics.json` (`{topics, mapping}`).

## Data model

- `Kanji.topic` (String, vd. `"thoi_gian"`), index `idx_kanji_topic`.
- `scripts/seed-kanji.py` đọc `kanji-topics.json`, ghi field `topic` (idempotent —
  chạy lại chỉ update field; **fail-fast khi thiếu `MONGODB_DATABASE`**).
- User chạy lại 1 lần sau khi merge để có field mới:
  `$env:MONGODB_DATABASE="masterjp"; python scripts/seed-kanji.py`

## API (đều public read — `GET /api/kanji/**` đã permitAll)

- `GET /api/kanji?lessonNo=&jlpt=&topic=` — `topic` nhận nhiều code cách nhau
  dấu phẩy (vd. `?topic=thoi_gian,so_dem`); code lạ bị bỏ qua. Response
  `KanjiDto` thêm field `topic`. Luôn sắp theo `strokes` tăng dần.
- `GET /api/kanji/topics` → `[{code, label, count}]` (count từ DB thực tế;
  topic 0 chữ vẫn trả về).
- `GET /api/kanji/{character}` — `KanjiDetailDto` thêm field `topic`.
- `POST /api/kanji/quiz` — không đổi (cần auth như cũ).

## Frontend (`/kanji` viết lại theo mô hình trang bài học)

- Chọn nhiều bài (grid checkbox, giữ nguyên) + **chọn chủ đề** (grid checkbox
  17 chủ đề, label + count từ `GET /api/kanji/topics`).
- Lưới kanji: tick từng chữ (giữ nguyên).
- **Danh sách từ vựng** của các bài đã chọn: nhóm theo cụm chủ đề (tái dùng
  logic `LessonDetail.jsx`), tick từng từ / từng cụm.
- Thanh luyện tập sticky thống nhất:
  - "Đã chọn N chữ" → 🃏 Flashcard kanji | ✏️ Quiz kanji (2 mode) |
    ⌨️ Gõ cách đọc (qua `POST /api/kanji/quiz` + `/luyen-tap/kanji-quiz`).
  - "Đã chọn M từ" → 🃏 Flashcard | ✏️ Quiz (dropdown mode) |
    ⌨️ Gõ phiên âm | ＋ Thêm vào bộ thẻ (pattern `?quick=1` + sessionStorage).
  - Ẩn nhóm nào khi chưa chọn gì ở nhóm đó.
- Trang chi tiết `/kanji/:char` giữ nguyên.
