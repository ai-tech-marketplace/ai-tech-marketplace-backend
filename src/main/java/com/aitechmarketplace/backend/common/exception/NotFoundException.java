package com.aitechmarketplace.backend.common.exception;

public class NotFoundException extends RuntimeException {

    private final String errorCode;

    public NotFoundException(String message) {
        super(message);
        this.errorCode = "RESOURCE_NOT_FOUND";
    }

    public NotFoundException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}