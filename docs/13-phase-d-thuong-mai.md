# DOC-13 — Phase D: Thương mại (SePay webhook · Freemium + paywall · Email Resend)

**Ngày:** 2026-10-23 · **Trạng thái:** SPEC — chưa code, tài liệu này là source of truth cho implementer BE/FE
**Kế hoạch gốc:** `~/workspace/master-jp-v2/KE-HOACH-PHAT-TRIEN.md` Phần 5 (Phase D: D1/D2/D3)
**IA liên quan:** `docs/10-v2-ia-redesign.md` (tab Cá nhân `/ca-nhan`, homepage Phase A pricing)
**Branch triển khai:** `v2/phase-d-thuong-mai` (branch hiện tại)
**⚠️ Code Java viết trong môi trường không-JDK: CHƯA COMPILE** — ghi rõ trong PR, dặn user đọc log build Render/Docker.

## 0. Quyết định đánh số (binding)

Kiểm tra `docs/03-functions.md` (2026-10-23): số cao nhất đang dùng là **F-037**
(F-038/039/040 của Phase B — DOC-11 — và F-041/042/043 của Phase C — DOC-12 —
chưa được merge vào file này, không xung đột).
Theo operating rule của doc-writer (số mới nối tiếp số cuối, không sửa requirement đã duyệt),
3 function mới được đánh số:

| Phase | Số chính thức | Tên |
|---|---|---|
| D1 | **F-044** | SePay webhook — tự động kích hoạt Premium |
| D2 | **F-045** | Freemium limits (20 từ mới/ngày) + paywall |
| D3 | **F-046** | Email qua Resend (welcome · payment success · nhắc ôn tập) |

FR tương ứng: **FR-044 / FR-045 / FR-046** (mirror F). **[Giả định]** BE/FE implementer
dùng đúng số này; orchestrator cập nhật `01-SRE.md`, `03-functions.md`, `02-ERD.md`,
`04-user-stories.md` sau khi spec này được duyệt (mục 11).

---

## 1. Tổng quan & vị trí trong IA

Phase D biến Master JP từ "app học miễn phí" thành sản phẩm có dòng tiền:

- **F-044 (SePay webhook)** — user tạo hóa đơn trong app → chuyển khoản ngân hàng
  với nội dung = mã hóa đơn → SePay webhook báo về → backend tự động match và nâng
  user lên PREMIUM. Không qua cổng thanh toán trung gian, không phí giao dịch.
- **F-045 (Freemium + paywall)** — plan FREE giới hạn **20 từ mới/ngày**; chạm trần
  thì hiện paywall phong cách washi (giá 49k/tháng · 390k/năm — khớp pricing homepage
  Phase A) thay vì lỗi thô.
- **F-046 (Email Resend)** — email chào mừng khi đăng ký, email xác nhận thanh toán
  thành công, email nhắc ôn tập hàng ngày 8:00 (Asia/Ho_Chi_Minh, có opt-in/opt-out).

**Ràng buộc kiến trúc (binding):**
- Backend pattern theo Phase B/C: `@RestController`, DTO `record`,
  `@AuthenticationPrincipal UserPrincipal`, error shape `{code, message}` qua
  `ApiException` + `GlobalExceptionHandler`, múi giờ `Asia/Ho_Chi_Minh` qua `AppTime`
  (đã có — reuse, không tự `LocalDate.now()`).
- Không thêm dependency Maven/npm mới (Resend gọi qua `RestTemplate`/`WebClient`
  có sẵn của Spring).
- Không emoji làm icon trong code mới (email template cũng tuân thủ — xem mục 5.5).
- `MailService` (Gmail SMTP, plain-text, dùng cho OTP) **giữ nguyên, không đụng** —
  D3 thêm service mới song song.

---

## 2. FR mới

- **FR-044** SePay webhook: user tạo hóa đơn trả phí (MONTHLY 49.000đ / YEARLY
  390.000đ) nhận hướng dẫn chuyển khoản với nội dung = mã hóa đơn; khi SePay báo
  giao dịch đến, hệ thống tự động tìm hóa đơn PENDING khớp mã + đủ số tiền, đánh dấu
  PAID, nâng user lên PREMIUM (+ gia hạn), gửi email xác nhận, ghi audit log.
  Giao dịch không khớp mã hoặc thiếu tiền → UNMATCHED để xử lý tay.
- **FR-045** Freemium: user plan FREE được học tối đa **20 từ mới mỗi ngày**
  (từ mới = từ lần đầu được đánh dấu "đã học" trong ngày); vượt trần → backend trả
  lỗi `FREEMIUM_LIMIT` (429) kèm `{limit, resetAt}`; frontend hiện paywall washi
  thay vì toast lỗi.
- **FR-046** Email Resend: gửi 3 loại email HTML phong cách washi, giọng Tanu-chan:
  (1) chào mừng khi đăng ký xong, (2) xác nhận thanh toán khi hóa đơn được fulfill,
  (3) nhắc ôn tập lúc 8:00 sáng hàng ngày cho user còn từ đến hạn và đã opt-in
  (mặc định opt-in).

---

## 3. F-044 — SePay webhook

