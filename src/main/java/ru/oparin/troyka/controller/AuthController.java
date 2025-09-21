package ru.oparin.troyka.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.AuthResponse;
import ru.oparin.troyka.model.dto.LoginRequest;
import ru.oparin.troyka.model.dto.RegisterRequest;
import ru.oparin.troyka.service.AuthService;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<AuthResponse>> register(@RequestBody RegisterRequest request) {
        log.info("Получен запрос на регистрацию нового пользователя: {}", request);
        return authService.register(request)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@RequestBody LoginRequest request) {
        log.info("Получен запрос на авторизацию пользователя с логином: {}", request.getUsername());
        return authService.login(request)
                .map(ResponseEntity::ok);
    }
}