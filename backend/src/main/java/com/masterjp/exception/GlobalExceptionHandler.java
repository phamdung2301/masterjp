package com.masterjp.exception;

import com.masterjp.dto.CommonDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Moi loi tra ve FE deu theo shape {code, message} tieng Viet.
 * KHONG bao gio lo stack trace ra ngoai.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<CommonDtos.ErrorResponse> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new CommonDtos.ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonDtos.ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(" "));
        if (message.isBlank()) {
            message = "Dữ liệu gửi lên không hợp lệ.";
        }
        return badRequest("VALIDATION_ERROR", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonDtos.ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return badRequest("BAD_REQUEST", "Dữ liệu gửi lên không đúng định dạng.");
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<CommonDtos.ErrorResponse> handleBadParam(Exception e) {
        return badRequest("VALIDATION_ERROR", "Tham số yêu cầu không hợp lệ.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonDtos.ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new CommonDtos.ErrorResponse("FORBIDDEN", "Bạn không có quyền thực hiện thao tác này."));
    }

    /**
     * Request vao path khong ton tai (vd. mo backend URL truc tiep tren browser,
     * bot quet /wp-admin...). Backend la API-only nen khong co static resource —
     * day la 404 binh thuong: tra NOT_FOUND, log WARN thay vi ERROR de khoi
     * lam nhieu log loi that.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CommonDtos.ErrorResponse> handleNoResource(NoResourceFoundException e) {
        log.warn("No static resource for request '{}'", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new CommonDtos.ErrorResponse("NOT_FOUND", "Không tìm thấy tài nguyên."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonDtos.ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new CommonDtos.ErrorResponse("INTERNAL_ERROR",
                        "Đã có lỗi xảy ra, vui lòng thử lại sau."));
    }

    private ResponseEntity<CommonDtos.ErrorResponse> badRequest(String code, String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new CommonDtos.ErrorResponse(code, message));
    }
}
