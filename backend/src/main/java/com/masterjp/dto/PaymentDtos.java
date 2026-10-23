package com.masterjp.dto;

/**
 * Phase D — F-044/F-045: DTO cho payments, webhook SePay, freemium.
 * Ten field la contract tuyet doi voi FE (api-contract-guard se verify).
 */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    // ---------- F-044: tao hoa don ----------

    /** POST /api/payments/invoices — request: {"planType": "MONTHLY"} */
    public record CreateInvoiceRequest(String planType) {
    }

    public record TransferInstructions(
            String bankName,
            String accountNumber,
            String accountHolder,
            long amount,
            String transferContent,
            String note) {
    }

    /** POST /api/payments/invoices — response 201. */
    public record InvoiceDto(
            String code,
            String planType,
            long amount,
            String status,
            String createdAt,
            String expiresAt,
            TransferInstructions transferInstructions) {
    }

    /** DELETE /api/payments/invoices/{code} — response 200. */
    public record CancelInvoiceResponse(String code, String status) {
    }

    // ---------- F-044: webhook SePay ----------

    /** Webhook fulfill thanh cong: 200. */
    public record WebhookMatchedResponse(
            String status,
            String invoiceCode,
            String planType,
            String planExpiresAt) {
    }

    /** Webhook khong match: 200 {status: "unmatched", reason}. */
    public record WebhookUnmatchedResponse(String status, String reason) {
    }

    /** Webhook gui lai (cung bankTransactionId): 200, khong fulfill lan 2. */
    public record WebhookDuplicateResponse(String status, String bankTransactionId) {
    }

    /** Webhook bi bo qua (transferType != "in" / thieu id): 200. */
    public record WebhookIgnoredResponse(String status, String reason) {
    }

    // ---------- F-045: freemium ----------

    /** 429 FREEMIUM_LIMIT — shape mo rong FE doc de render paywall. */
    public record FreemiumLimitResponse(String code, String message, int limit, String resetAt) {
    }

    // ---------- Admin read-only ----------

    public record AdminInvoiceDto(
            String id,
            String code,
            String userId,
            String userEmail,
            String planType,
            long amount,
            String status,
            String createdAt,
            String paidAt,
            String sepayTransactionId) {
    }

    public record AdminSepayTransactionDto(
            String id,
            String bankTransactionId,
            long amount,
            String transferContent,
            String transactionDate,
            String senderAccount,
            String status,
            String matchedInvoiceId,
            String note,
            String createdAt) {
    }
}
