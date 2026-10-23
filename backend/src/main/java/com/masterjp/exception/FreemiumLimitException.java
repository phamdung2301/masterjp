package com.masterjp.exception;

import org.springframework.http.HttpStatus;

/**
 * Phase D — F-045: 429 FREEMIUM_LIMIT — user FREE vuot 20 tu moi/ngay.
 * Shape mo rong {code, message, limit, resetAt} (FE doc de render paywall washi).
 * resetAt = 00:00 ngay hom sau theo Asia/Ho_Chi_Minh (ISO-8601 co offset).
 */
public class FreemiumLimitException extends ApiException {

    private final int limit;
    private final String resetAt;

    public FreemiumLimitException(int limit, String resetAt) {
        super("FREEMIUM_LIMIT",
                "Hôm nay bạn đã học đủ " + limit + " từ mới. Nâng cấp Premium để học không giới hạn nhé!",
                HttpStatus.TOO_MANY_REQUESTS);
        this.limit = limit;
        this.resetAt = resetAt;
    }

    public int getLimit() {
        return limit;
    }

    public String getResetAt() {
        return resetAt;
    }
}
