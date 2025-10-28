package ru.oparin.troyka.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.config.properties.GenerationProperties;
import ru.oparin.troyka.exception.AuthException;
import ru.oparin.troyka.mapper.TelegramMapper;
import ru.oparin.troyka.model.dto.auth.AuthResponse;
import ru.oparin.troyka.model.dto.auth.TelegramAuthRequest;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.model.enums.Role;
import ru.oparin.troyka.service.JwtService;
import ru.oparin.troyka.service.SessionService;
import ru.oparin.troyka.service.UserPointsService;
import ru.oparin.troyka.service.UserService;
import ru.oparin.troyka.util.SecurityUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Сервис для аутентификации через Telegram Login Widget.
 * Обеспечивает валидацию данных от Telegram и создание/поиск пользователей.
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class TelegramAuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserPointsService userPointsService;
    private final SessionService sessionService;
    private final TelegramMapper telegramMapper;
    private final GenerationProperties generationProperties;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${telegram.login.bot-token}")
    private String telegramBotToken;

    /**
     * Вход через Telegram Login Widget.
     * Создает нового пользователя или находит существующего по telegram_id.
     * Если есть email - предлагает привязку к существующему аккаунту.
     * БЕЗОПАСНО: Не привязывает автоматически по username для предотвращения атак.
     *
     * @param request данные от Telegram Login Widget
     * @return JWT токен и информация о пользователе
     */
    public Mono<AuthResponse> loginWithTelegram(TelegramAuthRequest request) {
        return validateTelegramData(request)
                .then(Mono.defer(() -> {
                    // Ищем пользователя по telegram_id (безопасно)
                    return userService.findByTelegramId(request.getId())
                            .flatMap(existingUser -> {
                                return updateTelegramData(existingUser, request)
                                        .flatMap(userService::saveUser)
                                        .map(this::createAuthResponse);
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                // Если есть email - предлагаем привязку к существующему аккаунту
                                if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
                                    return userService.findByEmail(request.getEmail())
                                            .flatMap(existingUser -> {
                                                // Проверяем, подтвержден ли email
                                                if (existingUser.getEmailVerified() == null || !existingUser.getEmailVerified()) {
                                                // Создаем нового пользователя, если email не подтвержден
                                                return createNewUserAndAuthResponse(request);
                                                }
                                                
                                                return updateTelegramData(existingUser, request)
                                                        .flatMap(userService::saveUser)
                                                        .map(this::createAuthResponse);
                                            })
                                            .switchIfEmpty(Mono.defer(() -> {
                                                // Пользователь с таким email не найден, создаем нового
                                                return createNewUserAndAuthResponse(request);
                                            }));
                                } else {
                                    // Создаем нового пользователя без email
                                    return createNewUserAndAuthResponse(request);
                                }
                            }));
                }))
                .doOnError(error -> log.error("Ошибка входа через Telegram для ID: {}", request.getId(), error));
    }

    /**
     * Привязка Telegram к текущему авторизованному пользователю.
     * Используется в настройках аккаунта для привязки Telegram.
     *
     * @param request данные от Telegram Login Widget
     * @return JWT токен и обновленная информация о пользователе
     */
    public Mono<AuthResponse> linkTelegramToUser(TelegramAuthRequest request) {
        return SecurityUtil.getCurrentUsername()
                .doOnNext(username -> log.debug("Привязка телеграмм к аккаунту с username {}: {}", username, request))
                .flatMap(userService::findByUsernameOrThrow)
                .flatMap(user -> linkTelegramToExistingUser(request, user.getId()));
    }

    /**
     * Привязка Telegram к существующему аккаунту.
     * Пользователь должен быть авторизован в системе.
     *
     * @param request данные от Telegram Login Widget
     * @param currentUserId ID текущего авторизованного пользователя
     * @return JWT токен и обновленная информация о пользователе
     */
    public Mono<AuthResponse> linkTelegramToExistingUser(TelegramAuthRequest request, Long currentUserId) {
        log.info("Привязка Telegram к существующему аккаунту, TelegramAuthRequest: {}", request);
        return validateTelegramData(request)
                .then(Mono.defer(() -> {
                    // Проверяем, не привязан ли уже этот telegram_id к другому пользователю
                    return userService.findByTelegramId(request.getId())
                            .flatMap(existingUser -> {
                                if (!existingUser.getId().equals(currentUserId)) {
                                    return Mono.error(new AuthException(
                                            HttpStatus.CONFLICT,
                                            "Этот Telegram аккаунт уже привязан к другому пользователю"
                                    ));
                                }
                                return Mono.just(existingUser);
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                // Получаем текущего пользователя и привязываем Telegram
                                return userService.findByIdOrThrow(currentUserId)
                                        .flatMap(user -> updateTelegramData(user, request)
                                                .flatMap(userService::saveUser));
                            }));
                }))
                .map(this::createAuthResponse);
    }

    /**
     * Отвязка Telegram от аккаунта.
     *
     * @param userId ID пользователя
     * @return результат операции
     */
    public Mono<Void> unlinkTelegram(Long userId) {
        return userService.findById(userId)
                .switchIfEmpty(Mono.error(new AuthException(
                        HttpStatus.NOT_FOUND,
                        "Пользователь не найден"
                )))
                .flatMap(user -> {
                    user.setTelegramId(null);
                    user.setTelegramUsername(null);
                    user.setTelegramFirstName(null);
                    user.setTelegramPhotoUrl(null);

                    return userService.saveUser(user);
                })
                .then();
    }

    /**
     * Отвязка Telegram от текущего авторизованного пользователя.
     *
     * @return результат операции
     */
    public Mono<Void> unlinkTelegramFromCurrentUser() {
        return SecurityUtil.getCurrentUsername()
                .flatMap(userService::findByUsernameOrThrow)
                .flatMap(user -> unlinkTelegram(user.getId()));
    }

    /**
     * Валидация данных от Telegram Login Widget.
     * Проверяет подпись и актуальность данных.
     */
    private Mono<Void> validateTelegramData(TelegramAuthRequest request) {
        return Mono.fromCallable(() -> {
            // Проверяем актуальность данных (не старше 24 часов)
            long currentTime = Instant.now().getEpochSecond();
            
            if (currentTime - request.getAuth_date() > 86400) { // 24 часа
                throw new AuthException(HttpStatus.BAD_REQUEST, "Данные авторизации устарели");
            }

            Map<String, String> params = paramsForHash(request);

            String dataCheckString = params.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("\n"));

            // Вычисляем подпись согласно примеру из интернета
            // secret_key = SHA256(bot_token) как байты, а не hex строку
            byte[] secretKeyBytes = DigestUtils.sha256(telegramBotToken);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKeyBytes, "HmacSHA256");
            
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));
            String calculatedHash = bytesToHex(hash);

            log.debug("Подпись Telegram: {}", request.getHash());
            log.debug("Рассчитанная подпись: {}", calculatedHash);

            if (!calculatedHash.equals(request.getHash())) {
                throw new AuthException(HttpStatus.UNAUTHORIZED, "Неверная подпись Telegram");
            }

            return null;
        });
    }

    /**
     * Создаем строку для проверки подписи
     */
    private Map<String, String> paramsForHash(TelegramAuthRequest request) {
        Map<String, String> params = new TreeMap<>();
        params.put("id", request.getId().toString());
        if (request.getFirst_name() != null) {
            params.put("first_name", request.getFirst_name());
        }
        if (request.getLast_name() != null) {
            params.put("last_name", request.getLast_name());
        }
        if (request.getUsername() != null) {
            params.put("username", request.getUsername());
        }
        if (request.getPhoto_url() != null) {
            params.put("photo_url", request.getPhoto_url());
        }
        params.put("auth_date", request.getAuth_date().toString());
        return params;
    }


    /**
     * Создание нового пользователя из данных Telegram.
     * Использует email из Telegram, если он предоставлен, иначе оставляет поле пустым.
     */
    private Mono<User> createNewUserFromTelegram(TelegramAuthRequest request) {
        return generateUniqueUsername(request.getUsername(), request.getId())
                .map(username -> {
                    // Используем email из Telegram, если он предоставлен, иначе null
                    String email = (request.getEmail() != null && !request.getEmail().trim().isEmpty()) 
                            ? request.getEmail() 
                            : null;
                    
                    // Email подтвержден только если он предоставлен в Telegram
                    boolean emailVerified = (request.getEmail() != null && !request.getEmail().trim().isEmpty());

                    return User.builder()
                            .username(username)
                            .email(email)
                            .password(passwordEncoder.encode("telegram_auth_" + request.getId())) // Временный пароль
                            .role(Role.USER)
                            .emailVerified(emailVerified)
                            .telegramId(request.getId())
                            .telegramUsername(request.getUsername())
                            .telegramFirstName(request.getFirst_name())
                            .telegramPhotoUrl(request.getPhoto_url())
                            .build();
                });
    }

    /**
     * Генерация уникального username для Telegram пользователя.
     */
    private Mono<String> generateUniqueUsername(String telegramUsername, Long telegramId) {
        String baseUsername = telegramUsername != null ? 
                telegramUsername : 
                "tg_" + telegramId;
        
        return generateUniqueUsernameRecursive(baseUsername, 1);
    }

    /**
     * Рекурсивная генерация уникального username.
     */
    private Mono<String> generateUniqueUsernameRecursive(String baseUsername, int counter) {
        // Ограничиваем количество попыток (максимум 100)
        if (counter > 100) {
            return Mono.just(baseUsername + "_" + System.currentTimeMillis());
        }
        
        String username = counter == 1 ? baseUsername : baseUsername + "_" + counter;
        
        // Проверяем, существует ли пользователь с таким username
        return userService.existsByUsername(username)
                .flatMap(exists -> {
                    if (exists) {
                        // Username занят, пробуем следующий
                        return generateUniqueUsernameRecursive(baseUsername, counter + 1);
                    } else {
                        // Username свободен
                        return Mono.just(username);
                    }
                });
    }

    /**
     * Обновление данных Telegram для существующего пользователя.
     */
    private Mono<User> updateTelegramData(User user, TelegramAuthRequest request) {
        return Mono.fromCallable(() -> telegramMapper.updateUserFromTelegramRequest(user, request));
    }

    /**
     * Создание нового пользователя из Telegram и возврат ответа аутентификации.
     * Включает сохранение пользователя, начисление бонусных баллов и создание JWT токена.
     */
    private Mono<AuthResponse> createNewUserAndAuthResponse(TelegramAuthRequest request) {
        log.info("Создание нового пользователя из Telegram, TelegramAuthRequest: {}", request);
        return createNewUserFromTelegram(request)
                .flatMap(user -> userService.saveUser(user)
                        .flatMap(savedUser -> {
                            // Начисляем поинты за регистрацию и создаем дефолтную сессию
                            return userPointsService.addPointsToUser(savedUser.getId(), generationProperties.getPointsOnRegistration())
                                    .then(sessionService.createSession(savedUser.getId(), "Моя студия"))
                                    .then(Mono.defer(() -> Mono.just(createAuthResponse(savedUser, true))));
                        }));
    }

    /**
     * Создание ответа аутентификации.
     */
    private AuthResponse createAuthResponse(User user) {
        return createAuthResponse(user, false);
    }

    /**
     * Создание ответа аутентификации с флагом нового пользователя.
     */
    private AuthResponse createAuthResponse(User user, boolean isNewUser) {
        String token = jwtService.generateToken(user);
        AuthResponse response = new AuthResponse(
                token,
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                LocalDateTime.now().plusSeconds(expiration / 1000),
                isNewUser
        );
        return response;
    }

    /**
     * Конвертация байтов в hex строку.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}