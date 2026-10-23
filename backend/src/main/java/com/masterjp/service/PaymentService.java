package com.masterjp.service;

import com.masterjp.domain.Invoice;
import com.masterjp.domain.InvoiceStatus;
import com.masterjp.domain.PlanType;
import com.masterjp.domain.SepayTransaction;
import com.masterjp.domain.SepayTransactionStatus;
import com.masterjp.domain.User;
import com.masterjp.dto.CommonDtos;
import com.masterjp.dto.PaymentDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.InvoiceRepository;
import com.masterjp.repository.SepayTransactionRepository;
import com.masterjp.repository.UserRepository;
import com.masterjp.util.AppTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Phase D — F-044: SePay webhook — tu dong kich hoat Premium.
 * <p>
 * Luong: POST /api/payments/invoices (JWT) tao hoa don PENDING + huong dan
 * chuyen khoan -> user chuyen khoan voi noi dung = ma hoa don ->
 * POST /api/payments/sepay/webhook (public, verify X-Webhook-Secret) ->
 * auto-match (idempotency -> regex ma -> tim invoice PENDING -> doi chieu tien)
 * -> fulfill (transactional) / UNMATCHED.
 * <p>
 * Webhook LUON tra 200 (tru 401 sai secret) de SePay khong retry vo han.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** Regex trich ma hoa don tu noi dung chuyen khoan — case-insensitive, lay match dau tien. */
    private static final Pattern INVOICE_CODE_PATTERN =
            Pattern.compile("INV[- ]?([A-Z0-9]{6})(?![A-Z0-9])", Pattern.CASE_INSENSITIVE);

    private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final long INVOICE_TTL_MS = 24L * 3600_000; // 24 gio

    private final SecureRandom secureRandom = new SecureRandom();

    private final InvoiceRepository invoiceRepository;
    private final SepayTransactionRepository sepayTransactionRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final AppTime appTime;
    private final PaymentFulfillmentService fulfillmentService;
    private final ResendEmailService resendEmailService;

    private final String webhookSecret;
    private final String bankName;
    private final String bankAccountNumber;
    private final String accountHolder;

    public PaymentService(InvoiceRepository invoiceRepository,
                          SepayTransactionRepository sepayTransactionRepository,
                          UserRepository userRepository,
                          MongoTemplate mongoTemplate,
                          AppTime appTime,
                          PaymentFulfillmentService fulfillmentService,
                          ResendEmailService resendEmailService,
                          @Value("${sepay.webhook-secret:}") String webhookSecret,
                          @Value("${sepay.bank-name:}") String bankName,
                          @Value("${sepay.bank-account-number:}") String bankAccountNumber,
                          @Value("${sepay.account-holder:}") String accountHolder) {
        // Fail-fast: thieu ENV bat buoc -> app KHONG boot (khong chay o che do thanh toan mo).
        requireEnv("SEPAY_WEBHOOK_SECRET", webhookSecret);
        requireEnv("SEPAY_BANK_NAME", bankName);
        requireEnv("SEPAY_BANK_ACCOUNT_NUMBER", bankAccountNumber);
        requireEnv("SEPAY_ACCOUNT_HOLDER", accountHolder);
        this.invoiceRepository = invoiceRepository;
        this.sepayTransactionRepository = sepayTransactionRepository;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.appTime = appTime;
        this.fulfillmentService = fulfillmentService;
        this.resendEmailService = resendEmailService;
        this.webhookSecret = webhookSecret.trim();
        this.bankName = bankName.trim();
        this.bankAccountNumber = bankAccountNumber.trim();
        this.accountHolder = accountHolder.trim();
    }

    private static void requireEnv(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Thieu ENV bat buoc " + name + " (F-044 SePay webhook). "
                            + "Them vao backend/.env hoac Render Dashboard -> Environment, roi khoi dong lai. "
                            + "Tu choi khoi dong thay vi chay o che do thanh toan mo.");
        }
    }

    // ================= F-044: tao / xem / huy hoa don (can JWT) =================

    /** POST /api/payments/invoices — amount backend tu set theo planType, khong tin client. */
    public PaymentDtos.InvoiceDto createInvoice(String userId, String planTypeStr) {
        PlanType planType;
        try {
            planType = PlanType.valueOf(planTypeStr == null ? "" : planTypeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException("VALIDATION_ERROR",
                    "planType chỉ nhận MONTHLY hoặc YEARLY.", HttpStatus.BAD_REQUEST);
        }

        Invoice invoice = new Invoice();
        invoice.setCode(generateInvoiceCode());
        invoice.setUserId(userId);
        invoice.setPlanType(planType);
        invoice.setAmount(planType.amountVnd());
        invoice.setStatus(InvoiceStatus.PENDING);
        invoice.setCreatedAt(new Date());
        try {
            invoiceRepository.save(invoice);
        } catch (DuplicateKeyException e) {
            // Race cuc hiem: ma trung du da check — sinh lai 1 lan.
            invoice.setCode(generateInvoiceCode());
            invoiceRepository.save(invoice);
        }
        log.info("Tao hoa don {} ({}d) cho user {}", invoice.getCode(), invoice.getAmount(), userId);
        return toDto(invoice, true);
    }

    /** GET /api/payments/invoices — chi hoa don cua chinh user, moi nhat truoc. */
    public List<PaymentDtos.InvoiceDto> listMyInvoices(String userId, String statusStr) {
        List<Invoice> invoices;
        if (statusStr == null || statusStr.isBlank()) {
            invoices = invoiceRepository.findByUserIdOrderByCreatedAtDesc(userId);
        } else {
            InvoiceStatus status;
            try {
                status = InvoiceStatus.valueOf(statusStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ApiException("VALIDATION_ERROR",
                        "status không hợp lệ.", HttpStatus.BAD_REQUEST);
            }
            invoices = invoiceRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        }
        // Lazy-check het han moi lan doc (DOC-13 muc 3.2).
        invoices.forEach(this::touchExpiry);
        return invoices.stream()
                .map(inv -> toDto(inv, inv.getStatus() == InvoiceStatus.PENDING))
                .toList();
    }

    /** DELETE /api/payments/invoices/{code} — chi huy PENDING cua chinh minh. */
    public PaymentDtos.CancelInvoiceResponse cancelInvoice(String userId, String code) {
        Invoice invoice = invoiceRepository.findByCode(normalizeCode(code))
                .orElseThrow(() -> new ApiException("NOT_FOUND",
                        "Không tìm thấy hóa đơn.", HttpStatus.NOT_FOUND));
        if (!userId.equals(invoice.getUserId())) {
            throw new ApiException("NOT_FOUND", "Không tìm thấy hóa đơn.", HttpStatus.NOT_FOUND);
        }
        touchExpiry(invoice); // PENDING qua 24h -> EXPIRED truoc khi check
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new ApiException("VALIDATION_ERROR",
                    "Chỉ hủy được hóa đơn đang chờ thanh toán.", HttpStatus.BAD_REQUEST);
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoiceRepository.save(invoice);
        log.info("User {} huy hoa don {}", userId, invoice.getCode());
        return new PaymentDtos.CancelInvoiceResponse(invoice.getCode(), "CANCELLED");
    }

    // ================= F-044: webhook SePay (public) =================

    /**
     * POST /api/payments/sepay/webhook — auto-match theo dung thu tu binding (DOC-13 muc 3.6):
     * verify secret -> idempotency -> regex ma -> tim invoice PENDING ->
     * doi chieu tien -> fulfill (transactional) / UNMATCHED.
     * Tra 200 trong moi truong hop hop le (tru 401 sai secret).
     */
    public Object handleWebhook(String providedSecret, Map<String, Object> payload) {
        verifySecret(providedSecret);

        // Body JSON "null"/rong -> payload null -> NPE o payload.get() se gay 500
        // va SePay retry vo han. Tra 200 ignored thay vi 500 (DOC-13 muc 3.7).
        if (payload == null || payload.isEmpty()) {
            log.warn("SePay webhook: body rong hoac khong phai JSON object, bo qua.");
            return new PaymentDtos.WebhookIgnoredResponse("ignored", "MISSING_PAYLOAD");
        }

        // Log raw payload o DEBUG (chua senderAccountName = PII, khong de INFO giu log production).
        // Khi UNMATCHED, unmatched() da log WARN rieng de admin doi soat.
        log.debug("SePay webhook: raw payload={}", payload);

        String transferType = str(firstNonBlank(payload.get("transferType")));
        if (!"in".equalsIgnoreCase(transferType)) {
            // Chi xu ly giao dich tien vao; tien ra bo qua, tra 200.
            return new PaymentDtos.WebhookIgnoredResponse("ignored", "NOT_TRANSFER_IN");
        }

        String bankTransactionId = str(firstNonBlank(payload.get("id"), payload.get("transactionId")));
        if (bankTransactionId == null || bankTransactionId.isBlank()) {
            log.warn("SePay webhook: thieu id giao dich, bo qua.");
            return new PaymentDtos.WebhookIgnoredResponse("ignored", "MISSING_TRANSACTION_ID");
        }

        SepayTransaction tx = new SepayTransaction();
        tx.setBankTransactionId(bankTransactionId);
        tx.setAmount(toLong(firstNonBlank(payload.get("transferAmount"), payload.get("amount"))));
        tx.setTransferContent(str(firstNonBlank(payload.get("content"), payload.get("description"))));
        tx.setTransactionDate(toDate(payload.get("transactionDate")));
        tx.setSenderAccount(str(firstNonBlank(payload.get("senderAccountName"), payload.get("senderAccount"))));
        tx.setStatus(SepayTransactionStatus.PENDING);

        // 1. Idempotency: SePay gui lai webhook cung id -> khong fulfill lan 2.
        //    Ngoai le MUST-FIX: tx cu van PENDING va da "cu" (>60s) nghia la attempt
        //    truoc crash TRUOC KHI match - retry phai thu match lai, khong duoc bo
        //    qua vinh vien (tien da vao ma hoa don mai PENDING = mat tien user).
        //    Guard 60s de khong bao gio tranh voi attempt dau dang chay
        //    (SePay retry som trong luc attempt 1 con song) - tranh double-fulfill.
        Optional<SepayTransaction> prev = sepayTransactionRepository.findByBankTransactionId(bankTransactionId);
        if (prev.isPresent()) {
            SepayTransaction p = prev.get();
            if (p.getStatus() == SepayTransactionStatus.PENDING && isStale(p)) {
                log.warn("SePay webhook: retry tren tx PENDING cu (crash lan truoc) - thu match lai: {}",
                        bankTransactionId);
                return matchAndFulfill(p);
            }
            log.info("SePay webhook duplicate: bankTransactionId={} (status={})",
                    bankTransactionId, p.getStatus());
            return new PaymentDtos.WebhookDuplicateResponse("duplicate", bankTransactionId);
        }
        try {
            sepayTransactionRepository.save(tx);
        } catch (DuplicateKeyException e) {
            // Race: 2 webhook cung id den cung luc - winner tu xu ly den cung,
            // loser chi bao duplicate (khong re-process de tranh double-fulfill).
            log.info("SePay webhook duplicate (race): bankTransactionId={}", bankTransactionId);
            return new PaymentDtos.WebhookDuplicateResponse("duplicate", bankTransactionId);
        }

        return matchAndFulfill(tx);
    }

    /**
     * Buoc 2-5 cua pipeline webhook (DOC-13 muc 3.6): regex ma -> tim invoice
     * PENDING -> doi chieu tien -> fulfill (transactional, email SAU KHI commit)
     * / UNMATCHED. Van luon tra 200 cho SePay.
     */
    private Object matchAndFulfill(SepayTransaction tx) {
        // 2. Trich ma hoa don tu noi dung chuyen khoan.
        String code = extractInvoiceCode(tx.getTransferContent());
        if (code == null) {
            return unmatched(tx, "CODE_NOT_FOUND");
        }

        // 3. Tim invoice — code DB luon uppercase, query dung uppercase (da chuan hoa o buoc 2).
        Invoice invoice = invoiceRepository.findByCode(code).orElse(null);
        if (invoice == null) {
            return unmatched(tx, "CODE_NOT_FOUND");
        }
        touchExpiry(invoice);
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            return unmatched(tx, "INVOICE_NOT_PENDING");
        }

        // 4. Doi chieu so tien — cho phep tra THUA (>=), khong yeu cau bang chinh xac.
        if (tx.getAmount() < invoice.getAmount()) {
            return unmatched(tx, "AMOUNT_MISMATCH");
        }

        // 5. Fulfill (transactional) + email SAU KHI commit.
        User user = userRepository.findById(invoice.getUserId()).orElse(null);
        if (user == null) {
            log.error("Webhook fulfill: invoice {} tro den user khong ton tai {}",
                    invoice.getCode(), invoice.getUserId());
            return unmatched(tx, "USER_NOT_FOUND");
        }
        Date newExpiry = fulfillmentService.fulfill(invoice, tx, user, invoice.getPlanType());
        log.info("Webhook fulfill: invoice {} PAID, user {} len PREMIUM den {}",
                invoice.getCode(), user.getId(), newExpiry);
        try {
            resendEmailService.sendPaymentSuccessEmail(user, invoice, newExpiry);
        } catch (Exception e) {
            // Email best-effort: user da tra tien — khong rollback fulfill vi loi mail.
            log.error("Fulfill xong nhung gui email payment-success that bai cho user {}: {}",
                    user.getId(), e.getMessage());
        }
        return new PaymentDtos.WebhookMatchedResponse("matched", invoice.getCode(),
                invoice.getPlanType().name(), isoOffset(newExpiry));
    }

    /** Job quet invoice het han: PENDING qua 24h -> EXPIRED (2:00 sang hang ngay, gio VN). */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")
    public void expirePendingInvoices() {
        Date cutoff = new Date(System.currentTimeMillis() - INVOICE_TTL_MS);
        List<Invoice> stale = invoiceRepository.findByStatusAndCreatedAtBefore(
                InvoiceStatus.PENDING, cutoff);
        for (Invoice inv : stale) {
            inv.setStatus(InvoiceStatus.EXPIRED);
        }
        if (!stale.isEmpty()) {
            invoiceRepository.saveAll(stale);
            log.info("Da danh dau EXPIRED cho {} hoa don PENDING qua 24h.", stale.size());
        }
    }

    // ================= Admin read-only =================

    /** GET /api/admin/payments/invoices — filter tuy chon, moi nhat truoc. */
    public CommonDtos.PageResponse<PaymentDtos.AdminInvoiceDto> listInvoicesAdmin(
            String status, String userId, int page, int size) {
        Criteria criteria = new Criteria();
        if (status != null && !status.isBlank()) {
            try {
                criteria.and("status").is(InvoiceStatus.valueOf(status.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ApiException("VALIDATION_ERROR",
                        "status không hợp lệ.", HttpStatus.BAD_REQUEST);
            }
        }
        if (userId != null && !userId.isBlank()) {
            criteria.and("userId").is(userId.trim());
        }
        PageRequest pageRequest = pageRequest(page, size);
        Query query = new Query(criteria).with(pageRequest);
        List<Invoice> items = mongoTemplate.find(query, Invoice.class);
        long total = mongoTemplate.count(new Query(criteria), Invoice.class);

        Map<String, String> emailByUserId = userRepository.findAllById(
                        items.stream().map(Invoice::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getEmail, (a, b) -> a));

        List<PaymentDtos.AdminInvoiceDto> dtos = items.stream().map(inv ->
                new PaymentDtos.AdminInvoiceDto(
                        inv.getId(), inv.getCode(), inv.getUserId(),
                        emailByUserId.get(inv.getUserId()),
                        inv.getPlanType().name(), inv.getAmount(), inv.getStatus().name(),
                        isoOffset(inv.getCreatedAt()), isoOffset(inv.getPaidAt()),
                        inv.getSepayTransactionId()))
                .toList();
        return new CommonDtos.PageResponse<>(dtos, total, pageRequest.getPageNumber(),
                pageRequest.getPageSize());
    }

    /** GET /api/admin/payments/sepay/transactions — filter tuy chon, moi nhat truoc. */
    public CommonDtos.PageResponse<PaymentDtos.AdminSepayTransactionDto> listTransactionsAdmin(
            String status, int page, int size) {
        Criteria criteria = new Criteria();
        if (status != null && !status.isBlank()) {
            try {
                criteria.and("status").is(
                        SepayTransactionStatus.valueOf(status.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ApiException("VALIDATION_ERROR",
                        "status không hợp lệ.", HttpStatus.BAD_REQUEST);
            }
        }
        PageRequest pageRequest = pageRequest(page, size);
        Query query = new Query(criteria).with(pageRequest);
        List<SepayTransaction> items = mongoTemplate.find(query, SepayTransaction.class);
        long total = mongoTemplate.count(new Query(criteria), SepayTransaction.class);

        List<PaymentDtos.AdminSepayTransactionDto> dtos = items.stream().map(tx ->
                new PaymentDtos.AdminSepayTransactionDto(
                        tx.getId(), tx.getBankTransactionId(), tx.getAmount(),
                        tx.getTransferContent(), isoOffset(tx.getTransactionDate()),
                        tx.getSenderAccount(), tx.getStatus().name(),
                        tx.getMatchedInvoiceId(), tx.getNote(), isoOffset(tx.getCreatedAt())))
                .toList();
        return new CommonDtos.PageResponse<>(dtos, total, pageRequest.getPageNumber(),
                pageRequest.getPageSize());
    }

    // ================= Helpers =================

    /** So sanh constant-time; khong log gia tri secret nhan duoc. */
    private void verifySecret(String provided) {
        boolean ok = provided != null && MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            throw new ApiException("WEBHOOK_UNAUTHORIZED",
                    "Webhook secret không hợp lệ.", HttpStatus.UNAUTHORIZED);
        }
    }

    /** Sinh ma INV-XXXXXX bang SecureRandom, kiem tra trung truoc insert. */
    private String generateInvoiceCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder("INV-");
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!invoiceRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new ApiException("INTERNAL_ERROR",
                "Không tạo được mã hóa đơn, vui lòng thử lại sau.",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** Regex INV[- ]?([A-Z0-9]{6}) case-insensitive, match dau tien -> uppercase.
     *  (?![A-Z0-9]): tu choi khi sau 6 ky tu con tiep 1 ky tu alnum (tranh match nham
     *  hoa don khac khi user go thua ky tu, vd. INV-9F3A2B99). */
    private String extractInvoiceCode(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = INVOICE_CODE_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        return "INV-" + matcher.group(1).toUpperCase();
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    /** Lazy-check 24h moi lan invoice duoc doc/match (DOC-13 muc 3.2). */
    private void touchExpiry(Invoice invoice) {
        if (invoice.getStatus() == InvoiceStatus.PENDING
                && System.currentTimeMillis() - invoice.getCreatedAt().getTime() > INVOICE_TTL_MS) {
            invoice.setStatus(InvoiceStatus.EXPIRED);
            invoiceRepository.save(invoice);
        }
    }

    /**
     * Tx PENDING tao tu >60s truoc ma chua match = attempt truoc da crash
     * (chu khong phai dang chay) - an toan de retry match lai.
     */
    private static boolean isStale(SepayTransaction tx) {
        Date createdAt = tx.getCreatedAt();
        return createdAt != null && System.currentTimeMillis() - createdAt.getTime() > 60_000L;
    }

    private PaymentDtos.WebhookUnmatchedResponse unmatched(SepayTransaction tx, String reason) {
        tx.setStatus(SepayTransactionStatus.UNMATCHED);
        tx.setNote(reason);
        sepayTransactionRepository.save(tx);
        // Tien da vao tai khoan nhung khong match duoc — can xu ly tay (hoan tien /
        // huong dan user). Log WARN de khong chim trong log INFO (audit F-044).
        log.warn("SePay webhook UNMATCHED: bankTransactionId={}, reason={}, amount={}, content='{}'",
                tx.getBankTransactionId(), reason, tx.getAmount(), tx.getTransferContent());
        return new PaymentDtos.WebhookUnmatchedResponse("unmatched", reason);
    }

    private PaymentDtos.InvoiceDto toDto(Invoice invoice, boolean includeInstructions) {
        Date createdAt = invoice.getCreatedAt();
        String expiresAt = createdAt == null ? null
                : isoOffset(new Date(createdAt.getTime() + INVOICE_TTL_MS));
        PaymentDtos.TransferInstructions instructions = includeInstructions
                ? new PaymentDtos.TransferInstructions(
                        bankName, bankAccountNumber, accountHolder,
                        invoice.getAmount(), invoice.getCode(),
                        "Vui lòng ghi đúng nội dung chuyển khoản để hệ thống tự động kích hoạt.")
                : null;
        return new PaymentDtos.InvoiceDto(
                invoice.getCode(), invoice.getPlanType().name(), invoice.getAmount(),
                invoice.getStatus().name(), isoOffset(createdAt), expiresAt, instructions);
    }

    private PageRequest pageRequest(int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 100);
        return PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /** ISO-8601 co offset theo Asia/Ho_Chi_Minh (qua AppTime, khong now() tran). */
    private String isoOffset(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(appTime.zoneId())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    // ---------- Parse payload phong thu (field name SePay thuc te chua xac minh) ----------

    private static Object firstNonBlank(Object... candidates) {
        for (Object c : candidates) {
            if (c != null && !String.valueOf(c).isBlank()) {
                return c;
            }
        }
        return null;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }

    private static long toLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v != null) {
            try {
                return (long) Double.parseDouble(String.valueOf(v).trim());
            } catch (NumberFormatException ignored) {
                // roi ve 0 -> se UNMATCHED o buoc doi chieu tien
            }
        }
        return 0L;
    }

    private static Date toDate(Object v) {
        if (v instanceof Number n) {
            return new Date(n.longValue());
        }
        if (v != null) {
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) {
                try {
                    return new Date(Long.parseLong(s)); // epoch millis
                } catch (NumberFormatException ignored) {
                }
                try {
                    return Date.from(OffsetDateTime.parse(s).toInstant()); // ISO-8601
                } catch (Exception ignored) {
                }
                try {
                    return Date.from(LocalDateTime.parse(s)
                            .atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());
                } catch (Exception ignored) {
                }
            }
        }
        return new Date();
    }
}
