# Feature Pack 2: Cụm chủ đề + Kanji + Luyện tập nhanh

**Ngày:** 2026-10-15
**Trạng thái:** Chờ user duyệt thiết kế

Ba tính năng mới theo yêu cầu của user (2026-10-15), sau khi web đã deploy
và chạy được.

---

## F-A. Cụm chủ đề (topic cluster) theo bài

### Vấn đề
Trong giáo trình Minna no Nihongo, từ vựng mỗi bài là **danh sách phẳng** —
không có sẵn khái niệm "cụm chủ đề". User muốn mỗi bài được chia thành các
cụm theo chủ đề (ví dụ: cụm "đồ dùng" gồm nhiều từ), chọn được từng cụm
hoặc cả bài để luyện.

### Thiết kế
- **"Tự động" = assistant gán cụm**, không phải user gán tay. Mình định nghĩa
  ~28 cụm chủ đề và gán cho toàn bộ 2.692 từ bằng script (rule theo
  nghĩa/loại từ + rà soát tay các từ mơ hồ). Từ nào không xếp được vào cụm
  nào → cụm `khac` ("Khác").
- **Data model:** thêm field `cluster` (string, vd. `do-dung`) vào
  `vocab_words` + index `(lessonId, cluster)`. Không tách collection riêng
  (cụm là thuộc tính của từ, không có metadata riêng cần quản lý).
- **Bảng cụm dự kiến** (mã → tên hiển thị):
  `gia-dinh` (Gia đình), `nghe-nghiep` (Nghề nghiệp), `do-dung` (Đồ dùng),
  `thoi-gian` (Thời gian), `dia-diem` (Địa điểm), `an-uong` (Ăn uống),
  `dong-vat` (Động vật), `thien-nhien` (Thiên nhiên & thời tiết),
  `co-the` (Cơ thể), `quan-ao` (Quần áo), `mau-sac` (Màu sắc),
  `so-dem` (Số đếm), `giao-thong` (Giao thông), `hoc-tap` (Học tập),
  `cong-viec` (Công việc), `mua-sam` (Mua sắm), `du-lich` (Du lịch),
  `suc-khoe` (Sức khỏe), `cam-xuc` (Cảm xúc & tính cách),
  `hanh-dong` (Hành động), `vi-tri` (Vị trí & hướng),
  `giao-tiep` (Giao tiếp), `gia-dinh`..., `khac` (Khác).
  (Danh sách cuối chốt khi chạy script gán — đảm bảo mỗi bài có 5–15 cụm.)
- **Backend:**
  - `VocabWord.cluster` + DTO.
  - `GET /api/lessons/{id}/clusters` → `[{ cluster, label, count }]` (kèm section filter).
  - `GET /api/lessons/{id}/words` thêm param `cluster` (optional).
  - Seed script: đọc thêm file `data/seed/clusters.json` (map cụm) khi import.
- **Frontend (trang chi tiết bài học):** thêm chế độ xem "Theo cụm": nhóm từ
  theo cụm, mỗi cụm hiện tên + số từ + nút chọn; giữ nguyên tab
  Từ vựng chính / Tham khảo / Cụm từ. Nút "Luyện cả bài" và "Luyện cụm đã chọn".

### Rủi ro
- Gán cụm bằng rule có thể sai vài % → chấp nhận được cho v1; admin có thể
  sửa cụm của từng từ trong trang Quản trị (mở rộng field edit sẵn có).

---

## F-B. Bộ data Kanji học riêng

### Vấn đề
Hiện tại kanji chỉ hiện kèm trong thẻ từ. User muốn **bộ data kanji đầy đủ
để học riêng** (như một phân môn độc lập).

### Thiết kế
- **Data:** trích tất cả chữ kanji xuất hiện trong `kanji` của 2.692 từ
  (dự kiến ~600–700 chữ cho N5–N4). Mỗi chữ curate:
  `character`, `onYomi[]`, `kunYomi[]`, `meaningVi`, `strokes` (số nét),
  `jlpt` (N5/N4), `lessonNos[]` (suy tự động từ các từ chứa chữ đó).
- **Collection mới:** `kanji` (không nhúng vào vocab_words vì 1 chữ dùng cho
  nhiều từ và có metadata riêng).
