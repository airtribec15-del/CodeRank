package com.coderank.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends CodeRankException {

    public InvalidRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }
}