package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.exception.AuthException;
import ru.oparin.troyka.model.dto.auth.*;
import ru.oparin.troyka.service.*;
import ru.oparin.troyka.service.telegram.TelegramAuthService;
import ru.oparin.troyka.util.IpUtil;
import ru.oparin.troyka.util.JwtUtil;
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
    private final JwtService jwtService;

    @Operation(summary = "Регистрация нового пользователя",
            description = "Создает нового пользователя в системе и возвращает JWT токен")
    @PostMapping("/register")
    public Mono<ResponseEntity<?>> register(@Valid @RequestBody RegisterRequest request, 
                                             ServerWebExchange exchange) {
        log.info("Получен запрос на регистрацию нового пользователя: {}", request);
        return IpUtil.extractClientIp(exchange)
                .<ResponseEntity<?>>flatMap(clientIp -> {
                    log.debug("IP адрес клиента для регистрации: {}", clientIp);
                    String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
                    return authService.register(request, clientIp, userAgent)
                            .<ResponseEntity<?>>map(authResponse -> ResponseEntity.ok(authResponse))
                            .onErrorResume(e -> {
                                if (e instanceof AuthException authEx) {
                                    log.warn("Ошибка аутентификации при регистрации: {}", authEx.getMessage());
                                    return Mono.<ResponseEntity<?>>just(ResponseEntity.status(authEx.getStatus())
                                            .body(new MessageResponse(authEx.getMessage())));
                                }
                                log.error("Ошибка при регистрации пользователя", e);
                                return Mono.<ResponseEntity<?>>just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(new MessageResponse("Ошибка при регистрации")));
                            });
                })
                .onErrorResume(e -> {
                    if (e instanceof IllegalStateException && e.getMessage() != null && e.getMessage().contains("IP адрес")) {
                        log.error("Критическая ошибка: не удалось определить IP адрес клиента", e);
                        return Mono.<ResponseEntity<?>>just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(new MessageResponse("Ошибка сервера. Обратитесь в поддержку.")));
                    }
                    log.error("Ошибка при извлечении IP адреса", e);
                    return Mono.<ResponseEntity<?>>just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new MessageResponse("Ошибка при регистрации")));
                });
    }

    @Operation(summary = "Вход в систему",
            description = "Аутентификация пользователя и получение JWT токена")
    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
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

    @Operation(summary = "Проверка и автоматическая отправка письма подтверждения",
            description = "Проверяет наличие активного токена и отправляет письмо, если нужно (токена нет или он старше часа)")
    @PostMapping("/check-and-send-verification")
    public Mono<ResponseEntity<MessageResponse>> checkAndSendVerificationEmail() {
        return SecurityUtil.getCurrentUsername()
                .flatMap(userService::findByUsernameOrThrow)
                .flatMap(user -> {
                    if (user.getEmailVerified() != null && user.getEmailVerified()) {
                        return Mono.just(ResponseEntity.ok(new MessageResponse("Email уже подтвержден")));
                    }
                    return emailVerificationService.checkAndSendVerificationEmailIfNeeded(user)
                            .map(wasSent -> wasSent 
                                    ? new MessageResponse("Письмо подтверждения отправлено на ваш email")
                                    : new MessageResponse("Письмо уже было отправлено недавно"))
                            .map(ResponseEntity::ok);
                })
                .onErrorResume(e -> {
                    log.error("Ошибка при проверке и отправке письма подтверждения", e);
                    return Mono.just(ResponseEntity.badRequest().body(new MessageResponse("Ошибка при отправке письма")));
                });
    }

    @Operation(summary = "Вход через Telegram",
            description = "Аутентификация пользователя через Telegram Login Widget")
    @PostMapping("/telegram/login")
    public Mono<ResponseEntity<AuthResponse>> loginWithTelegram(
            @Valid @RequestBody TelegramAuthRequest request,
            ServerWebExchange exchange) {
        return IpUtil.extractClientIp(exchange)
                .flatMap(clientIp -> {
                    String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
                    return telegramAuthService.loginWithTelegram(request, clientIp, userAgent);
                })
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    if (e instanceof AuthException authEx) {
                        log.warn("Ошибка аутентификации при входе через Telegram: {}", authEx.getMessage());
                        return Mono.just(ResponseEntity.status(authEx.getStatus())
                                .body(new AuthResponse(null, null, null, null, null, false)));
                    }
                    log.error("Ошибка при входе через Telegram", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new AuthResponse(null, null, null, null, null, false)));
                });
    }

    @Operation(summary = "Выход из системы",
            description = "Завершает сессию пользователя и инвалидирует JWT токен")
    @PostMapping("/logout")
    public Mono<ResponseEntity<MessageResponse>> logout(ServerWebExchange exchange) {
        log.info("Получен запрос на выход из системы");
        
        return SecurityUtil.getCurrentUsername()
                .doOnNext(username -> log.info("Выход пользователя: {}", username))
                .then(JwtUtil.extractToken(exchange))
                .doOnNext(token -> {
                    if (token != null) {
                        jwtService.invalidateToken(token);
                        log.info("Токен инвалидирован");
                    } else {
                        log.warn("Токен не найден при выходе");
                    }
                })
                .then(Mono.just(ResponseEntity.ok(new MessageResponse("Успешный выход из системы"))))
                .onErrorResume(e -> {
                    log.error("Ошибка при выходе из системы", e);
                    return Mono.just(ResponseEntity.ok(new MessageResponse("Выход выполнен")));
                });
    }

    @PostMapping("/telegram/debug")
    public Mono<ResponseEntity<String>> debugTelegram(@RequestBody TelegramAuthRequest request) {
        return Mono.just(ResponseEntity.ok("Отладочные данные записаны в лог"));
    }
}