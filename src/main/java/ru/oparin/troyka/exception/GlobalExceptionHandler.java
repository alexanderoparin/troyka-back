package ru.oparin.troyka.exception;

import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAllExceptions(Exception ex) {
        log.error("Неизвестная ошибка: ", ex);

        return Mono.just(ResponseEntity.internalServerError()
                .body(Map.of(
                        "error", "Внутренняя ошибка сервера",
                        "status", 500,
                        "message", ex.getMessage() != null ? ex.getMessage() : "Unknown error"
                )));
    }

    @ExceptionHandler(AuthException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAuthException(AuthException ex) {
        log.warn("Ошибка аутентификации: {}", ex.getMessage());

        return Mono.just(ResponseEntity.status(ex.getStatus())
                .body(Map.of(
                        "error", ex.getMessage(),
                        "status", ex.getStatus().value()
                )));
    }

    @ExceptionHandler(ValidationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidationException(ValidationException ex) {
        return Mono.just(ResponseEntity.badRequest()
                .body(Map.of(
                        "error", "Ошибка валидации",
                        "status", 400,
                        "details", ex.getMessage()
                )));
    }

    @ExceptionHandler(org.springframework.web.bind.support.WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidationException(WebExchangeBindException ex) {
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null ?
                                fieldError.getDefaultMessage() : "Invalid value"
                ));

        return Mono.just(ResponseEntity.badRequest()
                .body(Map.of(
                        "error", "Ошибка валидации данных",
                        "status", 400,
                        "details", errors
                )));
    }

    @ExceptionHandler(FalAIException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleFalAIException(FalAIException ex) {
        log.error("Ошибка FalAI: {}", ex.getMessage());

        if (ex.getCause() != null) {
            log.error("Cause: {}", ex.getCause().getMessage());
        }

        return Mono.just(ResponseEntity.status(ex.getStatus())
                .body(Map.of(
                        "error", ex.getMessage(),
                        "status", ex.getStatus().value()
                )));
    }

    @ExceptionHandler(PromptEnhancementException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handlePromptEnhancementException(PromptEnhancementException ex) {
        log.error("Ошибка улучшения промпта: {}", ex.getMessage());

        if (ex.getCause() != null) {
            log.error("Cause: {}", ex.getCause().getMessage());
        }

        return Mono.just(ResponseEntity.status(ex.getStatus())
                .body(Map.of(
                        "error", ex.getMessage(),
                        "status", ex.getStatus().value()
                )));
    }
}