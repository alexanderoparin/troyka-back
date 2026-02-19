package ru.oparin.troyka.exception;

import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.channel.AbortedException;

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
        log.error("Ошибка сервиса генерации: {}", ex.getMessage());

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

    @ExceptionHandler(AbortedException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleClientDisconnectException(AbortedException ex) {
        log.debug("Клиент закрыл соединение до завершения запроса. Полученное исключение: {}", ex.getClass().getSimpleName());
        return Mono.empty();
    }

    @ExceptionHandler(MethodNotAllowedException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleMethodNotAllowedException(
            MethodNotAllowedException ex, 
            ServerWebExchange exchange) {
        
        // Получаем минимальную информацию о запросе
        var request = exchange.getRequest();
        String uri = request.getURI().getPath(); // Только путь, без полного URL
        HttpMethod method = request.getMethod();
        
        // Получаем IP адрес (кратко)
        String clientIp = "unknown";
        try {
            String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isEmpty()) {
                clientIp = forwardedFor.split(",")[0].trim();
            } else {
                String realIp = request.getHeaders().getFirst("X-Real-IP");
                if (realIp != null && !realIp.isEmpty()) {
                    clientIp = realIp;
                } else if (request.getRemoteAddress() != null) {
                    clientIp = request.getRemoteAddress().getAddress().getHostAddress();
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки извлечения IP
        }
        
        // Формируем список поддерживаемых методов
        String supportedMethods = ex.getSupportedMethods() != null 
                ? ex.getSupportedMethods().stream()
                    .map(HttpMethod::name)
                    .collect(Collectors.joining(", "))
                : "неизвестно";
        
        // Краткое логирование
        log.warn("405 Method Not Allowed: {} {} from {} (supported: {})", 
                method != null ? method.name() : "null", 
                uri, 
                clientIp, 
                supportedMethods);

        return Mono.just(ResponseEntity.status(405)
                .body(Map.of(
                        "error", "Метод не разрешен",
                        "status", 405,
                        "message", String.format("Метод %s не поддерживается для данного эндпоинта. Поддерживаемые методы: %s", 
                                method != null ? method.name() : "null", supportedMethods),
                        "requestedMethod", method != null ? method.name() : "null",
                        "supportedMethods", supportedMethods,
                        "uri", uri
                )));
    }
}