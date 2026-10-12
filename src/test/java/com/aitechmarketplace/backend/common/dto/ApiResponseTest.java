package com.aitechmarketplace.backend.common.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void success_withData_shouldSetSuccessTrueAndNullMessage() {
        ApiResponse<String> response = ApiResponse.success("hello");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("hello");
        assertThat(response.message()).isNull();
    }

    @Test
    void success_withDataAndMessage_shouldSetFieldsCorrectly() {
        ApiResponse<Integer> response = ApiResponse.success(42, "Operation completed");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(42);
        assertThat(response.message()).isEqualTo("Operation completed");
    }
}
