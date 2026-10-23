package com.masterjp.service;

import com.masterjp.domain.Invoice;
import com.masterjp.domain.InvoiceStatus;
import com.masterjp.domain.PlanType;
import com.masterjp.domain.SepayTransaction;
import com.masterjp.domain.User;
import com.masterjp.dto.PaymentDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.InvoiceRepository;
import com.masterjp.repository.SepayTransactionRepository;
import com.masterjp.repository.UserRepository;
import com.masterjp.util.AppTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test cho PaymentService (Phase D — F-044 SePay auto-match engine).
 * Mock repositories + HTTP — KHONG goi SePay that, KHONG boot Spring context.
 * Chay bang: mvn test -Dtest=PaymentServiceTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService — SePay auto-match + sinh mã hóa đơn")
class PaymentServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private SepayTransactionRepository sepayTransactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private AppTime appTime;
    @Mock
    private PaymentFulfillmentService fulfillmentService;
    @Mock
    private ResendEmailService resendEmailService;

    private PaymentService newService() {
        // zoneId chỉ dùng ở nhánh match/create thành công -> lenient cho các test 401/ignored/duplicate
        lenient().when(appTime.zoneId()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        return new PaymentService(invoiceRepository, sepayTransactionRepository, userRepository,
                mongoTemplate, appTime, fulfillmentService, resendEmailService,
                "s3cr3t-webhook", "Vietcombank", "0011001234567", "MASTER JP");
    }

    /** Payload webhook SePay toi thieu cho 1 giao dich tien vao. */
    private Map<String, Object> inPayload(String txId, long amount, String content) {
        Map<String, Object> p = new HashMap<>();
        p.put("transferType", "in");
        p.put("id", txId);
        p.put("transferAmount", amount);
        p.put("content", content);
        return p;
    }

    private Invoice pendingInvoice(String code, long amount) {
        Invoice inv = new Invoice();
        inv.setId("inv-1");
        inv.setCode(code);
        inv.setUserId("u1");
        inv.setPlanType(PlanType.MONTHLY);
        inv.setAmount(amount);
        inv.setStatus(InvoiceStatus.PENDING);
        inv.setCreatedAt(new Date());
        return inv;
    }

    private User user() {
        User u = new User();
        u.setId("u1");
        u.setEmail("user@example.com");
        return u;
    }

    // ================= Webhook: verify secret =================

    @Test
    @DisplayName("Webhook secret SAI -> 401 WEBHOOK_UNAUTHORIZED, zero DB write")
    void webhook_secretSai_nem401_khongChamDb() {
        PaymentService svc = newService();

        ApiException e = assertThrows(ApiException.class,
                () -> svc.handleWebhook("sai-secret", inPayload("TX-1", 49_000L, "INV-ABC123")));

        assertEquals("WEBHOOK_UNAUTHORIZED", e.getCode());
        assertEquals(401, e.getStatus().value());
        verifyNoInteractions(sepayTransactionRepository, invoiceRepository, userRepository,
                fulfillmentService, resendEmailService);
    }

    @Test
    @DisplayName("Webhook THIẾU secret (null) -> 401, zero DB write")
    void webhook_thieuSecret_nem401_khongChamDb() {
        PaymentService svc = newService();

        ApiException e = assertThrows(ApiException.class,
                () -> svc.handleWebhook(null, inPayload("TX-1", 49_000L, "INV-ABC123")));

        assertEquals("WEBHOOK_UNAUTHORIZED", e.getCode());
        assertEquals(401, e.getStatus().value());
        verifyNoInteractions(sepayTransactionRepository, invoiceRepository, userRepository,
                fulfillmentService, resendEmailService);
    }

    // ================= Webhook: ignored =================

    @Test
    @DisplayName("Payload null hoặc empty -> ignored 200 MISSING_PAYLOAD, zero DB write")
    void webhook_payloadNullHoacEmpty_biBoQua_200() {
        PaymentService svc = newService();

        Object r1 = svc.handleWebhook("s3cr3t-webhook", null);
        PaymentDtos.WebhookIgnoredResponse i1 = (PaymentDtos.WebhookIgnoredResponse) r1;
        assertEquals("ignored", i1.status());
        assertEquals("MISSING_PAYLOAD", i1.reason());

        Object r2 = svc.handleWebhook("s3cr3t-webhook", new HashMap<>());
        PaymentDtos.WebhookIgnoredResponse i2 = (PaymentDtos.WebhookIgnoredResponse) r2;
        assertEquals("ignored", i2.status());
        assertEquals("MISSING_PAYLOAD", i2.reason());

        // Secret vẫn được verify trước; payload rỗng thì không chạm DB/fulfill/mail
        verifyNoInteractions(sepayTransactionRepository, invoiceRepository, userRepository,
                fulfillmentService, resendEmailService);
    }

    @Test
    @DisplayName("transferType != 'in' -> ignored 200, không lưu transaction")
    void webhook_transferTypeKhacIn_biBoQua_200() {
        PaymentService svc = newService();
        Map<String, Object> p = inPayload("TX-1", 49_000L, "INV-ABC123");
        p.put("transferType", "out");

        Object resp = svc.handleWebhook("s3cr3t-webhook", p);

        PaymentDtos.WebhookIgnoredResponse r = (PaymentDtos.WebhookIgnoredResponse) resp;
        assertEquals("ignored", r.status());
        assertEquals("NOT_TRANSFER_IN", r.reason());
        verify(sepayTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Thiếu transaction id -> ignored MISSING_TRANSACTION_ID, không lưu DB")
    void webhook_thieuTransactionId_biBoQua() {
        PaymentService svc = newService();
        Map<String, Object> p = inPayload(null, 49_000L, "INV-ABC123");

        Object resp = svc.handleWebhook("s3cr3t-webhook", p);

        PaymentDtos.WebhookIgnoredResponse r = (PaymentDtos.WebhookIgnoredResponse) resp;
        assertEquals("ignored", r.status());
        assertEquals("MISSING_TRANSACTION_ID", r.reason());
        verify(sepayTransactionRepository, never()).save(any());
    }

    // ================= Webhook: happy path =================

    @Test
    @DisplayName("Match thành công: regex case-insensitive + space, fulfill + email, 200 matched")
    void webhook_matchThanhCong_fulfillVaGuiEmail() {
        PaymentService svc = newService();
        // "inv abc123" (chữ thường, dấu cách) -> phải trích được INV-ABC123
        Map<String, Object> p = inPayload("TX-001", 49_000L, "chuyen khoan inv abc123 mua goi");
        when(sepayTransactionRepository.findByBankTransactionId("TX-001"))
                .thenReturn(Optional.empty());
        Invoice inv = pendingInvoice("INV-ABC123", 49_000L);
        when(invoiceRepository.findByCode("INV-ABC123")).thenReturn(Optional.of(inv));
        User u = user();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        Date expiry = new Date(1_780_000_000_000L); // 2026-05-29T03:26:40+07:00
        when(fulfillmentService.fulfill(eq(inv), any(SepayTransaction.class), eq(u),
                eq(PlanType.MONTHLY)))
                .thenReturn(expiry);

        Object resp = svc.handleWebhook("s3cr3t-webhook", p);

        PaymentDtos.WebhookMatchedResponse r = (PaymentDtos.WebhookMatchedResponse) resp;
        assertEquals("matched", r.status());
        assertEquals("INV-ABC123", r.invoiceCode());
        assertEquals("MONTHLY", r.planType());
        assertTrue(r.planExpiresAt().startsWith("2026-05-29"),
                "planExpiresAt phải ISO-8601 theo Asia/Ho_Chi_Minh, nhận được: " + r.planExpiresAt());
        assertTrue(r.planExpiresAt().contains("+07:00"));
        verify(fulfillmentService).fulfill(any(Invoice.class), any(SepayTransaction.class),
                any(User.class), org.mockito.ArgumentMatchers.eq(PlanType.MONTHLY));
        verify(resendEmailService).sendPaymentSuccessEmail(u, inv, expiry);
    }

    @Test
    @DisplayName("Khách trả THỪA tiền (amount > invoice) vẫn match — rule >= ")
    void webhook_traThuaTien_vanMatch() {
        PaymentService svc = newService();
        Map<String, Object> p = inPayload("TX-002", 100_000L, "INV-ABC123");
        when(sepayTransactionRepository.findByBankTransactionId("TX-002"))
                .thenReturn(Optional.empty());
        Invoice inv = pendingInvoice("INV-ABC123", 49_000L);
        when(invoiceRepository.findByCode("INV-ABC123")).thenReturn(Optional.of(inv));
        when(userRepository.findById("u1")).thenReturn(Optional.of(user()));
        when(fulfillmentService.fulfill(any(), any(), any(), any())).thenReturn(new Date());

        Object resp = svc.handleWebhook("s3cr3t-webhook", p);

        assertEquals("matched", ((PaymentDtos.WebhookMatchedResponse) resp).status());
        verify(fulfillmentService).fulfill(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Mã INV-xxxxxx dính liền (không dấu cách/gạch) vẫn trích được")
    void webhook_maDinhLien_vanMatch() {
        PaymentService svc = newService();
        Map<String, Object> p = inPayload("TX-003", 49_000L, "TT INVABC123");
        when(sepayTransactionRepository.findByBankTransactionId("TX-003"))
                .thenReturn(Optional.empty());
        Invoice inv = pendingInvoice("INV-ABC123", 49_000L);
        when(invoiceRepository.findByCode("INV-ABC123")).thenReturn(Optional.of(inv));
        when(userRepository.findById("u1")).thenReturn(Optional.of(user()));
        when(fulfillmentService.fulfill(any(), any(), any(), any())).thenReturn(new Date());

        Object resp = svc.handleWebhook("s3cr3t-webhook", p);

        assertEquals("matched", ((PaymentDtos.WebhookMatchedResponse) resp).status());
        // verify query dùng code đã chuẩn hóa uppercase
        verify(invoiceRepository).findByCode("INV-ABC123");
    }

    // ================= Webhook: UNMATCHED =================

    private SepayTransaction lastSavedTx() {
        ArgumentCaptor<SepayTransaction> captor = ArgumentCaptor.forClass(SepayTransaction.class);
        verify(sepayTransactionRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues().get(captor.getAllValues().size() - 1);
    }

    @Test
    @DisplayName("Nội dung không chứa mã -> UNMATCHED CODE_NOT_FOUND, không fulfill")
    void webhook_noiDungKhongChuaMa_unmatched() {
        PaymentService svc = newService();
        when(sepayTransactionRepository.findByBankTransactionId("TX-1"))
                .thenReturn(Optional.empty());

        Object resp = svc.handleWebhook("s3cr3t-webhook",
                inPayload("TX-1", 49_000L, "nap tien khong ghi ma"));

        PaymentDtos.WebhookUnmatchedResponse r = (PaymentDtos.WebhookUnmatchedResponse) resp;
        assertEquals("unmatched", r.status());
        assertEquals("CODE_NOT_FOUND", r.reason());
        SepayTransaction tx = lastSavedTx();
        assertEquals(com.masterjp.domain.SepayTransactionStatus.UNMATCHED, tx.getStatus());
        assertEquals("CODE_NOT_FOUND", tx.getNote());
        verify(fulfillmentService, never()).fulfill(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Mã có trong nội dung nhưng invoice không tồn tại -> CODE_NOT_FOUND")
    void webhook_maKhongTonTai_unmatched() {
        PaymentService svc = newService();
        when(sepayTransactionRepository.findByBankTransactionId("TX-1"))
                .thenReturn(Optional.empty());
        when(invoiceRepository.findByCode("INV-ZZZ999")).thenReturn(Optional.empty());

        Object resp = svc.handleWebhook("s3cr3t-webhook",
                inPayload("TX-1", 49_000L, "TT INV-ZZZ999"));

        assertEquals("CODE_NOT_FOUND",
                ((PaymentDtos.WebhookUnmatchedResponse) resp).reason());
        verify(fulfillmentService, never()).fulfill(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Invoice đã PAID (không còn PENDING) -> UNMATCHED INVOICE_NOT_PENDING")
    void webhook_invoiceDaPaid_unmatched() {
        PaymentService svc = newService();
        when(sepayTransactionRepository.findByBankTransactionId("TX-1"))
                .thenReturn(Optional.empty());
        Invoice inv = pendingInvoice("INV-ABC123", 49_000L);
        inv.setStatus(InvoiceStatus.PAID);
        when(invoiceRepository.findByCode("INV-ABC123")).thenReturn(Optional.of(inv));

        Object resp = svc.handleWebhook("s3cr3t-webhook",
                inPayload("TX-1", 49_000L, "INV-ABC123"));

        assertEquals("INVOICE_NOT_PENDING",
                ((PaymentDtos.WebhookUnmatchedResponse) resp).reason());
        verify(fulfillmentService, never()).fulfill(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Thiếu tiền (amount < invoice) -> UNMATCHED AMOUNT_MISMATCH")
    void webhook_thieuTien_unmatched() {
        PaymentService svc = newService();
        when(sepayTransactionRepository.findByBankTransactionId("TX-1"))
                .thenReturn(Optional.empty());
        when(invoiceRepository.findByCode("INV-ABC123"))
                .thenReturn(Optional.of(pendingInvoice("INV-ABC123", 49_000L)));

        Object resp = svc.handleWebhook("s3cr3t-webhook",
                inPayload("TX-1", 48_999L, "INV-ABC123"));

        assertEquals("AMOUNT_MISMATCH",
                ((PaymentDtos.WebhookUnmatchedResponse) resp).reason());
        SepayTransaction tx = lastSavedTx();
        assertEquals("AMOUNT_MISMATCH", tx.getNote());
        verify(fulfillmentService, never()).fulfill(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Invoice PENDING quá 24h -> lazy EXPIRED khi match -> INVOICE_NOT_PENDING")
    void webhook_invoiceHetHan24h_unmatched() {
        PaymentService svc = newService();
        when(sepayTransactionRepository.findByBankTransactionId("TX-1"))
                .thenReturn(Optional.empty());
        Invoice inv = pendingInvoice("INV-ABC123", 49_000L);
        inv.setCreatedAt(new Date(System.currentTimeMillis() - 25L * 3600_000));
        when(invoiceRepository.findByCode("INV-ABC123")).thenReturn(Optional.of(inv));

        Object resp = svc.handleWebhook("s3cr3t-webhook",
                inPayload("TX-1", 49_000L, "INV-ABC123"));

        assertEquals("INVOICE_NOT_PENDING",
                ((PaymentDtos.WebhookUnmatchedResponse) resp).reason());
        assertEquals(InvoiceStatus.EXPIRED, inv.getStatus());
        verify(fulfillmentService, never()).fulfill(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Invoice trỏ đến user không tồn tại -> UNMATCHED USER_NOT_FOUND")
    void webhook_userKhongTonTai_unmatched() {
        PaymentService svc = newService();
        when(sepayTransactionRepository.findByBankTransactionId("TX-1"))
                .thenReturn(Optional.empty());
        when(invoiceRepository.findByCode("INV-ABC123"))
                .thenReturn(Optional.of(pendingInvoice("INV-ABC123", 49_000L)));
        when(userRepository.findById("u1")).thenReturn(Optional.empty());

        Object resp = svc.handleWebhook("s3cr3t-webhook",
                inPayload("TX-1", 49_000L, "INV-ABC123"));

        assertEquals("USER_NOT_FOUND",
                ((PaymentDtos.WebhookUnmatchedResponse) resp).reason());
        verify(fulfillmentService, never()).fulfill(any(), any(), any(), any());
    }

    // ================= Webhook: idempotency =================

    @Test
    @DisplayName("Webhook gửi lại cùng bankTransactionId (tx đã xử lý) -> duplicate 200, KHÔNG fulfill lần 2")
    void webhook_guiLaiTrungId_khongFulfillLan2() {
        PaymentService svc = newService();
        SepayTransaction existing = new SepayTransaction();
        existing.setBankTransactionId("TX-DUP");
        // Đặt status đã xử lý: duplicate-check chỉ bỏ qua tx PENDING (resume sau crash);
        // tx đã MATCHED/UNMATCHED gửi lại -> duplicate, không fulfill lần 2.
        existing.setStatus(com.masterjp.domain.SepayTransactionStatus.MATCHED);
        when(sepayTransactionRepository.findByBankTransactionId("TX-DUP"))
                .thenReturn(Optional.of(existing));

        Object resp = svc.handleWebhook("s3cr3t-webhook",
                inPayload("TX-DUP", 49_000L, "INV-ABC123"));

        PaymentDtos.WebhookDuplicateResponse r = (PaymentDtos.WebhookDuplicateResponse) resp;
        assertEquals("duplicate", r.status());
        assertEquals("TX-DUP", r.bankTransactionId());
        verify(sepayTransactionRepository, never()).save(any());
        verify(fulfillmentService, never()).fulfill(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Race: 2 webhook cùng id đến cùng lúc, save ném DuplicateKey -> duplicate 200")
    void webhook_raceDuplicate_saveNemDuplicateKey_duplicate() {
        PaymentService svc = newService();
        when(sepayTransactionRepository.findByBankTransactionId("TX-RACE"))
                .thenReturn(Optional.empty());
        when(sepayTransactionRepository.save(any(SepayTransaction.class)))
                .thenThrow(new DuplicateKeyException("E11000 duplicate key"));

        Object resp = svc.handleWebhook("s3cr3t-webhook",
                inPayload("TX-RACE", 49_000L, "INV-ABC123"));

        assertEquals("duplicate", ((PaymentDtos.WebhookDuplicateResponse) resp).status());
        verify(fulfillmentService, never()).fulfill(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Webhook gửi lại: tx PENDING cũ (>60s, attempt trước crash) -> resume matchAndFulfill, fulfill (KHÔNG 'duplicate')")
    void webhook_duplicateTxPendingCu_resumeMatchVaFulfill() {
        PaymentService svc = newService();
        // Tx PENDING tạo 61s trước = attempt trước đã crash TRƯỚC KHI match —
        // retry phải thu match lại, không được báo duplicate bỏ qua vĩnh viễn.
        SepayTransaction stale = new SepayTransaction();
        stale.setBankTransactionId("TX-CRASH");
        stale.setStatus(com.masterjp.domain.SepayTransactionStatus.PENDING);
        stale.setCreatedAt(new Date(System.currentTimeMillis() - 61_000L));
        stale.setAmount(49_000L);
        stale.setTransferContent("TT INV-ABC123");
        when(sepayTransactionRepository.findByBankTransactionId("TX-CRASH"))
                .thenReturn(Optional.of(stale));
        Invoice inv = pendingInvoice("INV-ABC123", 49_000L);
        when(invoiceRepository.findByCode("INV-ABC123")).thenReturn(Optional.of(inv));
        User u = user();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        Date expiry = new Date(1_780_000_000_000L);
        when(fulfillmentService.fulfill(eq(inv), any(SepayTransaction.class), eq(u),
                eq(PlanType.MONTHLY)))
                .thenReturn(expiry);

        Object resp = svc.handleWebhook("s3cr3t-webhook",
                inPayload("TX-CRASH", 49_000L, "TT INV-ABC123"));

        PaymentDtos.WebhookMatchedResponse r = (PaymentDtos.WebhookMatchedResponse) resp;
        assertEquals("matched", r.status(), "tx PENDING stale phải được match lại, không trả 'duplicate'");
        assertEquals("INV-ABC123", r.invoiceCode());
        // Cùng tx cũ được đưa qua matchAndFulfill -> fulfill đúng 1 lần, không lưu tx mới
        verify(fulfillmentService).fulfill(eq(inv), eq(stale), eq(u), eq(PlanType.MONTHLY));
        verify(sepayTransactionRepository, never()).save(any());
        verify(resendEmailService).sendPaymentSuccessEmail(u, inv, expiry);
    }

    // ================= createInvoice + sinh mã =================

    @Test
    @DisplayName("createInvoice MONTHLY: mã INV-XXXXXX, amount 49_000 do backend set")
    void createInvoice_monthly_maDungFormat_amount49000() {
        PaymentService svc = newService();
        when(invoiceRepository.existsByCode(anyString())).thenReturn(false);

        PaymentDtos.InvoiceDto dto = svc.createInvoice("u1", "monthly"); // lowercase vẫn nhận

        assertTrue(dto.code().matches("^INV-[A-Z0-9]{6}$"),
                "Mã phải format INV-XXXXXX (6 ký tự uppercase alnum), nhận được: " + dto.code());
        assertEquals(49_000L, dto.amount());
        assertEquals("MONTHLY", dto.planType());
        assertEquals("PENDING", dto.status());
        assertNotNull(dto.transferInstructions());
        assertEquals(dto.code(), dto.transferInstructions().transferContent());
    }

    @Test
    @DisplayName("createInvoice YEARLY: amount 390_000")
    void createInvoice_yearly_amount390000() {
        PaymentService svc = newService();
        when(invoiceRepository.existsByCode(anyString())).thenReturn(false);

        PaymentDtos.InvoiceDto dto = svc.createInvoice("u1", "YEARLY");

        assertEquals(390_000L, dto.amount());
        assertEquals("YEARLY", dto.planType());
    }

    @Test
    @DisplayName("createInvoice planType lạ -> 400 VALIDATION_ERROR")
    void createInvoice_planTypeKhongHopLe_400() {
        PaymentService svc = newService();

        ApiException e = assertThrows(ApiException.class,
                () -> svc.createInvoice("u1", "WEEKLY"));

        assertEquals("VALIDATION_ERROR", e.getCode());
        assertEquals(400, e.getStatus().value());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Mã bị trùng (existsByCode) -> sinh lại mã mới, không trùng mã cũ")
    void createInvoice_maTrung_sinhLaiMaMoi() {
        PaymentService svc = newService();
        // 2 lần đầu trùng, lần 3 OK
        when(invoiceRepository.existsByCode(anyString())).thenReturn(true, true, false);

        PaymentDtos.InvoiceDto dto = svc.createInvoice("u1", "MONTHLY");

        assertTrue(dto.code().matches("^INV-[A-Z0-9]{6}$"));
        verify(invoiceRepository, org.mockito.Mockito.times(3)).existsByCode(anyString());
        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        assertEquals(dto.code(), captor.getValue().getCode());
    }

    @Test
    @DisplayName("Trùng mã 20 lần liên tiếp -> 500 INTERNAL_ERROR, không save")
    void generateInvoiceCode_het20LanNemInternalError() {
        PaymentService svc = newService();
        when(invoiceRepository.existsByCode(anyString())).thenReturn(true);

        ApiException e = assertThrows(ApiException.class,
                () -> svc.createInvoice("u1", "MONTHLY"));

        assertEquals("INTERNAL_ERROR", e.getCode());
        assertEquals(500, e.getStatus().value());
        verify(invoiceRepository, org.mockito.Mockito.times(20)).existsByCode(anyString());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Hai hóa đơn liên tiếp có mã khác nhau (SecureRandom)")
    void createInvoice_haiLanMaKhacNhau() {
        PaymentService svc = newService();
        when(invoiceRepository.existsByCode(anyString())).thenReturn(false);

        String c1 = svc.createInvoice("u1", "MONTHLY").code();
        String c2 = svc.createInvoice("u1", "MONTHLY").code();

        assertNotEquals(c1, c2, "SecureRandom phải sinh mã khác nhau mỗi lần");
    }

    // ================= cancelInvoice (liên quan TTL, regression) =================

    @Test
    @DisplayName("cancelInvoice: chỉ chủ hóa đơn PENDING mới hủy được")
    void cancelInvoice_chuSoHuuPending_huyDuoc() {
        PaymentService svc = newService();
        Invoice inv = pendingInvoice("INV-ABC123", 49_000L);
        when(invoiceRepository.findByCode("INV-ABC123")).thenReturn(Optional.of(inv));

        PaymentDtos.CancelInvoiceResponse resp = svc.cancelInvoice("u1", "inv-abc123");

        assertEquals("INV-ABC123", resp.code());
        assertEquals("CANCELLED", resp.status());
        assertEquals(InvoiceStatus.CANCELLED, inv.getStatus());
    }

    @Test
    @DisplayName("cancelInvoice của user khác -> 404 (không lộ tồn tại)")
    void cancelInvoice_cuaUserKhac_404() {
        PaymentService svc = newService();
        Invoice inv = pendingInvoice("INV-ABC123", 49_000L);
        when(invoiceRepository.findByCode("INV-ABC123")).thenReturn(Optional.of(inv));

        ApiException e = assertThrows(ApiException.class,
                () -> svc.cancelInvoice("u2", "INV-ABC123"));

        assertEquals("NOT_FOUND", e.getCode());
        assertEquals(404, e.getStatus().value());
    }
}
