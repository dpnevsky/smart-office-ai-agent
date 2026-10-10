package com.example.smartoffice.api;

import chat.giga.http.client.HttpClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, Object>> handleBusiness(RuntimeException ex) {
        log.warn("Ошибка бизнес-операции", ex);
        return ResponseEntity.badRequest().body(Map.of(
                "timestamp", Instant.now().toString(),
                "error", ex.getMessage()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "timestamp", Instant.now().toString(),
                "error", "sessionId и message обязательны"
        ));
    }

    @ExceptionHandler(HttpClientException.class)
    ResponseEntity<Map<String, Object>> handleGigaChat(HttpClientException ex) {
        log.error("Ошибка обращения к GigaChat", ex);
        boolean temporaryFailure = ex.statusCode() == 429 || ex.statusCode() >= 500;
        HttpStatus status = temporaryFailure
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_GATEWAY;
        String message = temporaryFailure
                ? "Сервис AI временно недоступен. Попробуйте повторить запрос позже."
                : "Сервис AI не смог обработать запрос.";
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "error", message
        ));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Необработанная ошибка API", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "timestamp", Instant.now().toString(),
                "error", "Внутренняя ошибка сервера"
        ));
    }
}
