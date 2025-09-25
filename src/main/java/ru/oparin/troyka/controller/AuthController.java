package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.auth.AuthResponse;
import ru.oparin.troyka.model.dto.auth.LoginRequest;
import ru.oparin.troyka.model.dto.auth.RegisterRequest;
import ru.oparin.troyka.service.AuthService;

@Slf4j
@RestController
@RequestMapping("/auth")
@Tag(name = "Аутентификация", description = "API для регистрации и входа пользователей")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Регистрация нового пользователя",
            description = "Создает нового пользователя в системе и возвращает JWT токен")
    @PostMapping("/register")
    public Mono<ResponseEntity<AuthResponse>> register(@RequestBody RegisterRequest request) {
        log.info("Получен запрос на регистрацию нового пользователя: {}", request);
        return authService.register(request)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "Вход в систему",
            description = "Аутентификация пользователя и получение JWT токена")
    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@RequestBody LoginRequest request) {
        log.info("Получен запрос на авторизацию пользователя с логином: {}", request.getUsername());
        return authService.login(request)
                .map(ResponseEntity::ok);
    }
}