package com.example.smartoffice.api;

import chat.giga.http.client.HttpClientException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void shouldReturnSafeServiceUnavailableResponseForGigaChatFailure() {
        HttpClientException exception = new HttpClientException(
                503,
                "sensitive upstream details".getBytes(StandardCharsets.UTF_8)
        );

        var response = handler.handleGigaChat(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("error"))
                .isEqualTo("Сервис AI временно недоступен. Попробуйте повторить запрос позже.")
                .asString()
                .doesNotContain("sensitive upstream details");
    }

    @Test
    void shouldHideUnexpectedExceptionDetailsFromClient() {
        var response = handler.handleUnexpected(
                new NullPointerException("internal implementation detail")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("error"))
                .isEqualTo("Внутренняя ошибка сервера")
                .asString()
                .doesNotContain("internal implementation detail");
    }

    @Test
    void shouldNotDescribeInvalidUpstreamRequestAsTemporaryOutage() {
        HttpClientException exception = new HttpClientException(
                422,
                "invalid function result with sensitive details".getBytes(StandardCharsets.UTF_8)
        );

        var response = handler.handleGigaChat(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().get("error"))
                .isEqualTo("Сервис AI не смог обработать запрос.")
                .asString()
                .doesNotContain("sensitive details", "временно недоступен");
    }
}
