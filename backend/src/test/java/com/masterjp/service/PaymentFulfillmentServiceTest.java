package com.masterjp.service;

import com.masterjp.domain.Invoice;
import com.masterjp.domain.InvoiceStatus;
import com.masterjp.domain.Plan;
import com.masterjp.domain.PlanType;
import com.masterjp.domain.SepayTransaction;
import com.masterjp.domain.SepayTransactionStatus;
import com.masterjp.domain.User;
import com.masterjp.repository.InvoiceRepository;
import com.masterjp.repository.SepayTransactionRepository;
import com.masterjp.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

/**
 * Unit test cho PaymentFulfillmentService (Phase D — F-044).
 * Không boot Spring context — @Transactional ở đây chỉ là annotation,
 * test verify 4 bước fulfill đúng thứ tự nội dung (transaction thật do MongoDB đảm nhận).
 * Chay bang: mvn test -Dtest=PaymentFulfillmentServiceTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentFulfillmentService — fulfill invoice -> Premium")
class PaymentFulfillmentServiceTest {

    private static final long DAY_MS = 24L * 3600_000;

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private SepayTransactionRepository sepayTransactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private PaymentFulfillmentService fulfillmentService;

    private Invoice pendingInvoice() {
        Invoice inv = new Invoice();
        inv.setId("inv-1");
        inv.setCode("INV-ABC123");
        inv.setUserId("u1");
        inv.setPlanType(PlanType.MONTHLY);
        inv.setAmount(49_000L);
        inv.setStatus(InvoiceStatus.PENDING);
        inv.setCreatedAt(new Date());
        return inv;
    }

    private SepayTransaction pendingTx() {
        SepayTransaction tx = new SepayTransaction();
        tx.setId("tx-1");
        tx.setBankTransactionId("TX-001");
        tx.setAmount(49_000L);
        tx.setStatus(SepayTransactionStatus.PENDING);
        return tx;
    }

    private User freeUser() {
        User u = new User();
        u.setId("u1");
        u.setEmail("user@example.com");
        u.setPlan(Plan.FREE);
        return u;
    }

    @Test
    @DisplayName("Fulfill đủ 4 bước: invoice PAID, tx MATCHED, user PREMIUM +30 ngày, audit log")
    void fulfill_freeUserMonthly_du4Buoc() {
        Invoice inv = pendingInvoice();
        SepayTransaction tx = pendingTx();
        User user = freeUser();
        long before = System.currentTimeMillis();

        Date newExpiry = fulfillmentService.fulfill(inv, tx, user, PlanType.MONTHLY);

        // 1. Invoice -> PAID
        assertEquals(InvoiceStatus.PAID, inv.getStatus());
        assertNotNull(inv.getPaidAt());
        assertEquals("TX-001", inv.getSepayTransactionId());
        verify(invoiceRepository).save(inv);

        // 2. Transaction -> MATCHED
        assertEquals(SepayTransactionStatus.MATCHED, tx.getStatus());
        assertEquals("inv-1", tx.getMatchedInvoiceId());
        verify(sepayTransactionRepository).save(tx);

        // 3. User -> PREMIUM, hạn = now + 30 ngày (user FREE trước đó)
        assertEquals(Plan.PREMIUM, user.getPlan());
        assertNotNull(user.getPlanExpiresAt());
        long delta = user.getPlanExpiresAt().getTime() - before;
        assertTrue(delta >= 30 * DAY_MS - 60_000 && delta <= 30 * DAY_MS + 60_000,
                "PREMIUM phải hết hạn sau ~30 ngày, delta=" + delta);
        assertEquals(newExpiry, user.getPlanExpiresAt());
        verify(userRepository).save(user);

        // 4. Audit log PAYMENT_FULFILLED (adminId null — hệ thống tự động)
        ArgumentCaptor<Map> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).log(isNull(), eq("PAYMENT_FULFILLED"), eq("Invoice"),
                eq("inv-1"), detailsCaptor.capture());
        Map<String, Object> details = detailsCaptor.getValue();
        assertEquals("INV-ABC123", details.get("code"));
        assertEquals("MONTHLY", details.get("planType"));
        assertEquals(49_000L, details.get("amount"));
        assertEquals("TX-001", details.get("bankTransactionId"));
        assertNotNull(details.get("newPlanExpiresAt"));
    }

    @Test
    @DisplayName("YEARLY: cộng 365 ngày")
    void fulfill_yearly_cong365Ngay() {
        Invoice inv = pendingInvoice();
        inv.setPlanType(PlanType.YEARLY);
        inv.setAmount(390_000L);
        SepayTransaction tx = pendingTx();
        User user = freeUser();
        long before = System.currentTimeMillis();

        fulfillmentService.fulfill(inv, tx, user, PlanType.YEARLY);

        long delta = user.getPlanExpiresAt().getTime() - before;
        assertTrue(delta >= 365 * DAY_MS - 60_000 && delta <= 365 * DAY_MS + 60_000,
                "YEARLY phải cộng 365 ngày, delta=" + delta);
    }

    @Test
    @DisplayName("Premium còn hạn: cộng dồn từ hạn cũ (không mất ngày đã trả)")
    void fulfill_premiumConHan_congDonTuHanCu() {
        Invoice inv = pendingInvoice();
        SepayTransaction tx = pendingTx();
        User user = freeUser();
        user.setPlan(Plan.PREMIUM);
        Date oldExpiry = new Date(System.currentTimeMillis() + 10 * DAY_MS);
        user.setPlanExpiresAt(oldExpiry);

        Date newExpiry = fulfillmentService.fulfill(inv, tx, user, PlanType.MONTHLY);

        long delta = newExpiry.getTime() - oldExpiry.getTime();
        assertTrue(delta >= 30 * DAY_MS - 60_000 && delta <= 30 * DAY_MS + 60_000,
                "Phải cộng 30 ngày TỪ HẠN CŨ (10 ngày còn lại không được mất), delta=" + delta);
    }

    @Test
    @DisplayName("Premium đã hết hạn: tính từ now, không cộng từ hạn cũ đã qua")
    void fulfill_premiumHetHan_tinhTuNow() {
        Invoice inv = pendingInvoice();
        SepayTransaction tx = pendingTx();
        User user = freeUser();
        user.setPlan(Plan.PREMIUM);
        user.setPlanExpiresAt(new Date(System.currentTimeMillis() - 5 * DAY_MS)); // hết 5 ngày trước
        long before = System.currentTimeMillis();

        Date newExpiry = fulfillmentService.fulfill(inv, tx, user, PlanType.MONTHLY);

        long delta = newExpiry.getTime() - before;
        assertTrue(delta >= 30 * DAY_MS - 60_000 && delta <= 30 * DAY_MS + 60_000,
                "Hạn mới phải ~ now + 30 ngày, không phải hạn cũ + 30 ngày, delta=" + delta);
        assertTrue(newExpiry.after(new Date()),
                "Hạn mới không được nằm trong quá khứ");
    }

    @Test
    @DisplayName("Premium còn hạn nhưng planExpiresAt null (dữ liệu bẩn) -> tính từ now")
    void fulfill_premiumNhungExpiryNull_tinhTuNow() {
        Invoice inv = pendingInvoice();
        SepayTransaction tx = pendingTx();
        User user = freeUser();
        user.setPlan(Plan.PREMIUM);
        user.setPlanExpiresAt(null);
        long before = System.currentTimeMillis();

        Date newExpiry = fulfillmentService.fulfill(inv, tx, user, PlanType.MONTHLY);

        assertTrue(newExpiry.getTime() - before >= 30 * DAY_MS - 60_000);
    }

    @Test
    @DisplayName("Gia hạn 2 lần liên tiếp: hạn cộng dồn đúng 60 ngày từ hạn đầu")
    void fulfill_haiLanLienTiep_congDon60Ngay() {
        Invoice inv = pendingInvoice();
        SepayTransaction tx = pendingTx();
        User user = freeUser();

        Date e1 = fulfillmentService.fulfill(inv, tx, user, PlanType.MONTHLY);
        Date e2 = fulfillmentService.fulfill(inv, tx, user, PlanType.MONTHLY);

        long delta = e2.getTime() - e1.getTime();
        assertTrue(delta >= 30 * DAY_MS - 60_000 && delta <= 30 * DAY_MS + 60_000,
                "Lần 2 phải cộng dồn từ hạn của lần 1, delta=" + delta);
        assertEquals(Plan.PREMIUM, user.getPlan());
    }
}