- **Backend:**
  - `KanjiController`: `GET /api/kanji?lessonNo=1` (kanji của bài),
    `GET /api/kanji/{character}` (chi tiết + danh sách từ chứa chữ đó).
  - Index: `lessonNos`, `jlpt`.
- **Frontend:**
  - Trang `/kanji`: lưới chữ kanji, lọc theo bài / cấp độ N5-N4, tìm theo chữ.
  - Trang `/kanji/:char`: chi tiết (âm on/kun, nghĩa, số nét) + các từ vựng
    chứa chữ đó (bấm vào nhảy sang từ).
  - **Flashcard kanji** (v1): mặt trước hiện chữ kanji, mặt sau hiện
    âm đọc + nghĩa — tái dùng luồng flashcard hiện có.
  - Quiz kanji để phase sau (không làm trong đợt này).
- **Seed:** script mới `scripts/seed-kanji.py` đọc `data/seed/kanji.json`
  (do assistant biên soạn) → import idempotent.

### Rủi ro
- Data kanji (âm đọc, số nét) do assistant biên soạn → cần rà soát kỹ;
  chấp nhận sai sót nhỏ v1, admin sửa được qua trang Quản trị.

---

## F-C. Luyện tập nhanh (Quick Practice)

### Luồng (theo đúng yêu cầu user)
1. Trang `/luyen-tap-nhanh`: chọn **nhiều bài** (checkbox) + **nhiều cụm**
   trong các bài đó (sau khi có F-A; trước khi F-A xong thì chọn theo bài).
   Hiện tổng số từ sẽ luyện (real-time).
2. Bấm "Bắt đầu" → sinh quiz từ đúng danh sách từ đã chọn, **KHÔNG lưu
   thành bộ thẻ** (không tạo CustomSet).
3. Màn kết quả cho 3 lựa chọn:
   - **Luyện lại từ sai** — chỉ các từ trả lời sai.
   - **Luyện lại toàn bộ** — đúng bộ từ vừa chọn.
   - **Chọn lại từ** — quay về màn chọn.
4. Chế độ quiz: dùng 3 mode hiện có (Nhật→Việt, Việt→Nhật, Nghe→chọn);
   typing để phase sau.

### Thiết kế kỹ thuật
- **Backend:** `POST /api/quiz/quick` nhận `{ wordIds: [...], mode }`
  → tái dùng logic sinh quiz của `QuizService` (tách hàm sinh câu hỏi theo
  danh sách từ ra khỏi `resolveScope`). Validate: 1–100 từ (mode LISTENING
  cần ≥4 từ sau khi lọc audio để có đáp án nhiễu; các mode khác ≥1).
  - Session: **endpoint KHÔNG tạo StudySession** (giống `generate()` hiện tại
    cũng không tạo). FE ghi lịch sử bằng `POST /api/sessions` với
    `scopeType: "QUICK"`, `scopeId: "quick"`, `mode`, `items` sau khi làm bài;
    SM-2 cập nhật qua `PATCH /api/progress/answer` từng câu như quiz thường.
    Không tạo CustomSet.
- **Frontend:** trang chọn (bài + cụm, multi-select) → trang làm quiz
  (tái dùng component Quiz) → trang kết quả với 3 nút trên.
- **Không cần migration DB.**

### Rủi ro
- Thấp: tái dùng code hiện có, chỉ thêm 1 endpoint + 1 trang.

---

## Kế hoạch triển khai (sau khi duyệt)

1. **Data:** script gán cụm cho 2.692 từ → `clusters.json`; biên soạn
   `kanji.json` (~600–700 chữ).
2. **Backend:** field `cluster`, collection `kanji`, API clusters/kanji,
   `POST /api/quiz/quick`. (PR riêng)
3. **Seed:** cập nhật `seed-import.py` (đọc cluster), `seed-kanji.py` mới.
4. **Frontend:** trang bài học (nhóm theo cụm), trang kanji + flashcard kanji,
   trang luyện tập nhanh. (PR riêng)
5. **Review UI:** đưa user duyệt giao diện từng màn trước khi merge
   (theo yêu cầu standing của user).

## Ngoài phạm vi đợt này
- Quiz kanji (chỉ flashcard).
- Sửa/xóa cụm hàng loạt (admin sửa từng từ được, đủ cho v1).
- Thanh toán online / PREMIUM (phase 2 theo kế hoạch cũ).
