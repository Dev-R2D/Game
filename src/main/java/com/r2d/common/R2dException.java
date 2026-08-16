package com.r2d.common;

import org.springframework.http.HttpStatus;

/** 게임 규칙 위반이나 잘못된 요청을 HTTP 상태와 함께 표현하는 예외. */
public class R2dException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public R2dException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static R2dException notFound(String code, String message) {
        return new R2dException(HttpStatus.NOT_FOUND, code, message);
    }

    public static R2dException badRequest(String code, String message) {
        return new R2dException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static R2dException conflict(String code, String message) {
        return new R2dException(HttpStatus.CONFLICT, code, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
