package com.masterjp.domain;

/**
 * Phase D — F-044: goi tra phi khi tao hoa don.
 */
public enum PlanType {
    MONTHLY,
    YEARLY;

    /** So tien VND backend tu set — KHONG tin amount tu client. */
    public long amountVnd() {
        return this == MONTHLY ? 49_000L : 390_000L;
    }

    /** So ngay cong don vao planExpiresAt khi fulfill. */
    public int daysToAdd() {
        return this == MONTHLY ? 30 : 365;
    }
}
