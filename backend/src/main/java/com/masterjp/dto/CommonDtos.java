package com.masterjp.dto;

import java.util.List;

/** DTO dung chung: message / phan trang / loi. */
public final class CommonDtos {

    private CommonDtos() {
    }

    public record MessageResponse(String message) {
    }

    public record PageResponse<T>(List<T> items, long total, int page, int size) {
    }

    /** Moi loi tra ve FE deu theo shape nay, message tieng Viet, khong stack trace. */
    public record ErrorResponse(String code, String message) {
    }
}
