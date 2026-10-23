package com.masterjp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * Phase D — F-044: hoa don tra phi (SePay).
 * <p>
 * - code: format "INV-XXXXXX" (6 ky tu uppercase alnum), unique, sinh bang
 *   SecureRandom + kiem tra trung truoc insert (PaymentService).
 * - amount: backend tu set theo planType (PlanType.amountVnd) — khong tin client.
 * - Chi chuyen PENDING -> PAID qua webhook fulfill (khong co API client-supplied).
 * - PENDING qua 24h -> EXPIRED (job 2:00 sang + lazy-check moi lan doc/match).
 */
@Document("invoices")
@CompoundIndexes({
        @CompoundIndex(name = "ix_invoice_user_created", def = "{'userId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "ix_invoice_user_status", def = "{'userId': 1, 'status': 1}")
})
public class Invoice {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code; // "INV-9F3A2B" — luon uppercase

    private String userId; // hex string theo convention codebase
    private PlanType planType;
    private long amount; // VND
    private InvoiceStatus status = InvoiceStatus.PENDING;
    private Date createdAt = new Date();
    private Date paidAt; // null cho toi khi PAID
    private String sepayTransactionId; // bankTransactionId da match (null khi chua match)

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public PlanType getPlanType() {
        return planType;
    }

    public void setPlanType(PlanType planType) {
        this.planType = planType;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Date paidAt) {
        this.paidAt = paidAt;
    }

    public String getSepayTransactionId() {
        return sepayTransactionId;
    }

    public void setSepayTransactionId(String sepayTransactionId) {
        this.sepayTransactionId = sepayTransactionId;
    }
}
