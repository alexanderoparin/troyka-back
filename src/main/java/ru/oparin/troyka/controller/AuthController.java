package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.auth.*;
import ru.oparin.troyka.service.AuthService;
import ru.oparin.troyka.service.PasswordResetService;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
@Tag(name = "Аутентификация", description = "API для регистрации и входа пользователей")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

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

    @Operation(summary = "Запрос восстановления пароля",
            description = "Отправляет email с инструкциями по восстановлению пароля")
    @PostMapping("/forgot-password")
    public Mono<ResponseEntity<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("Получен запрос на восстановление пароля для email: {}", request.getEmail());
        return passwordResetService.requestPasswordReset(request)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "Сброс пароля",
            description = "Устанавливает новый пароль по токену из email")
    @PostMapping("/reset-password")
    public Mono<ResponseEntity<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("Получен запрос на сброс пароля с токеном");
        return passwordResetService.resetPassword(request)
                .map(ResponseEntity::ok);
    }
}