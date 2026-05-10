package com.coderank.common.exception;

import org.springframework.http.HttpStatus;

public class CodeRankException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;

    public CodeRankException(String message, HttpStatus httpStatus, String errorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public CodeRankException(String message, Throwable cause, HttpStatus httpStatus, String errorCode) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }
}