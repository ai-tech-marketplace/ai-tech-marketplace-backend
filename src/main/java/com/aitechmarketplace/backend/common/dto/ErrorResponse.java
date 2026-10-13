package com.aitechmarketplace.backend.common.dto;

public record ErrorResponse(
        boolean success,
        Object data,
        String message,
        String errorCode
) {

    public static ErrorResponse of(String message, String errorCode) {
        return new ErrorResponse(false, null, message, errorCode);
    }
}
