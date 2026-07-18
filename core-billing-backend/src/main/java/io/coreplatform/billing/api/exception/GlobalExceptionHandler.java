package io.coreplatform.billing.api.exception;

import io.coreplatform.billing.application.exception.AccountNotFoundException;
import io.coreplatform.billing.application.exception.BillingBusinessException;
import io.coreplatform.billing.application.exception.DuplicateTransactionException;
import io.coreplatform.billing.application.exception.InsufficientPermissionException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(AccountNotFoundException ex,
                                                               HttpServletRequest request) {
        return errorResponse(HttpStatus.NOT_FOUND, ErrorCode.ACCOUNT_NOT_FOUND.getCode(),
                ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientPermissionException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(InsufficientPermissionException ex,
                                                                HttpServletRequest request) {
        return errorResponse(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.getCode(),
                ex.getMessage(), request);
    }

    @ExceptionHandler(BillingBusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BillingBusinessException ex,
                                                               HttpServletRequest request) {
        HttpStatus status = switch (ex.getKind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
        };
        return errorResponse(status, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateTransactionException ex,
                                                                HttpServletRequest request) {
        Map<String, Object> body = baseBody(HttpStatus.CONFLICT.value(),
                ErrorCode.DUPLICATE_TRANSACTION.getCode(), ex.getMessage(), request);
        body.put("existingTransactionNo", ex.getExistingTransactionNo());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadArgument(IllegalArgumentException ex,
                                                                  HttpServletRequest request) {
        return errorResponse(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.getCode(),
                ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex,
                                                                   HttpServletRequest request) {
        return errorResponse(HttpStatus.CONFLICT, ErrorCode.VALIDATION_ERROR.getCode(),
                ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.VALIDATION_ERROR.getCode(),
                message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex,
                                                              HttpServletRequest request) {
        log.error("未预期的错误", ex);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.getCode(),
                "服务器内部错误", request);
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String errorCode,
                                                               String detail, HttpServletRequest request) {
        Map<String, Object> body = baseBody(status.value(), errorCode, detail, request);
        return ResponseEntity.status(status).body(body);
    }

    private Map<String, Object> baseBody(int status, String errorCode, String detail,
                                          HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://core-platform.dev/problems/" + errorCode.toLowerCase());
        body.put("title", detail);
        body.put("status", status);
        body.put("detail", detail);
        body.put("errorCode", errorCode);
        body.put("path", request.getRequestURI());
        body.put("timestamp", LocalDateTime.now().toString());
        return body;
    }
}
