package ru.oparin.troyka.exception;


import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class FalAIException extends RuntimeException {
    private final HttpStatus status;

    public FalAIException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public FalAIException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