### 3.1. Luồng tổng thể (binding)

```
User (đã login) ──POST /api/payments/invoices {planType}──▶ Backend
     ◀── 201 {invoice + transferInstructions} ──
User ── chuyển khoản ──▶ Ngân hàng ── webhook ──▶ SePay ──POST /api/payments/sepay/webhook──▶ Backend
     Backend: verify secret → idempotency check → regex mã hóa đơn → tìm invoice PENDING
        → đối chiếu số tiền → fulfill (PAID + PREMIUM + email + audit) / UNMATCHED
```

### 3.2. Collection mới `invoices`

| Field | Type | Ghi chú |
|---|---|---|
| `_id` | ObjectId | |
| `code` | String | **unique** — format `INV-XXXXXX` (6 ký tự uppercase alnum, vd. `INV-9F3A2B`); sinh bằng `SecureRandom`, kiểm tra trùng trước insert |
| `userId` | ObjectId | index — chủ hóa đơn |
| `planType` | String | `"MONTHLY"` hoặc `"YEARLY"` (enum `PlanType` mới trong `domain/`) |
| `amount` | Number | VND, integer: `49000` (MONTHLY) \| `390000` (YEARLY) — backend tự set theo planType, **không tin amount từ client** |
| `status` | String | `PENDING` / `PAID` / `EXPIRED` / `CANCELLED` (enum `InvoiceStatus`) |
| `createdAt` | Date | |
| `paidAt` | Date | null cho tới khi PAID |
| `sepayTransactionId` | String | id giao dịch SePay đã match (null khi chưa match) |
| Index | | `code` unique; `(userId, createdAt)` |

**Vòng đời:**
- Tạo: `PENDING`, `createdAt = now`.
- Hết hạn: PENDING quá **24 giờ** → `EXPIRED` (job quét `0 0 2 * * *` Asia/Ho_Chi_Minh,
  mục 3.8; đồng thời lazy-check mỗi khi invoice được đọc/match).
- Hủy: user tự hủy invoice PENDING của mình → `CANCELLED`
  (`DELETE /api/payments/invoices/{code}`, mục 3.5).
- Thanh toán: chỉ chuyển `PENDING → PAID` qua **webhook fulfill** (mục 3.7) —
  **không endpoint nào cho phép client tự set PAID** (persistence contract, mục 3.9).

### 3.3. Collection mới `sepay_transactions`

| Field | Type | Ghi chú |
|---|---|---|
| `_id` | ObjectId | |
| `bankTransactionId` | String | **unique** — `id` từ payload SePay; chống double-fulfill khi SePay gửi lại webhook |
| `amount` | Number | `transferAmount` từ payload (VND) |
| `transferContent` | String | `content` từ payload (nội dung chuyển khoản — nơi tìm mã hóa đơn) |
| `transactionDate` | Date | `transactionDate` từ payload |
| `senderAccount` | String | `senderAccountName` từ payload (có thể null) |
| `status` | String | `PENDING` / `MATCHED` / `UNMATCHED` (enum `SepayTransactionStatus`) |
| `matchedInvoiceId` | ObjectId | set khi MATCHED |
| `note` | String | lý do khi UNMATCHED: `CODE_NOT_FOUND` / `AMOUNT_MISMATCH` / `INVOICE_NOT_PENDING` / `DUPLICATE_IGNORED` — phục vụ xử lý tay |
| `createdAt` | Date | |

**Mapping payload SePay → field (binding):** webhook SePay gửi JSON, chỉ xử lý khi
`transferType == "in"` (giao dịch tiền vào; tiền ra bỏ qua, trả 200).
`bankTransactionId ← id`, `amount ← transferAmount`, `transferContent ← content`,
`transactionDate ← transactionDate`, `senderAccount ← senderAccountName`.
**[Giả định]** field name đúng như tài liệu webhook SePay hiện hành; implementer log
raw payload (không log secret) khi UNMATCHED để đối chiếu lần chạy thật đầu tiên.

### 3.4. Xác thực webhook (binding — không JWT)

- Endpoint **`POST /api/payments/sepay/webhook`** là **public** (không qua JWT filter).
- Request phải mang header **`X-Webhook-Secret`** bằng đúng giá trị ENV
  `SEPAY_WEBHOOK_SECRET` (so sánh constant-time, `MessageDigest.isEqual`).
- Sai/thiếu → **`401`** `{code: "WEBHOOK_UNAUTHORIZED", message: "Webhook secret không hợp lệ."}`.
  Không log giá trị secret nhận được.
- `SEPAY_WEBHOOK_SECRET` thiếu lúc startup → **fail-fast** (app không boot) —
  theo rule "không default lặng lẽ" của AGENTS.md.

### 3.5. Contract invoice (cần JWT, trừ webhook)

**`POST /api/payments/invoices`** — tạo hóa đơn. Request: `{ "planType": "MONTHLY" }`
(`planType` chỉ nhận `MONTHLY`/`YEARLY`, sai → `400 VALIDATION_ERROR`).
Response `201`:

