package com.aitechmarketplace.backend.common.exception;

import com.aitechmarketplace.backend.common.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void handleNotFoundException_shouldReturnErrorResponse() {
        NotFoundException exception = new NotFoundException("User not found");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleNotFoundException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().message()).isEqualTo("User not found");
        assertThat(response.getBody().errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void handleBadRequestException_shouldReturnErrorResponse() {
        BadRequestException exception = new BadRequestException("Invalid input");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBadRequestException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid input");
        assertThat(response.getBody().errorCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void handleConflictException_shouldReturnErrorResponse() {
        ConflictException exception = new ConflictException("Email already exists");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleConflictException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().message()).isEqualTo("Email already exists");
        assertThat(response.getBody().errorCode()).isEqualTo("CONFLICT");
    }
}
