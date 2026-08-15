package com.r2d.common;

import java.time.Instant;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 오류 응답 본문. */
    public record ApiError(String code, String message, Instant timestamp) {
    }

    @ExceptionHandler(R2dException.class)
    public ResponseEntity<ApiError> handleR2d(R2dException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new ApiError(e.getCode(), e.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_FAILED", detail, Instant.now()));
    }

    /**
     * 필수 헤더·파라미터 누락.
     *
     * <p>클라이언트가 고칠 수 있는 실수이므로 500이 아니라 400으로 내려보냅니다.
     * 앱이 500을 받으면 서버 장애로 오인해 재시도 루프에 들어갑니다.
     */
    @ExceptionHandler({MissingRequestHeaderException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleMissingInput(Exception e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("MISSING_REQUIRED_INPUT", e.getMessage(), Instant.now()));
    }

    /**
     * 존재하지 않는 경로.
     *
     * <p>맨 아래 {@code Exception} 핸들러가 이것까지 잡으면 오타 난 URL이 500으로 나갑니다.
     * 클라이언트는 서버 장애로 오인해 재시도하게 되므로, 404로 분리합니다.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("ENDPOINT_NOT_FOUND", "존재하지 않는 경로입니다.", Instant.now()));
    }

    /** 지원하지 않는 HTTP 메서드. 마찬가지로 500이 아니라 405로 알려 줘야 합니다. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiError("METHOD_NOT_ALLOWED", e.getMessage(), Instant.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("BAD_REQUEST", e.getMessage(), Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", e.getMessage(), Instant.now()));
    }
}
