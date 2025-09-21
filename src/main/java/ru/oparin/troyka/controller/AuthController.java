package ru.oparin.troyka.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.exception.AuthException;
import ru.oparin.troyka.model.dto.LoginRequest;
import ru.oparin.troyka.model.dto.RegisterRequest;
import ru.oparin.troyka.service.AuthService;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<?>> register(@RequestBody RegisterRequest request) {
        return authService.register(request)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .onErrorResume(AuthException.class, e ->
                        Mono.just(ResponseEntity.status(e.getStatus())
                                .body(Map.of(
                                        "error", e.getMessage(),
                                        "status", e.getStatus().value()
                                )))
                )
                .onErrorResume(e -> {
                    log.error("Неизвестная ошибка при регистрации", e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(Map.of(
                                    "error", "Внутренняя ошибка сервера",
                                    "status", 500
                            )));
                });
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<?>> login(@RequestBody LoginRequest request) {
        return authService.login(request)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .onErrorResume(AuthException.class, e ->
                        Mono.just(ResponseEntity.status(e.getStatus())
                                .body(Map.of(
                                        "error", e.getMessage(),
                                        "status", e.getStatus().value()
                                )))
                )
                .onErrorResume(e -> {
                    log.error("Неизвестная ошибка при входе", e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(Map.of(
                                    "error", "Внутренняя ошибка сервера",
                                    "status", 500
                            )));
                });
    }
}