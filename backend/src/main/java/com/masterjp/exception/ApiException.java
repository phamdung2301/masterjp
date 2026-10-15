package com.masterjp.exception;

import org.springframework.http.HttpStatus;

/** Loi nghiep vu: luon co {code, message tieng Viet} + HTTP status. */
public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ApiException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
