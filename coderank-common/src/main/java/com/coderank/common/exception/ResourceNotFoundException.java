package com.coderank.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends CodeRankException {

    public ResourceNotFoundException(String resource, String identifier) {
        super(
                resource + " not found with identifier: " + identifier,
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND"
        );
    }
}