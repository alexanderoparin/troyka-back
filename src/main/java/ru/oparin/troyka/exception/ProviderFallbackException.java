package ru.oparin.troyka.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Исключение, указывающее, что произошла ошибка провайдера,
 * требующая fallback переключения на резервный провайдер.
 */
@Getter
public class ProviderFallbackException extends FalAIException {
    
    /**
     * Тип ошибки для метрик (TIMEOUT, CONNECTION_ERROR, HTTP_ERROR и т.д.).
     */
    private final String errorType;
    
    /**
     * HTTP статус код (может быть null для ошибок подключения или таймаутов).
     */
    private final Integer httpStatus;

    public ProviderFallbackException(String message, HttpStatus status, String errorType) {
        super(message, status);
        this.errorType = errorType;
        this.httpStatus = status != null ? status.value() : null;
    }

    public ProviderFallbackException(String message, HttpStatus status, String errorType, Integer httpStatus) {
        super(message, status);
        this.errorType = errorType;
        this.httpStatus = httpStatus;
    }

    public ProviderFallbackException(String message, HttpStatus status, String errorType, Throwable cause) {
        super(message, status, cause);
        this.errorType = errorType;
        this.httpStatus = status != null ? status.value() : null;
    }
}
