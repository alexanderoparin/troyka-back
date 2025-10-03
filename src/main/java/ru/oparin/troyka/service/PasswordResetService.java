package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.exception.AuthException;
import ru.oparin.troyka.model.dto.auth.ForgotPasswordRequest;
import ru.oparin.troyka.model.dto.auth.MessageResponse;
import ru.oparin.troyka.model.dto.auth.ResetPasswordRequest;
import ru.oparin.troyka.model.entity.PasswordResetToken;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.repository.PasswordResetTokenRepository;
import ru.oparin.troyka.repository.UserRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${app.frontend.url:https://24reshai.ru}")
    private String frontendUrl;

    @Value("${app.email.from:noreply@24reshai.ru}")
    private String fromEmail;

    private static final int TOKEN_LENGTH = 32;
    private static final int TOKEN_EXPIRY_HOURS = 24;

    public Mono<MessageResponse> requestPasswordReset(ForgotPasswordRequest request) {
        return userRepository.findByEmail(request.getEmail())
                .switchIfEmpty(Mono.error(new AuthException(HttpStatus.NOT_FOUND, "Пользователь с таким email не найден")))
                .flatMap(user -> {
                    log.info("Удаляем старые неиспользованные токены для пользователя {}", user.getUsername());
                    // Удаляем старые неиспользованные токены для этого пользователя
                    return tokenRepository.findActiveTokenByUserId(user.getId(), LocalDateTime.now())
                            .flatMap(tokenRepository::delete)
                            .then(Mono.just(user));
                })
                .flatMap(this::createPasswordResetToken)
                .flatMap(this::sendPasswordResetEmail)
                .doOnSuccess(result -> log.info("Запрос на восстановление пароля отправлен для email: {}", request.getEmail()))
                .map(result -> new MessageResponse("Инструкции по восстановлению пароля отправлены на вашу почту"))
                .onErrorResume(AuthException.class, e -> {
                    log.warn("Попытка восстановления пароля для несуществующего email: {}", request.getEmail());
                    return Mono.just(new MessageResponse("Пользователь с таким email не найден"));
                })
                .onErrorResume(e -> {
                    log.error("Ошибка при запросе восстановления пароля для email: {}", request.getEmail(), e);
                    return Mono.just(new MessageResponse("Произошла ошибка при отправке инструкций. Попробуйте позже"));
                });
    }

    public Mono<MessageResponse> resetPassword(ResetPasswordRequest request) {
        return tokenRepository.findByTokenAndNotUsedAndNotExpired(request.getToken(), LocalDateTime.now())
                .switchIfEmpty(Mono.error(new AuthException(HttpStatus.BAD_REQUEST, "Недействительный или истекший токен")))
                .flatMap(token -> {
                    // Помечаем токен как использованный
                    return tokenRepository.markTokenAsUsed(request.getToken())
                            .then(Mono.just(token));
                })
                .flatMap(token -> userRepository.findById(token.getUserId())
                        .switchIfEmpty(Mono.error(new AuthException(HttpStatus.NOT_FOUND, "Пользователь не найден")))
                        .flatMap(user -> {
                            // Обновляем пароль
                            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
                            return userRepository.save(user);
                        }))
                .doOnSuccess(user -> log.info("Пароль успешно сброшен для пользователя: {}", user.getUsername()))
                .map(user -> new MessageResponse("Пароль успешно изменен"))
                .onErrorResume(AuthException.class, e -> {
                    log.warn("Ошибка при сбросе пароля: {}", e.getMessage());
                    return Mono.just(new MessageResponse(e.getMessage()));
                })
                .onErrorResume(e -> {
                    log.error("Неожиданная ошибка при сбросе пароля", e);
                    return Mono.just(new MessageResponse("Произошла ошибка при изменении пароля. Попробуйте позже"));
                });
    }

    private Mono<PasswordResetToken> createPasswordResetToken(User user) {
        String token = generateSecureToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(user.getId())
                .token(token)
                .expiresAt(expiresAt)
                .used(false)
                .build();

        return tokenRepository.save(resetToken);
    }

    private Mono<String> sendPasswordResetEmail(PasswordResetToken token) {
        return userRepository.findById(token.getUserId())
                .flatMap(user -> {
                    String resetUrl = frontendUrl + "/reset-password?token=" + token.getToken();

                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setFrom(fromEmail);
                    message.setTo(user.getEmail());
                    message.setSubject("Восстановление пароля - 24reshai.ru");
                    message.setText(buildEmailContent(user.getUsername(), resetUrl));

                    try {
                        log.info("Отправляем email на: {}", user.getEmail());
                        mailSender.send(message);
                        log.info("Email с инструкциями по восстановлению пароля отправлен на: {}", user.getEmail());
                        return Mono.just("Инструкции по восстановлению пароля отправлены на вашу почту");
                    } catch (Exception e) {
                        log.error("Ошибка при отправке email на: {}", user.getEmail(), e);
                        return Mono.error(new RuntimeException("Ошибка при отправке email"));
                    }
                });
    }

    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[TOKEN_LENGTH];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String buildEmailContent(String username, String resetUrl) {
        return String.format("""
                Здравствуйте, %s!
                
                Вы запросили восстановление пароля для вашего аккаунта 24reshai.ru.
                
                Для установки нового пароля перейдите по ссылке:
                %s
                
                Ссылка действительна в течение 24 часов.
                
                Если вы не запрашивали восстановление пароля, просто проигнорируйте это письмо.
                
                С уважением,
                Команда 24reshai.ru
                """, username, resetUrl);
    }

    // Метод для очистки истекших токенов (можно вызывать по расписанию)
    public Mono<Void> cleanupExpiredTokens() {
        return tokenRepository.deleteExpiredTokens(LocalDateTime.now())
                .doOnSuccess(result -> log.info("Очищены истекшие токены восстановления пароля"));
    }
}

