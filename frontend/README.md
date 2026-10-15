# Master JP — Frontend

React 19 + Vite + react-router-dom + axios. CSS thuần (file .css theo component/trang).

## Chạy dev

```bash
cd frontend
npm install
npm run dev   # http://localhost:5173
```

Backend Spring Boot chạy ở `http://localhost:8080`. Vite proxy chuyển tiếp:
- `/api` → `http://localhost:8080`
- `/audio` → `http://localhost:8080`

Axios dùng `baseURL: '/api'` (hoặc `VITE_API_BASE_URL` nếu set) — **không hardcode localhost trong code**.

## Quy ước quan trọng

- **Auth token**: response backend có field `accessToken` — FE destructure đúng `{ accessToken }`
  (bug cũ: dùng `{token}` → localStorage lưu chuỗi `"undefined"` → mọi API 401).
  Token lưu ở localStorage key `mjp_access_token`. Interceptor 401 → chuyển `/dang-nhap`.
- **Persistence contract**: mọi thao tác ghi dữ liệu user đều gọi đúng 1 API backend ngay
  (PATCH `/api/progress/answer` sau mỗi câu trả lời flashcard/quiz/ôn tập; POST `/api/sessions`
  khi kết thúc phiên; CRUD bộ thẻ qua `/api/sets`). Không ghi tiến trình/bộ thẻ vào localStorage.
- **Copy tiếng Việt**: không hiện mã nội bộ ra UI — `VOCAB` → "Từ vựng chính",
  `JP_VI` → "Nhật → Việt"... (map trong `src/utils.js`).
- **Chia sẻ công khai**: nút "Chia sẻ công khai" chỉ hiện khi `user.plan === 'PREMIUM'` hoặc
  `role === 'ADMIN'`; backend enforce 403 cho FREE (FE hiện thông báo nâng cấp gói).

## Cấu trúc

```
src/
  api/        client.js (axios + interceptor), auth.js, lessons.js, study.js (quiz/progress/sessions), sets.js, admin.js
  context/    AuthContext.jsx (user, login/logout, canSharePublic)
  components/ Header, Footer, WordCard, AudioButton (ẩn khi audioUrl null), LoginPrompt (modal mời đăng nhập cho guest), AddToSetModal, ProtectedRoute/AdminRoute, AdminLayout
  pages/      Home, Lessons, LessonDetail, Dictionary, Flashcard, Quiz, ReviewDue, Sets, SetDetail, Explore, Dashboard, Login, Register (2 bước OTP), ForgotPassword, admin/*
  utils.js    map mã nội bộ → tiếng Việt, format ngày, shuffle, playAudio
```

## Routes

| Route | Màn hình | Quyền |
|---|---|---|
| `/` | Trang chủ giới thiệu | public |
| `/bai-hoc` | Danh sách bài (tab N5/N4) | public |
| `/bai-hoc/:id` | Chi tiết bài (3 tab phần) | public |
| `/tu-dien` | Tìm kiếm từ điển | public |
| `/kham-pha` | Bộ thẻ công khai | public |
| `/dang-nhap`, `/dang-ky`, `/quen-mat-khau` | Auth | public |
| `/luyen-tap/flashcard`, `/luyen-tap/quiz` | Flashcard / Quiz 3 dạng | login |
| `/on-tap` | Ôn tập đến hạn (SM-2) | login |
| `/bo-the`, `/bo-the/:id` | Bộ thẻ của tôi / chi tiết | login |
| `/tong-quan` | Dashboard tiến trình + streak | login |
| `/admin` (+ 6 trang con) | Quản trị | ROLE_ADMIN |
