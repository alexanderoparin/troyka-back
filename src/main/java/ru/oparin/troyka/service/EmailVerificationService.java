package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.EmailVerificationToken;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.repository.EmailVerificationTokenRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final JavaMailSender mailSender;
    private final EmailVerificationTokenRepository tokenRepository;
    private final UserService userService;

    @Value("${app.email.from:noreply@24reshai.ru}")
    private String fromEmail;

    @Value("${app.frontend.url:https://24reshai.ru}")
    private String frontendUrl;

    public Mono<Void> sendVerificationEmail(User user) {
        // Создаем токен подтверждения
        String token = generateVerificationToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24); // Токен действителен 24 часа
        
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .userId(user.getId())
                .token(token)
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now())
                .build();
        
        // Сохраняем токен в базе данных и отправляем письмо
        return tokenRepository.save(verificationToken)
                .doOnSuccess(savedToken -> {
                    log.info("Токен подтверждения сохранен в БД: {} для пользователя: {} <{}>", 
                            token, user.getUsername(), user.getEmail());
                    sendVerificationEmail(user.getEmail(), user.getUsername(), token);
                    log.info("Письмо подтверждения отправлено пользователю: {} <{}>", user.getUsername(), user.getEmail());
                })
                .doOnError(error -> log.error("Ошибка при сохранении токена подтверждения для пользователя: {} <{}>",
                        user.getUsername(), user.getEmail(), error))
                .then();
    }

    public Mono<Boolean> verifyEmail(String token) {
        log.info("Попытка верификации токена: {}", token);
        return tokenRepository.findByToken(token)
                .flatMap(verificationToken -> {
                    log.info("Токен найден в БД: {} для пользователя ID: {}", token, verificationToken.getUserId());
                    
                    // Сначала проверяем, не подтвержден ли уже пользователь
                    return userService.findById(verificationToken.getUserId())
                            .flatMap(user -> {
                                if (user.getEmailVerified() != null && user.getEmailVerified()) {
                                    log.info("Email уже подтвержден для пользователя ID: {} ({}), возвращаем успех", 
                                            user.getId(), user.getUsername());
                                    return Mono.just(true);
                                }
                                
                                if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                                    log.warn("Токен подтверждения истек: {} (истекает: {}, сейчас: {})", 
                                            token, verificationToken.getExpiresAt(), LocalDateTime.now());
                                    return Mono.just(false);
                                }
                                
                                log.info("Токен действителен, обновляем статус пользователя ID: {}", verificationToken.getUserId());
                                user.setEmailVerified(true);
                                return userService.saveUser(user)
                                        .then(tokenRepository.deleteById(verificationToken.getId()))
                                        .doOnSuccess(v -> log.info("Токен {} успешно удален после верификации", token))
                                        .then(Mono.just(true));
                            });
                })
                .switchIfEmpty(Mono.fromRunnable(() -> 
                        log.warn("Токен не найден в БД: {}", token))
                        .then(Mono.just(false)));
    }

    private void sendVerificationEmail(String email, String username, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("Подтвердите ваш email - 24reshai.ru");
        message.setText(buildVerificationEmailContent(username, token));
        
        mailSender.send(message);
    }

    private String buildVerificationEmailContent(String username, String token) {
        String verificationUrl = frontendUrl + "/verify-email?token=" + token;
        
        return String.format("""
                Здравствуйте, %s!
                
                Добро пожаловать в 24reshai.ru!
                
                Для завершения регистрации подтвердите ваш email адрес, перейдя по ссылке:
                
                %s
                
                Ссылка действительна в течение 24 часов.
                
                Если вы не регистрировались на нашем сайте, просто проигнорируйте это письмо.
                
                С уважением,
                Команда 24reshai.ru
                
                ---
                Если ссылка не работает, скопируйте и вставьте её в адресную строку браузера.
                """,
                username,
                verificationUrl
        );
    }

    private String generateVerificationToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
