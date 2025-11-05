package ru.oparin.troyka.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class SessionNotFoundException extends RuntimeException {
    private final HttpStatus status;

    public SessionNotFoundException(String message) {
        super(message);
        this.status = HttpStatus.NOT_FOUND;
    }

    public SessionNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.status = HttpStatus.NOT_FOUND;
    }
}