```json
{
  "code": "INV-9F3A2B",
  "planType": "MONTHLY",
  "amount": 49000,
  "status": "PENDING",
  "createdAt": "2026-10-23T03:10:00+07:00",
  "expiresAt": "2026-10-24T03:10:00+07:00",
  "transferInstructions": {
    "bankName": "Vietcombank",
    "accountNumber": "0123456789",
    "accountHolder": "CONG TY ...",
    "amount": 49000,
    "transferContent": "INV-9F3A2B",
    "note": "Vui lòng ghi đúng nội dung chuyển khoản để hệ thống tự động kích hoạt."
  }
}
```

- `transferInstructions` lấy thông tin tài khoản từ ENV (`SEPAY_BANK_NAME`,
  `SEPAY_BANK_ACCOUNT_NUMBER`, `SEPAY_ACCOUNT_HOLDER` — mục 6); `amount` và
  `transferContent` từ invoice vừa tạo.
- **[Giả định]** 1 user có thể có nhiều invoice PENDING cùng lúc (mỗi lần bấm "Nâng cấp"
  tạo mã mới); FE hiển thị invoice mới nhất chưa hết hạn.

**`GET /api/payments/invoices`** — cần JWT, liệt kê hóa đơn **của chính user**
(query `?status=` tùy chọn; mới nhất trước). Không bao giờ trả invoice của user khác.

**`DELETE /api/payments/invoices/{code}`** — cần JWT; chỉ hủy invoice PENDING của
chính mình → `200 {code, status: "CANCELLED"}`; invoice không tồn tại/không thuộc
user → `404 NOT_FOUND`; đã PAID/EXPIRED/CANCELLED → `400 VALIDATION_ERROR`.

**Admin (read-only, ROLE_ADMIN):**
- `GET /api/admin/payments/invoices` — query `?status=&userId=&page=&size=`,
  phân trang, mới nhất trước.
- `GET /api/admin/payments/sepay/transactions` — query `?status=&page=&size=`.
- Cả hai không có write operation. UNMATCHED ở v1 được **xử lý tay qua DB**
  (hoàn tiền / hướng dẫn user chuyển lại đúng nội dung); endpoint manual-match để
  dành phase sau — không code ở Phase D.

### 3.6. Thuật toán auto-match (binding — từng bước, đúng thứ tự)

Khi webhook hợp lệ đến (sau bước verify secret + chỉ xử lý `transferType == "in"`):

1. **Idempotency:** tìm `sepay_transactions` theo `bankTransactionId`.
   - Đã tồn tại → **không fulfill lại**, trả `200 {status: "duplicate", bankTransactionId}`.
     (Ghi `note: "DUPLICATE_IGNORED"`? Không — bản ghi đã có, giữ nguyên.)
   - Chưa có → insert bản ghi mới với `status: PENDING`, rồi tiếp tục.
2. **Trích mã hóa đơn:** áp regex **`INV[- ]?([A-Z0-9]{6})`** **case-insensitive**
   lên `transferContent`; lấy **match đầu tiên**; nhóm capture → uppercase
   → `code = "INV-" + <6 ký tự>`.
   - Không match được → set transaction `UNMATCHED`, `note: "CODE_NOT_FOUND"`,
     trả `200 {status: "unmatched", reason: "CODE_NOT_FOUND"}`. Dừng.
3. **Tìm invoice:** `invoices.findByCode(code)` — so khớp **case-insensitive**
   (code lưu DB luôn uppercase; query dùng collation hoặc uppercase trước khi tìm —
   implementer chọn 1 cách và document trong code).
   - Không tìm thấy → `UNMATCHED` / `CODE_NOT_FOUND`. Dừng.
   - Tìm thấy nhưng `status != PENDING` → `UNMATCHED` / `INVOICE_NOT_PENDING`. Dừng.
4. **Đối chiếu số tiền:** nếu `transaction.amount >= invoice.amount` → **fulfill** (mục 3.7).
   Ngược lại → `UNMATCHED` / `AMOUNT_MISMATCH`. Dừng.
   (Cho phép trả thừa — `>=`, không yêu cầu bằng chính xác.)

### 3.7. Fulfill (binding — tất cả hoặc không gì cả)

Khi match thành công, thực hiện **trong 1 transaction MongoDB** (multi-document):

1. Invoice: `status = PAID`, `paidAt = now`, `sepayTransactionId = <bankTransactionId>`.
2. Transaction: `status = MATCHED`, `matchedInvoiceId = <invoice._id>`.
3. User: `plan = PREMIUM`; `planExpiresAt` tính như sau —
   `base = (user.plan == PREMIUM && planExpiresAt != null && planExpiresAt > now) ? planExpiresAt : now`;
   `planExpiresAt = base + 30 ngày` (MONTHLY) hoặc `base + 365 ngày` (YEARLY).
   (User đang Premium mà gia hạn tiếp → **cộng dồn từ hạn cũ**, không mất ngày đã trả.)
4. Audit log: insert `audit_logs` với `action = "PAYMENT_FULFILLED"`,
   `targetType = "Invoice"`, `targetId = <invoice._id>`, `adminId = null`,
   `diff = {code, planType, amount, bankTransactionId, newPlanExpiresAt}`.
