package ru.oparin.troyka.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {
    @Getter
    private final HttpStatus status;


    public AuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;

    }
}