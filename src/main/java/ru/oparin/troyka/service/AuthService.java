package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.config.properties.GenerationProperties;
import ru.oparin.troyka.exception.AuthException;
import ru.oparin.troyka.model.dto.auth.AuthResponse;
import ru.oparin.troyka.model.dto.auth.LoginRequest;
import ru.oparin.troyka.model.dto.auth.RegisterRequest;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.model.enums.Role;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
@Slf4j
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserPointsService userPointsService;
    private final EmailVerificationService emailVerificationService;
    private final GenerationProperties generationProperties;
    private final RateLimitingService rateLimitingService;
    private final EmailDomainBlacklistService emailDomainBlacklistService;
    private final BlockedRegistrationMetricsService blockedRegistrationMetricsService;

    @Value("${jwt.expiration}")
    private long expiration;

    public Mono<AuthResponse> register(RegisterRequest request, String clientIp, String userAgent) {
        String trimmedUsername = request.getUsername().trim();
        String trimmedEmail = request.getEmail().trim();
        
        // Проверяем, не заблокирован ли домен email
        if (emailDomainBlacklistService.isDomainBlocked(trimmedEmail)) {
            log.warn("Попытка регистрации с заблокированным email доменом. IP: {}, email: {}", clientIp, trimmedEmail);
            
            // Извлекаем домен из email
            String emailDomain = trimmedEmail.substring(trimmedEmail.indexOf('@') + 1).toLowerCase();
            
            // Записываем метрику блокированной регистрации (асинхронно, не блокируем ответ)
            blockedRegistrationMetricsService.recordBlockedRegistration(
                    trimmedEmail,
                    emailDomain,
                    trimmedUsername,
                    clientIp,
                    userAgent,
                    "EMAIL"
            ).subscribe(
                    metric -> log.debug("Метрика блокированной регистрации сохранена: id={}", metric.getId()),
                    error -> log.error("Ошибка сохранения метрики блокированной регистрации", error)
            );
            
            // Возвращаем общую ошибку без указания причины
            return Mono.error(new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "Ошибка регистрации. Проверьте введенные данные."
            ));
        }
        
        // Проверяем rate limit перед регистрацией
        return rateLimitingService.isRegistrationAllowed(clientIp)
                .flatMap(isAllowed -> {
                    if (!isAllowed) {
                        log.warn("Попытка регистрации заблокирована из-за превышения лимита для IP: {}", clientIp);
                        return Mono.error(new AuthException(
                                HttpStatus.TOO_MANY_REQUESTS,
                                "Превышен лимит регистраций. Попробуйте позже."
                        ));
                    }
                    
                    return userService.existsByUsernameOrEmail(trimmedUsername, trimmedEmail)
                            .then(Mono.defer(() -> {

                                User user = User.builder()
                                        .username(trimmedUsername)
                                        .email(trimmedEmail)
                                        .password(passwordEncoder.encode(request.getPassword()))
                                        .role(Role.USER)
                                        .build();

                                return userService.saveUser(user)
                                        .flatMap(savedUser -> {
                                            // Регистрируем попытку регистрации в rate limiter
                                            return rateLimitingService.recordRegistrationAttempt(clientIp)
                                                    .then(userPointsService.addPointsToUser(savedUser.getId(), generationProperties.getPointsOnRegistration()))
                                                    .then(emailVerificationService.sendVerificationEmail(savedUser))
                                                    .then(Mono.fromCallable(() -> {
                                                        String token = jwtService.generateToken(savedUser);
                                                        log.info("Пользователь {} зарегистрирован с {} бесплатными поинтами, письмо подтверждения отправлено. IP: {}", 
                                                                savedUser.getUsername(), generationProperties.getPointsOnRegistration(), clientIp);
                                                        return new AuthResponse(
                                                                token,
                                                                savedUser.getUsername(),
                                                                savedUser.getEmail(),
                                                                savedUser.getRole().name(),
                                                                LocalDateTime.now().plusSeconds(expiration / 1000),
                                                                true
                                                        );
                                                    }));
                                        });
                            }));
                });
    }

    public Mono<AuthResponse> login(LoginRequest request) {
        String trimmedUsername = request.getUsername().trim();
        return userService.findByUsernameOrThrow(trimmedUsername)
                .flatMap(user -> {
                    // Проверяем, не заблокирован ли пользователь
                    if (user.getBlocked() != null && user.getBlocked()) {
                        log.warn("Попытка входа заблокированного пользователя: {}", trimmedUsername);
                        return Mono.error(new AuthException(
                                HttpStatus.FORBIDDEN,
                                "Ваш аккаунт заблокирован. Обратитесь к администратору."
                        ));
                    }
                    
                    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        return Mono.error(new AuthException(
                                HttpStatus.UNAUTHORIZED,
                                "Неверный пароль"
                        ));
                    }

                    String token = jwtService.generateToken(user);
                    return Mono.just(new AuthResponse(
                            token,
                            user.getUsername(),
                            user.getEmail(),
                            user.getRole().name(),
                            LocalDateTime.now().plusSeconds(expiration / 1000),
                            false
                    ));
                });
    }
}