5. Email: gửi **payment-success email** qua `ResendEmailService` (mục 5.4, template 2).
   Gửi email **sau khi** transaction DB commit; email fail → log error, **không rollback**
   fulfill (user đã trả tiền — quyền lợi không được mất vì lỗi mail).

Response webhook khi fulfill: `200 {status: "matched", invoiceCode, planType, planExpiresAt}`.
Response khi unmatched: `200 {status: "unmatched", reason}` — **luôn 200** cho SePay
(trừ 401 sai secret), để SePay không retry vô hạn.

### 3.8. Job quét invoice hết hạn

`@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")` — 2:00 sáng hàng ngày:
`invoices` có `status = PENDING` và `createdAt < now - 24h` → `EXPIRED`.
(Chạy 1 lần/ngày là đủ vì TTL 24h; cron ghi rõ timezone trong code.)

### 3.9. Persistence contract — F-044

| Dữ liệu | Sống ở đâu |
|---|---|
| Hóa đơn | **Server DB** — collection mới `invoices`; chỉ tạo qua `POST /api/payments/invoices`, chỉ PAID qua webhook fulfill — **không client-supplied payment status nào được tin** |
| Giao dịch SePay | **Server DB** — collection mới `sepay_transactions`; chỉ ghi qua webhook |
| Plan / hạn Premium | **Server DB** — field hiện có `users.plan`, `users.planExpiresAt`; chỉ đổi qua fulfill hoặc `PlanExpiryService` hiện có |
| Thông tin tài khoản nhận tiền | **ENV** (`SEPAY_BANK_*`) — không hard-code trong code, không lưu DB |

### 3.10. Acceptance criteria — F-044

- [ ] `POST /api/payments/sepay/webhook` không có/khác `X-Webhook-Secret` → 401, không ghi DB.
- [ ] Webhook hợp lệ, content chứa `INV-9F3A2B` (kể cả viết thường `inv 9f3a2b`), amount ≥ 49000 → invoice PAID, transaction MATCHED, user PREMIUM, `planExpiresAt` đúng công thức cộng dồn.
- [ ] Gửi lại cùng webhook (cùng `id`) → 200 duplicate, không nâng hạn lần 2, không email lần 2.
- [ ] Content không có mã → UNMATCHED / CODE_NOT_FOUND; amount thiếu → AMOUNT_MISMATCH; invoice đã PAID → INVOICE_NOT_PENDING.
- [ ] Không endpoint nào cho phép client tự set invoice PAID (contract-guard check).
- [ ] PENDING quá 24h → EXPIRED (job 2:00 sáng + lazy-check).
- [ ] Admin xem được invoices/transactions (read-only); user chỉ thấy invoice của mình.
- [ ] Fulfill xong gửi đúng email payment-success (mục 5.4); email fail không rollback.

---

## 4. F-045 — Freemium limits + paywall

### 4.1. Rule (binding)

- Plan **FREE**: tối đa **20 từ mới mỗi ngày** (ngày theo **Asia/Ho_Chi_Minh**).
- **"Từ mới"** = từ/kanji **lần đầu tiên được đánh dấu "đã học" trong ngày**, định nghĩa
  kỹ thuật chính xác: **thời điểm tạo mới** document `word_progress` (F-026) hoặc
  `kanji_progress` (F-043) cho user — tức lần đầu user tương tác học với từ đó qua
  bất kỳ đường nào (flashcard, quiz, review queue, shadowing).
- Ôn tập từ đã học (update progress có sẵn) **không** tính vào hạn mức.
- Plan **PREMIUM**: không giới hạn.

### 4.2. Tracking — collection mới `daily_usage` (binding: chọn option rẻ nhất)

Mirror pattern `ai_usage` của Phase B (atomic `findAndModify` + upsert — đã chứng minh
chạy đúng), không thêm cơ chế mới:

| Field | Type | Ghi chú |
|---|---|---|
| `_id` | ObjectId | |
| `userId` | ObjectId | |
| `date` | String | `yyyy-MM-dd` theo Asia/Ho_Chi_Minh (qua `AppTime`, như `AiRateLimiter`) |
| `newWordCount` | Number | số từ mới đã học trong ngày |
| Unique index | | `(userId, date)` |

**Quyết định (binding):** track cho **mọi user** (FREE lẫn PREMIUM) — cùng 1 code path,
không rẽ nhánh; chỉ FREE bị enforce. Chi phí = 1 atomic upsert/ngày/user, tương đương
`ai_usage` hiện có.

### 4.3. Enforcement (binding — atomic, đúng điểm chặn)

Điểm chặn duy nhất: **trong `ProgressService` (BE), ngay trước khi tạo mới**
`word_progress`/`kanji_progress` (tức trước khi 1 từ được "học lần đầu"):

```
1. count = daily_usage.findAndModify({userId, date: today}, {$inc: {newWordCount: 1}}, upsert, returnNew)
2. Nếu user.plan == FREE && count.newWordCount > 20:
     → KHÔNG tạo word_progress/kanji_progress (từ chưa được đánh dấu đã học)
     → ném FreemiumLimitException → 429 {code: "FREEMIUM_LIMIT", ...} (mục 7)
   Ngược lại: tiếp tục tạo progress như bình thường.
```

