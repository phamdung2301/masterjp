package com.masterjp.util;

import com.masterjp.exception.ApiException;
import org.springframework.http.HttpStatus;

/** Parse enum tu String voi message tieng Viet (thay vi loi Jackson tieng Anh). */
public final class EnumUtils {

    private EnumUtils() {
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", fieldName + " không được để trống.",
                    HttpStatus.BAD_REQUEST);
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException("VALIDATION_ERROR",
                    fieldName + " không hợp lệ: " + value + ".", HttpStatus.BAD_REQUEST);
        }
    }

    public static <E extends Enum<E>> E parseOrNull(Class<E> type, String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parse(type, value, fieldName);
    }
}
