# User Stories — Website luyện tiếng Nhật

## Role: Học viên (LEARNER)

### US-001 Đăng ký bằng email + mã xác thực → F-001, F-002
- **Given** tôi chưa có tài khoản và đang ở trang đăng ký
- **When** tôi nhập email hợp lệ và bấm "Gửi mã", rồi nhập đúng mã 6 số trong
  5 phút và đặt mật khẩu ≥ 8 ký tự
- **Then** tài khoản được tạo, tôi đăng nhập ngay và thấy dashboard trống
  (0 từ đã học)

### US-002 Đăng nhập và quên mật khẩu → F-003, F-004
- **Given** tôi đã có tài khoản
- **When** tôi nhập đúng email + mật khẩu
- **Then** tôi vào được dashboard; token hết hạn sau 24h thì phải đăng nhập lại
- **Given** tôi quên mật khẩu
- **When** tôi xin mã qua email và đặt mật khẩu mới
- **Then** mật khẩu cũ không dùng được nữa, mật khẩu mới đăng nhập được

### US-003 Học từ vựng theo bài và theo phần → F-010, F-011, F-013
- **Given** tôi đang học N5 bài 7
- **When** tôi mở bài 7, chọn tab "Từ vựng chính"
- **Then** tôi thấy danh sách từ của đúng phần đó, mỗi từ có nút phát audio;
  tab "Từ tham khảo" hiện danh sách khác, không lẫn

### US-004 Tìm kiếm từ điển → F-012
- **Given** tôi nhớ mang máng một từ
- **When** tôi gõ "taberu", "食べる" hoặc "an" (không dấu của "ăn")
- **Then** từ 食べる (taberu — ăn) xuất hiện trong kết quả, kèm bài chứa nó
  và nút "＋ thêm vào bộ thẻ"

### US-005 Ôn bằng flashcard → F-020, F-027
- **Given** tôi chọn flashcard cho "Bài 7 — phần từ vựng chính"
- **When** tôi lật từng thẻ và bấm Nhớ/Chưa nhớ
- **Then** mỗi lần bấm được lưu ngay; tôi thoát giữa chừng, vào lại vẫn thấy
  các thẻ đã đánh giá không bị hỏi lại trong phiên này

### US-006 Quiz 3 dạng → F-021, F-022, F-023
- **Given** tôi chọn quiz Nghe cho bài 7
- **When** tôi nghe audio và chọn 1 trong 4 đáp án, làm hết 20 câu
- **Then** tôi thấy điểm, danh sách câu sai (kèm đáp án đúng), và các từ sai
  được đưa vào lịch ôn sớm hơn

### US-007 Tạo bộ thẻ tự chọn → F-024, F-025
- **Given** tôi muốn gom từ khó từ nhiều bài
- **When** tôi tạo bộ "Từ hay quên", rồi từ các bài tick chọn 30 từ và thêm vào
- **Then** bộ thẻ có đúng 30 từ; thêm lại từ đã có thì hệ thống báo "đã có sẵn"
  chứ không trùng

### US-008 Ôn tập đến hạn → F-026
- **Given** hôm qua tôi học 40 từ mới
- **When** hôm nay tôi mở "Ôn tập đến hạn"
- **Then** tôi thấy đúng các từ đến kỳ (theo SM-2), từ quá hạn lâu xếp trước;
  trả lời đúng liên tiếp thì khoảng cách ôn giãn ra

### US-009 Xem tiến trình & streak → F-028, F-029
- **Given** tôi đã học 5 ngày liên tiếp
- **When** tôi mở dashboard
- **Then** tôi thấy streak = 5, số từ NEW/LEARNING/MASTERED theo N5/N4,
  và % hoàn thành từng bài

## Role: Admin (ADMIN)

### US-010 Quản trị từ vựng → F-031, F-033
- **Given** tôi là admin và phát hiện nghĩa của một từ chưa chuẩn
- **When** tôi sửa nghĩa, lưu lại
- **Then** học viên thấy nghĩa mới ngay; audio được tạo lại cho từ đó;
  thao tác được ghi vào audit log

### US-011 Import Excel → F-032
- **Given** tôi có file Excel 50 từ cho bài mới
- **When** tôi tải lên
- **Then** hệ thống báo trước "48 hợp lệ, 2 lỗi ở dòng 12, 37"; tôi sửa file,
  tải lại, xác nhận thì 50 từ mới vào DB, mỗi từ có audio

### US-012 Thống kê tổng hợp → F-034
- **Given** tôi là admin
- **When** tôi mở trang thống kê
- **Then** tôi thấy số user, tổng phiên học/ngày, top từ bị sai nhiều nhất —
  và không thấy được tiến trình riêng của từng học viên

## Tiêu chí chung (áp dụng mọi story)
- Mọi thay đổi tiến trình học phải persist server ngay (F-027), không phụ thuộc
  localStorage.
- Mọi API /api/admin/** không có ROLE_ADMIN → 403.
- Lỗi validate trả message tiếng Việt, dễ hiểu với người học (không hiện stack trace).

## Role: phân quyền bổ sung

### US-013 PREMIUM chia sẻ bộ thẻ công khai → F-024, F-035
- **Given** tôi có gói PREMIUM và một bộ thẻ "Từ hay quên"
- **When** tôi bật "Chia sẻ công khai"
- **Then** bộ thẻ xuất hiện ở trang Khám phá; người khác sao chép được về
  tài khoản của họ

### US-014 FREE bị chặn chia sẻ công khai → F-024
- **Given** tôi dùng gói FREE
- **When** tôi cố bật "Chia sẻ công khai" (gọi API trực tiếp)
- **Then** hệ thống trả 403 kèm thông báo "Nâng cấp gói để chia sẻ công khai";
  bộ thẻ của tôi vẫn riêng tư bình thường

### US-015 Admin gán gói PREMIUM → F-036
- **Given** tôi là admin
- **When** tôi gán gói PREMIUM 30 ngày cho một user
- **Then** user đó chia sẻ công khai được ngay; hết 30 ngày tự về FREE nhưng
  các bộ đã chia sẻ vẫn công khai

### US-016 GUEST xem nội dung read-only
- **Given** tôi chưa đăng nhập
- **When** tôi mở một bài học
- **Then** tôi xem được từ vựng và nghe audio; bấm "Luyện tập" thì được mời
  đăng nhập

### US-017 Gõ phiên âm → F-037, F-027
- **Given** tôi đã đăng nhập và chọn "Gõ phiên âm" cho bài 1
- **When** màn hình hiện 私 + nghĩa "tôi", tôi gõ "watashi" (tự chuyển thành
  わたし realtime) rồi bấm Kiểm tra
- **Then** hệ thống báo đúng (màu xanh); mỗi câu được lưu tiến trình ngay;
  gõ sai thì hiện đáp án đúng (màu đỏ) và từ đó được đưa vào lịch ôn sớm hơn;
  hết phiên tôi thấy điểm và danh sách từ gõ sai
