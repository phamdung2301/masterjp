package com.masterjp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * Phase D — F-044: giao dich SePay nhan qua webhook.
 * <p>
 * - bankTransactionId unique: chong double-fulfill khi SePay gui lai webhook.
 * - note: ly do khi UNMATCHED (CODE_NOT_FOUND / AMOUNT_MISMATCH /
 *   INVOICE_NOT_PENDING) phuc vu xu ly tay. UNMATCHED khong auto-refund.
 * - Mapping payload (binding, DOC-13 muc 3.3): id -> bankTransactionId,
 *   transferAmount -> amount, content -> transferContent,
 *   transactionDate -> transactionDate, senderAccountName -> senderAccount.
 *   Field name SePay thuc te chua duoc xac minh — PaymentService parse
 *   phong thu + log raw payload de doi chieu lan chay that dau tien.
 */
@Document("sepay_transactions")
public class SepayTransaction {

    @Id
    private String id;

    /** Unique — "id" tu payload SePay; chong retry storm. */
    @Indexed(unique = true)
    private String bankTransactionId;

    private long amount; // VND — "transferAmount" tu payload
    private String transferContent; // "content" tu payload — noi tim ma hoa don
    private Date transactionDate;
    private String senderAccount; // "senderAccountName" tu payload (co the null)

    @Indexed
    private SepayTransactionStatus status = SepayTransactionStatus.PENDING;
    private String matchedInvoiceId; // set khi MATCHED
    private String note; // ly do khi UNMATCHED
    private Date createdAt = new Date();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBankTransactionId() {
        return bankTransactionId;
    }

    public void setBankTransactionId(String bankTransactionId) {
        this.bankTransactionId = bankTransactionId;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getTransferContent() {
        return transferContent;
    }

    public void setTransferContent(String transferContent) {
        this.transferContent = transferContent;
    }

    public Date getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(Date transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getSenderAccount() {
        return senderAccount;
    }

    public void setSenderAccount(String senderAccount) {
        this.senderAccount = senderAccount;
    }

    public SepayTransactionStatus getStatus() {
        return status;
    }

    public void setStatus(SepayTransactionStatus status) {
        this.status = status;
    }

    public String getMatchedInvoiceId() {
        return matchedInvoiceId;
    }

    public void setMatchedInvoiceId(String matchedInvoiceId) {
        this.matchedInvoiceId = matchedInvoiceId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