- Atomic inc-trước-check-sau (giống `AiRateLimiter.consume`): 2 request song song
  không thể cùng lọt qua khe 20 → 21.
- Hạn mức check **sau khi** `PlanExpiryService.applyIfExpired` đã chạy (login/me) —
  user vừa hết Premium thì tính là FREE ngay.
- Các endpoint AI của Phase B (`/api/ai/...`): **giữ nguyên** `AI_DAILY_LIMIT_PER_USER`
  (default 50), không gộp vào hạn 20 từ. AI endpoint không tạo `word_progress` nên
  không chạm F-045; FE **thống nhất message** hiển thị cho cả 2 mã lỗi (mục 4.4).

### 4.4. Paywall FE (binding)

Khi nhận `429 FREEMIUM_LIMIT` (hoặc `429 DAILY_LIMIT` từ AI quota), FE **không hiện
toast lỗi thô** mà mở **PaywallModal** phong cách washi:

- Tiêu đề (giọng Tanu-chan): 「Hôm nay bạn đã học đủ 20 từ mới rồi!」
- Nội dung: số từ đã học hôm nay / reset lúc 00:00; 2 gói giá **khớp homepage Phase A**:
  **49.000đ/tháng** · **390.000đ/năm**; nút "Nâng cấp Premium" → mở flow tạo invoice
  (mục 3.5) + hướng dẫn chuyển khoản; nút phụ "Để mai học tiếp" đóng modal.
- Trigger points: (a) `PATCH /api/progress/answer` trả 429 khi học từ mới thứ 21;
  (b) AI endpoint trả 429 hết quota — cùng 1 modal, chỉ đổi dòng tiêu đề.
- Không chặn: xem lại từ đã học, ôn tập queue, đọc ngữ pháp/kanji, mọi màn hình khác.

### 4.5. Persistence contract — F-045

| Dữ liệu | Sống ở đâu |
|---|---|
| Số từ mới/ngày | **Server DB** — collection mới `daily_usage`; inc atomic qua `findAndModify`, không tin con số từ client |
| Định nghĩa "từ mới" | Suy ra từ **việc tạo** `word_progress`/`kanji_progress` — không collection định nghĩa riêng |
| Plan user | `users.plan` hiện có (đọc lúc enforce, sau `applyIfExpired`) |

### 4.6. Acceptance criteria — F-045

- [ ] FREE học từ mới thứ 21 trong ngày → `429 FREEMIUM_LIMIT` + `{limit: 20, resetAt}`;
      từ đó **không** có `word_progress` trong DB.
- [ ] Ôn từ cũ (update progress có sẵn) không tăng `daily_usage.newWordCount`.
- [ ] 2 request song song ở mốc 20 → chỉ 1 lọt, request còn lại 429 (atomic).
- [ ] Qua 00:00 (Asia/Ho_Chi_Minh) → hạn mức reset, học tiếp được.
- [ ] PREMIUM không bao giờ bị chặn (vẫn track `daily_usage` nhưng không enforce).
- [ ] User hết hạn Premium → request tiếp theo tính là FREE (qua `applyIfExpired`).
- [ ] FE: 429 → mở PaywallModal washi (giá 49k/390k), không toast lỗi thô; AI 429
      dùng cùng modal.

---

## 5. F-046 — Email qua Resend

### 5.1. Kiến trúc (binding)

- Service mới **`ResendEmailService`** (package `service/`): gửi qua
  **`POST https://api.resend.com/emails`** với header
  `Authorization: Bearer <RESEND_API_KEY>`, body JSON
  `{from: <RESEND_FROM_EMAIL>, to: [email], subject, html}`.
- `MailService` (Gmail SMTP, plain-text) **giữ nguyên 100% cho OTP** — không refactor,
  không dùng chung interface ở Phase D (tránh regression luồng đăng nhập).
- ENV thiếu (`RESEND_API_KEY` hoặc `RESEND_FROM_EMAIL`) → **fail-fast lúc startup**.
- Gửi email là **best-effort**: fail → log error (không log API key), không throw lên
  caller, không rollback nghiệp vụ đã commit.

### 5.2. Template 1 — Welcome (khi đăng ký xong)

- **Trigger:** sau khi user verify OTP đăng ký thành công (`AuthService`).
- **Subject:** `Chào mừng bạn đến với Master JP — Tanu-chan đồng hành cùng bạn!`
- **Sections (HTML washi — inline CSS, palette kem/chàm/đỏ shu-iro):**
  1. Chào `「<tên hoặc email>」` + Tanu-chan giới thiệu ngắn (1–2 câu, giọng khích lệ).
  2. **3 bước bắt đầu:** (a) Học Bài 1 ở tab Học, (b) Ôn bằng flashcard 3D,
     (c) Giữ streak mỗi ngày — mỗi bước 1 dòng + nút CTA.
  3. Lưu ý gói Free: 20 từ mới/ngày + link nâng cấp Premium (không pushy, 1 dòng).
  4. Nút CTA chính: "Bắt đầu học ngay" → link homepage/app.
  5. Footer: lý do nhận email (vừa đăng ký) + link tắt nhắc ôn tập.

