package com.masterjp.domain;

/**
 * Phase D — F-044: vong doi hoa don.
 * Chi webhook fulfill moi duoc chuyen PENDING -> PAID — khong endpoint nao
 * cho phep client tu set PAID (persistence contract, DOC-13 muc 3.9).
 */
public enum InvoiceStatus {
    PENDING,
    PAID,
    EXPIRED,
    CANCELLED
}
