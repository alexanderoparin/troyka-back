package ru.oparin.troyka.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Исключение для ошибок сервиса улучшения промптов через DeepInfra API.
 */
@Getter
public class PromptEnhancementException extends RuntimeException {
    private final HttpStatus status;

    public PromptEnhancementException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public PromptEnhancementException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}