### 5.3. Template 2 — Payment success (khi invoice fulfill)

- **Trigger:** sau khi transaction fulfill commit thành công (mục 3.7, bước 5).
- **Subject:** `Thanh toán thành công — chào mừng bạn đến với Premium!`
- **Sections:**
  1. Cảm ơn + xác nhận: mã hóa đơn `<code>`, gói `<MONTHLY|YEARLY>`, số tiền
     `<amount>đ`, ngày hết hạn `<planExpiresAt dd/MM/yyyy>`.
  2. **Quyền lợi đã mở:** học không giới hạn từ mới/ngày; ưu tiên Sensei AI;
     "Huy hiệu Người ủng hộ" (đồng bộ với paywall/Home/NangCap — copy-ux-linter
     2026-10-22: ưu tiên nhất quán sản phẩm hơn câu chữ giả định cũ).
  3. Nút CTA: "Khám phá Premium ngay".
  4. Footer: hóa đơn điện tử sẽ được gửi riêng (nếu có) + liên hệ hỗ trợ.

### 5.4. Template 3 — Daily review reminder (cron)

- **Cron (binding):** `@Scheduled(cron = "0 0 8 * * *", zone = "Asia/Ho_Chi_Minh")`
  — **8:00:00 sáng hàng ngày, giờ Việt Nam**. (6 field cron của Spring:
  giây-phút-giờ-ngày-tháng-thứ.)
- **Đối tượng:** user có **email đã verify**, `emailReminderOptIn == true`
  (**default `true`** cho user mới — field mới trong `users`, mục 5.6), và có ít nhất
  1 item đến hạn: `word_progress.nextReviewAt <= now` HOẶC
  `kanji_progress.nextReviewAt <= now` HOẶC `wrong_answers {resolved: false}` tồn tại.
- **Subject:** `Tanu-chan nhắc bạn: hôm nay có <N> mục chờ ôn tập!`
  (`<N>` = tổng 3 nguồn; nếu N = 0 → không gửi).
- **Sections:**
  1. Chào buổi sáng + số mục chờ ôn (`<N>`) + streak hiện tại (`<streakDays> ngày liên tiếp — giữ lửa nhé!`).
  2. Nút CTA: "Ôn tập ngay" → link `/on-tap`.
  3. Dòng nhỏ: "Không muốn nhận email này? Tắt trong Cài đặt → Thông báo."
       (FE: toggle opt-in ở `/ca-nhan`, mục 8.4.)
- **Chống spam:** mỗi user tối đa 1 email/ngày (job chạy 1 lần/ngày nên tự nhiên đủ;
  nếu retry job → check `email_logs`? **v1: không collection log riêng**, job idempotent
  theo ngày nhờ cron đơn — **[giả định]** document trong code).

### 5.5. Quy ước template chung (binding)

- HTML email với **inline CSS** (email client không đọc external CSS); palette washi:
  nền kem `#FAF7F0`, chàm `#2B3A67`, đỏ shu-iro `#EB471F`, chữ `#333`.
- Layout 1 cột, max-width 600px, font stack hệ thống.
- Giọng Tanu-chan: thân thiện, khích lệ, câu ngắn; dùng ngoặc 「」 cho từ Nhật;
  **không emoji** (kể cả subject).
- Mọi link CTA là URL tuyệt đối, lấy base từ ENV `FRONTEND_URL` hiện có
  (không hard-code domain).

### 5.6. Thay đổi collection `users` (không collection mới)

Thêm field **`emailReminderOptIn`** (Boolean, **default `true`**).
User hiện có (đã tồn tại trước Phase D): treat `null` như `true` ở query
(`emailReminderOptIn != false`) — không cần migration script.

### 5.7. Persistence contract — F-046

| Dữ liệu | Sống ở đâu |
|---|---|
| Nội dung email | **Hard-code template trong backend** (`ResendEmailService` + thymeleaf/text block — implementer chọn 1, document); không lưu DB |
| Opt-in nhắc ôn tập | **Server DB** — field mới `users.emailReminderOptIn` (default true; null = true) |
| Trạng thái đã gửi | **Không persist ở v1** (job 1 lần/ngày, idempotent theo ngày) |

### 5.8. Acceptance criteria — F-046

- [ ] Đăng ký verify OTP xong → nhận welcome email (subject + đủ 5 sections).
- [ ] Invoice fulfill → nhận payment-success email với đúng code/plan/amount/hạn.
- [ ] 8:00 sáng: user có từ đến hạn + opt-in → nhận reminder đúng N mục; user không
      có từ đến hạn → không nhận; user tắt opt-in → không nhận.
- [ ] Thiếu `RESEND_API_KEY`/`RESEND_FROM_EMAIL` → app fail-fast lúc startup.
- [ ] Email gửi fail → log error, không throw, không rollback nghiệp vụ.
- [ ] `MailService` OTP vẫn chạy bình thường (không regression).

---

## 6. ENV vars

