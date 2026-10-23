package com.masterjp.exception;

import org.springframework.http.HttpStatus;

/**
 * 429 DAILY_LIMIT — mo rong shape {code, message} hien co them truong resetAt
 * (ISO-8601 = 00:00 ngay hom sau theo Asia/Ho_Chi_Minh).
 */
public class QuotaExceededException extends ApiException {

    private final String resetAt;

    public QuotaExceededException(String message, String resetAt) {
        super("DAILY_LIMIT", message, HttpStatus.TOO_MANY_REQUESTS);
        this.resetAt = resetAt;
    }

    public String getResetAt() {
        return resetAt;
    }
}
