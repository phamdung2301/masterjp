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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

/**
 * Phase D — F-044: fulfill hoa don sau khi webhook match thanh cong.
 * <p>
 * Toan bo 5 buoc (DOC-13 muc 3.7) chay TRONG 1 transaction MongoDB
 * (MongoTransactionManager, config o MongoConfig) — tat ca hoac khong gi ca:
 * <ol>
 *   <li>Invoice: PENDING -> PAID (+ paidAt, sepayTransactionId)</li>
 *   <li>SepayTransaction: PENDING -> MATCHED (+ matchedInvoiceId)</li>
 *   <li>User: plan = PREMIUM; planExpiresAt CONG DON tu han cu neu con hieu luc
 *       (+30 ngay MONTHLY / +365 ngay YEARLY)</li>
 *   <li>Audit log: action = PAYMENT_FULFILLED (adminId = null — he thong tu dong)</li>
 * </ol>
 * Buoc 5 (email payment-success) goi SAU KHI transaction commit — email fail
 * khong rollback fulfill (user da tra tien, quyen loi khong duoc mat vi loi mail).
 * <p>
 * Luu y: yeu cau MongoDB replica set (Atlas dat) moi chay duoc multi-document
 * transaction.
 */
@Service
public class PaymentFulfillmentService {

    private final InvoiceRepository invoiceRepository;
    private final SepayTransactionRepository sepayTransactionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public PaymentFulfillmentService(InvoiceRepository invoiceRepository,
                                     SepayTransactionRepository sepayTransactionRepository,
                                     UserRepository userRepository,
                                     AuditService auditService) {
        this.invoiceRepository = invoiceRepository;
        this.sepayTransactionRepository = sepayTransactionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Fulfill 1 invoice da match. Tra ve planExpiresAt moi (caller dung de gui
     * email SAU KHI commit).
     */
    @Transactional
    public Date fulfill(Invoice invoice, SepayTransaction tx, User user, PlanType planType) {
        Date now = new Date();

        // 1. Invoice -> PAID
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(now);
        invoice.setSepayTransactionId(tx.getBankTransactionId());
        invoiceRepository.save(invoice);

        // 2. Transaction -> MATCHED
        tx.setStatus(SepayTransactionStatus.MATCHED);
        tx.setMatchedInvoiceId(invoice.getId());
        sepayTransactionRepository.save(tx);

        // 3. User -> PREMIUM, cong don tu han cu neu con hieu luc (khong mat ngay da tra)
        Date base = (user.getPlan() == Plan.PREMIUM
                && user.getPlanExpiresAt() != null
                && user.getPlanExpiresAt().after(now))
                ? user.getPlanExpiresAt()
                : now;
        Date newExpiry = Date.from(base.toInstant().plus(planType.daysToAdd(), ChronoUnit.DAYS));
        user.setPlan(Plan.PREMIUM);
        user.setPlanExpiresAt(newExpiry);
        userRepository.save(user);

        // 4. Audit log
        auditService.log(null, "PAYMENT_FULFILLED", "Invoice", invoice.getId(),
                Map.of(
                        "code", invoice.getCode(),
                        "planType", planType.name(),
                        "amount", invoice.getAmount(),
                        "bankTransactionId", tx.getBankTransactionId(),
                        "newPlanExpiresAt", newExpiry.toInstant().toString()));

        return newExpiry;
    }
}