| ENV | Bắt buộc? | Default | Mô tả |
|---|---|---|---|
| `SEPAY_WEBHOOK_SECRET` | **Bắt buộc** (D1) | — (fail-fast nếu thiếu) | Shared secret verify webhook SePay qua header `X-Webhook-Secret` |
| `SEPAY_BANK_NAME` | **Bắt buộc** (D1) | — (fail-fast nếu thiếu) | Tên ngân hàng nhận tiền, vd. `Vietcombank` — hiện trong hướng dẫn chuyển khoản |
| `SEPAY_BANK_ACCOUNT_NUMBER` | **Bắt buộc** (D1) | — (fail-fast nếu thiếu) | Số tài khoản nhận tiền |
| `SEPAY_ACCOUNT_HOLDER` | **Bắt buộc** (D1) | — (fail-fast nếu thiếu) | Tên chủ tài khoản (viết hoa không dấu theo quy định ngân hàng) |
| `SEPAY_API_KEY` | Tùy chọn | rỗng | **Dự phòng** cho SePay API trực tiếp (truy vấn số dư/lịch sử) — Phase D chưa dùng, webhook không cần |
| `RESEND_API_KEY` | **Bắt buộc** (D3) | — (fail-fast nếu thiếu) | API key Resend, dùng làm `Authorization: Bearer` |
| `RESEND_FROM_EMAIL` | **Bắt buộc** (D3) | — (fail-fast nếu thiếu) | Địa chỉ gửi, vd. `Master JP <hello@masterjp.vn>` — phải là domain đã verify trong Resend |

**Đặt ở đâu:** Production → Render Dashboard → Environment; local → `backend/.env`
(gitignored). Không commit secret vào git. Render free tier lưu ý: đổi ENV cần
manual redeploy.

---

## 7. Error contract (bổ sung cho Phase D)

| HTTP | `code` | `message` (tiếng Việt) | Khi nào |
|---|---|---|---|
| 401 | `WEBHOOK_UNAUTHORIZED` | "Webhook secret không hợp lệ." | `POST /api/payments/sepay/webhook` thiếu/sai `X-Webhook-Secret` |
| 400 | `VALIDATION_ERROR` | vd. "planType chỉ nhận MONTHLY hoặc YEARLY." | `planType` sai; hủy invoice đã PAID/EXPIRED/CANCELLED |
| 404 | `NOT_FOUND` | "Không tìm thấy hóa đơn." | invoice code không tồn tại / không thuộc user |
| 429 | `FREEMIUM_LIMIT` | "Hôm nay bạn đã học đủ 20 từ mới. Nâng cấp Premium để học không giới hạn nhé!" | FREE vượt 20 từ mới/ngày |
| 429 | `DAILY_LIMIT` | (giữ nguyên message Phase B) | Hết quota AI (`AI_DAILY_LIMIT_PER_USER`) |

Shape `FREEMIUM_LIMIT` (binding — FE đọc để render paywall):

```json
{
  "code": "FREEMIUM_LIMIT",
  "message": "Hôm nay bạn đã học đủ 20 từ mới. Nâng cấp Premium để học không giới hạn nhé!",
  "limit": 20,
  "resetAt": "2026-10-24T00:00:00+07:00"
}
```

- `resetAt`: 00:00 ngày hôm sau theo Asia/Ho_Chi_Minh (ISO 8601 có offset).
- Webhook fulfill/unmatched **luôn trả 200** (trừ 401 sai secret) — không dùng mã lỗi
  bảng này cho response webhook.

---

## 8. Security & privacy

1. **Webhook không tin client:** status PAID chỉ được set bởi fulfill nội bộ sau khi
   verify secret + match DB. Không có request nào từ browser/app có thể nâng plan.
2. **Secret hygiene:** so sánh `X-Webhook-Secret` bằng constant-time compare; không
   log secret; thiếu ENV thì fail-fast thay vì chạy ở chế độ "mở".
3. **Idempotency là bắt buộc:** unique index `bankTransactionId` + check status
   PENDING trước fulfill — SePay retry webhook không thể double-charge quyền lợi.
4. **Scope dữ liệu:** invoice list/create/cancel lấy `userId` từ `@AuthenticationPrincipal`
   — không tin field user trong request; admin endpoints yêu cầu `ROLE_ADMIN`.
5. **Email:** chỉ gửi tới email đã verify của chính user; không lộ invoice của user
   khác; `RESEND_API_KEY` không bao giờ rời backend (không nhúng vào FE, không log).
6. **UNMATCHED không auto-refund:** tiền đã vào tài khoản — xử lý tay (liên hệ user,
   hoàn tiền thủ công). Không code refund tự động ở Phase D.

---

## 9. FE integration (tổng hợp)

- Module mới **`frontend/src/api/payments.js`**: `createInvoice(planType)`,
  `fetchMyInvoices(status?)`, `cancelInvoice(code)`.
- **PaywallModal** (component mới, washi-styled): dùng chung cho `FREEMIUM_LIMIT`
  và `DAILY_LIMIT` (đổi tiêu đề theo code); giá 49k/tháng · 390k/năm khớp homepage.
- **Trang/modal "Nâng cấp":** chọn gói → `POST /api/payments/invoices` → hiện
  `transferInstructions` (tên NH, số TK, số tiền, **nội dung chuyển khoản in đậm** +
  nút copy) + danh sách invoice PENDING của mình (kèm countdown hết hạn 24h).
  Entry point: nút trong PaywallModal + mục "Premium" ở `/ca-nhan`.
