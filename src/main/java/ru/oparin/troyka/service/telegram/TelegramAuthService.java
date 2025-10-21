package ru.oparin.troyka.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.exception.AuthException;
import ru.oparin.troyka.mapper.TelegramMapper;
import ru.oparin.troyka.model.dto.auth.AuthResponse;
import ru.oparin.troyka.model.dto.auth.TelegramAuthRequest;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.model.enums.Role;
import ru.oparin.troyka.service.JwtService;
import ru.oparin.troyka.service.UserPointsService;
import ru.oparin.troyka.service.UserService;

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
    private final TelegramMapper telegramMapper;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${telegram.login.bot-token}")
    private String telegramBotToken;

    /**
     * Вход через Telegram Login Widget.
     * Создает нового пользователя или находит существующего по telegram_id.
     *
     * @param request данные от Telegram Login Widget
     * @return JWT токен и информация о пользователе
     */
    public Mono<AuthResponse> loginWithTelegram(TelegramAuthRequest request) {
        log.info("Попытка входа через Telegram для пользователя с ID: {}", request.getId());

        return validateTelegramData(request)
                .then(Mono.defer(() -> {
                    // Ищем пользователя по telegram_id
                    return userService.findByTelegramId(request.getId())
                            .flatMap(existingUser -> {
                                log.info("Найден существующий пользователь с telegram_id: {}", request.getId());
                                return updateTelegramData(existingUser, request)
                                        .flatMap(userService::saveUser)
                                        .map(this::createAuthResponse);
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                // Проверяем, есть ли пользователь с таким username
                                if (request.getUsername() != null) {
                                    return userService.findByUsernameOrThrow(request.getUsername())
                                            .flatMap(existingUser -> {
                                                log.info("Найден существующий пользователь с username: {}, привязываем Telegram", request.getUsername());
                                                return updateTelegramData(existingUser, request)
                                                        .flatMap(userService::saveUser)
                                                        .map(this::createAuthResponse);
                                            })
                                            .onErrorResume(AuthException.class, e -> {
                                                // Пользователь не найден, создаем нового
                                                log.info("Создание нового пользователя для telegram_id: {}", request.getId());
                                                return createUserFromTelegram(request)
                                                        .flatMap(user -> userService.saveUser(user)
                                                                .flatMap(savedUser -> userPointsService.addPointsToUser(savedUser.getId(), 6)
                                                                        .then(Mono.just(createAuthResponse(savedUser)))));
                                            });
                                } else {
                                    // Создаем нового пользователя без username
                                    log.info("Создание нового пользователя для telegram_id: {}", request.getId());
                                    return createUserFromTelegram(request)
                                            .flatMap(user -> userService.saveUser(user)
                                                    .flatMap(savedUser -> userPointsService.addPointsToUser(savedUser.getId(), 6)
                                                            .then(Mono.just(createAuthResponse(savedUser)))));
                                }
                            }));
                }))
                .doOnSuccess(response -> log.info("Успешный вход через Telegram для пользователя: {}", response.getUsername()))
                .doOnError(error -> log.error("Ошибка входа через Telegram для ID: {}", request.getId(), error));
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
        log.info("Привязка Telegram к существующему пользователю: {}", currentUserId);

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
                                return userService.findById(currentUserId)
                                        .switchIfEmpty(Mono.error(new AuthException(
                                                HttpStatus.NOT_FOUND,
                                                "Пользователь не найден"
                                        )))
                                        .flatMap(user -> updateTelegramData(user, request)
                                                .flatMap(userService::saveUser));
                            }));
                }))
                .map(this::createAuthResponse)
                .doOnSuccess(response -> log.info("Telegram успешно привязан к пользователю: {}", response.getUsername()))
                .doOnError(error -> log.error("Ошибка привязки Telegram к пользователю: {}", currentUserId, error));
    }

    /**
     * Отвязка Telegram от аккаунта.
     *
     * @param userId ID пользователя
     * @return результат операции
     */
    public Mono<Void> unlinkTelegram(Long userId) {
        log.info("Отвязка Telegram от пользователя: {}", userId);

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
                    user.setTelegramNotificationsEnabled(false);

                    return userService.saveUser(user);
                })
                .then()
                .doOnSuccess(v -> log.info("Telegram отвязан от пользователя: {}", userId))
                .doOnError(error -> log.error("Ошибка отвязки Telegram от пользователя: {}", userId, error));
    }

    /**
     * Валидация данных от Telegram Login Widget.
     * Проверяет подпись и актуальность данных.
     */
    private Mono<Void> validateTelegramData(TelegramAuthRequest request) {
        return Mono.fromCallable(() -> {
            log.info("Валидация данных Telegram для пользователя с ID: {}", request.getId());
            log.debug("Данные запроса: id={}, first_name={}, username={}, auth_date={}", 
                request.getId(), request.getFirst_name(), request.getUsername(), request.getAuth_date());
            
            // Проверяем актуальность данных (не старше 24 часов)
            long currentTime = Instant.now().getEpochSecond();
            log.debug("Текущее время (Unix timestamp): {}", currentTime);
            log.debug("Время авторизации (Unix timestamp): {}", request.getAuth_date());
            log.debug("Разница во времени: {} секунд", currentTime - request.getAuth_date());
            
            if (currentTime - request.getAuth_date() > 86400) { // 24 часа
                throw new AuthException(HttpStatus.BAD_REQUEST, "Данные авторизации устарели");
            }

            // Создаем строку для проверки подписи
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

            log.debug("Отладка валидации Telegram, params = {}:", params);

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

            // Проверяем подпись
            log.debug("Отладка валидации Telegram:");
            log.debug("Строка для проверки: {}", dataCheckString);
            log.debug("Токен бота (первые 10 символов): {}", telegramBotToken.substring(0, Math.min(10, telegramBotToken.length())));
            log.debug("Полный токен бота: {}", telegramBotToken);
            log.debug("Secret key (SHA256 байты): {}", bytesToHex(secretKeyBytes));
            log.debug("Вычисленная подпись: {}", calculatedHash);
            log.debug("Полученная подпись: {}", request.getHash());
            
            if (!calculatedHash.equals(request.getHash())) {
                throw new AuthException(HttpStatus.UNAUTHORIZED, "Неверная подпись Telegram");
            }

            return null;
        });
    }


    /**
     * Создание нового пользователя из данных Telegram.
     */
    private Mono<User> createUserFromTelegram(TelegramAuthRequest request) {
        return generateUniqueUsername(request.getUsername(), request.getId())
                .map(username -> {
                    String email = "telegram_" + request.getId() + "@telegram.local";

                    return User.builder()
                            .username(username)
                            .email(email)
                            .password(passwordEncoder.encode("telegram_auth_" + request.getId())) // Временный пароль
                            .role(Role.USER)
                            .emailVerified(false)
                            .telegramId(request.getId())
                            .telegramUsername(request.getUsername())
                            .telegramFirstName(request.getFirst_name())
                            .telegramPhotoUrl(request.getPhoto_url())
                            .telegramNotificationsEnabled(true)
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
        String username = counter == 1 ? baseUsername : baseUsername + "_" + counter;
        
        return userService.findByUsernameOrThrow(username)
                .then(Mono.just(username))
                .onErrorResume(AuthException.class, e -> 
                    generateUniqueUsernameRecursive(baseUsername, counter + 1)
                );
    }

    /**
     * Обновление данных Telegram для существующего пользователя.
     */
    private Mono<User> updateTelegramData(User user, TelegramAuthRequest request) {
        return Mono.fromCallable(() -> telegramMapper.updateUserFromTelegramRequest(user, request));
    }

    /**
     * Создание ответа аутентификации.
     */
    private AuthResponse createAuthResponse(User user) {
        String token = jwtService.generateToken(user);
        return new AuthResponse(
                token,
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                LocalDateTime.now().plusSeconds(expiration / 1000)
        );
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
