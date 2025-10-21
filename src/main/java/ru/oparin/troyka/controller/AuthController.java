package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.auth.*;
import ru.oparin.troyka.service.AuthService;
import ru.oparin.troyka.service.EmailVerificationService;
import ru.oparin.troyka.service.PasswordResetService;
import ru.oparin.troyka.service.UserService;
import ru.oparin.troyka.service.telegram.TelegramAuthService;
import ru.oparin.troyka.util.SecurityUtil;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
@Tag(name = "Аутентификация", description = "API для регистрации и входа пользователей")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;
    private final UserService userService;
    private final TelegramAuthService telegramAuthService;

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
    public Mono<ResponseEntity<MessageResponse>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("Получен запрос на восстановление пароля для email: {}", request.getEmail());
        return passwordResetService.requestPasswordReset(request)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "Сброс пароля",
            description = "Устанавливает новый пароль по токену из email")
    @PostMapping("/reset-password")
    public Mono<ResponseEntity<MessageResponse>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("Получен запрос на сброс пароля с токеном");
        return passwordResetService.resetPassword(request)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "Подтверждение email",
            description = "Подтверждает email адрес пользователя по токену из письма")
    @GetMapping("/verify-email")
    public Mono<ResponseEntity<MessageResponse>> verifyEmail(@RequestParam String token) {
        log.info("Получен запрос на подтверждение email с токеном: {}", token);
        return emailVerificationService.verifyEmail(token)
                .map(success -> {
                    if (success) {
                        return ResponseEntity.ok(new MessageResponse("Email успешно подтвержден!"));
                    } else {
                        return ResponseEntity.badRequest().body(new MessageResponse("Неверный или истекший токен подтверждения"));
                    }
                });
    }

    @Operation(summary = "Повторная отправка письма подтверждения",
            description = "Отправляет повторное письмо подтверждения email текущему пользователю")
    @PostMapping("/resend-verification")
    public Mono<ResponseEntity<MessageResponse>> resendVerificationEmail() {
        return SecurityUtil.getCurrentUsername()
                .flatMap(userService::findByUsernameOrThrow)
                .flatMap(user -> {
                    if (user.getEmailVerified() != null && user.getEmailVerified()) {
                        return Mono.just(ResponseEntity.badRequest().body(new MessageResponse("Email уже подтвержден")));
                    }
                    return emailVerificationService.sendVerificationEmail(user)
                            .then(Mono.just(ResponseEntity.ok(new MessageResponse("Письмо подтверждения отправлено на ваш email"))));
                })
                .onErrorResume(e -> {
                    log.error("Ошибка при повторной отправке письма подтверждения", e);
                    return Mono.just(ResponseEntity.badRequest().body(new MessageResponse("Ошибка при отправке письма")));
                });
    }

    @Operation(summary = "Вход через Telegram",
            description = "Аутентификация пользователя через Telegram Login Widget")
    @PostMapping("/telegram/login")
    public Mono<ResponseEntity<AuthResponse>> loginWithTelegram(@Valid @RequestBody TelegramAuthRequest request) {
        log.info("Получен запрос на вход через Telegram для пользователя с ID: {}", request.getId());
        log.debug("Данные запроса Telegram: {}", request);
        return telegramAuthService.loginWithTelegram(request)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/telegram/debug")
    public Mono<ResponseEntity<String>> debugTelegram(@RequestBody TelegramAuthRequest request) {
        log.debug("Отладочный запрос Telegram: {}", request);
        return Mono.just(ResponseEntity.ok("Отладочные данные записаны в лог"));
    }
}