- **`PATCH /api/progress/answer`** (mục đã có từ Phase C): FE catch 429
  `FREEMIUM_LIMIT` → mở PaywallModal thay vì toast.
- **`/ca-nhan` → Cài đặt/Thông báo:** toggle `emailReminderOptIn`
  (`PATCH /api/users/me` mở rộng hoặc endpoint settings hiện có — implementer chọn,
  document trong code).
- Không thêm dependency npm mới.

---

## 10. Ma trận truy vết FR → Function → Entity

| FR | Function | Entities |
|----|----------|----------|
| FR-044 | F-044 | `invoices` (mới, write), `sepay_transactions` (mới, write), `users` (write: `plan`, `planExpiresAt`), `audit_logs` (write: `PAYMENT_FULFILLED`) |
| FR-045 | F-045 | `daily_usage` (mới, write), `word_progress`/`kanji_progress` (read: check tồn tại trước khi tạo), `users` (read: `plan`) |
| FR-046 | F-046 | `users` (+field mới `emailReminderOptIn`), `word_progress`/`kanji_progress`/`wrong_answers` (read: tính N mục đến hạn) |

Entity mới so với `docs/02-ERD.md`: `invoices`, `sepay_transactions`, `daily_usage`;
`users` thêm `emailReminderOptIn`; enum mới `PlanType`, `InvoiceStatus`,
`SepayTransactionStatus`.

---

## 11. Việc đồng bộ docs còn lại (cho orchestrator)

Spec này là DOC-13 độc lập. Sau khi duyệt, cần cập nhật để không lệch doc/code:
1. `docs/01-SRE.md`: thêm FR-044/045/046 vào danh sách FR.
2. `docs/03-functions.md`: thêm F-044/045/046 + nối vào ma trận truy vết (lưu ý file này
   hiện mới tới F-037 — F-038/039/040 của DOC-11 và F-041/042/043 của DOC-12 cũng
   chưa được thêm; nên làm 1 lần cho cả 9 function).
3. `docs/02-ERD.md`: thêm entity `invoices`, `sepay_transactions`, `daily_usage`;
   thêm field `users.emailReminderOptIn`; enum `PlanType`/`InvoiceStatus`/`SepayTransactionStatus`.
4. `docs/04-user-stories.md`: thêm US cho 3 function (gợi ý: "là learner free đã học
   20 từ hôm nay, tôi muốn thấy paywall rõ ràng thay vì lỗi khó hiểu để quyết định
   có nâng cấp không"; "là learner vừa chuyển khoản 49k với nội dung INV-XXXXXX,
   tôi muốn tài khoản tự động lên Premium trong vài phút mà không cần nhắn admin";
   "là learner hay quên ôn bài, tôi muốn nhận email nhắc lúc 8h sáng những hôm có
   từ đến hạn").

---

## 12. Acceptance criteria tổng (reviewer check)

**F-044 SePay webhook**
- [ ] Sai/thiếu `X-Webhook-Secret` → 401, DB không đổi.
- [ ] Webhook đúng: regex case-insensitive trích mã, tìm invoice PENDING (case-insensitive),
      amount ≥ invoice → fulfill đủ 5 bước (PAID + MATCHED + PREMIUM cộng dồn hạn + audit + email).
- [ ] Webhook gửi lại → duplicate, không fulfill lần 2.
- [ ] Không mã / thiếu tiền / invoice không PENDING → UNMATCHED + `note` đúng lý do.
- [ ] Không có đường nào cho client tự set PAID.
- [ ] Invoice PENDING quá 24h → EXPIRED.

**F-045 Freemium + paywall**
- [ ] Từ mới thứ 21 của FREE trong ngày → 429 `FREEMIUM_LIMIT` + `{limit, resetAt}`,
      không tạo progress.
- [ ] Ôn từ cũ không tốn hạn mức; qua 00:00 reset; PREMIUM không bị chặn;
      hết hạn Premium → tính FREE ngay.
- [ ] Race ở mốc 20 → atomic, chỉ 1 request lọt.
- [ ] FE mở PaywallModal washi (49k/390k) thay vì toast, cho cả 2 mã 429.

**F-046 Email Resend**
- [ ] Welcome sau verify OTP; payment-success sau fulfill (đúng code/plan/amount/hạn);
      reminder 8:00 đúng cron + timezone, đúng đối tượng (có từ đến hạn + opt-in).
- [ ] Thiếu ENV Resend → fail-fast startup; gửi fail → log, không rollback.
- [ ] OTP Gmail không regression.

**Chung**
- [ ] Không dependency Maven/npm mới.
- [ ] Không emoji trong code/email mới.
- [ ] Múi giờ mọi cron + `resetAt` + `date` đều Asia/Ho_Chi_Minh (qua `AppTime`, không `now()` trần).
- [ ] Mọi field FE đọc/gửi khớp DTO backend từng ký tự (chạy `api-contract-guard` sau implement).
- [ ] Bug phát hiện → log đầy đủ vào `~/workspace/bug-log/`.
