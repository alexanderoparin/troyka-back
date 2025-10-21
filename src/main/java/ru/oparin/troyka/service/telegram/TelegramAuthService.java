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
import ru.oparin.troyka.model.dto.auth.AuthResponse;
import ru.oparin.troyka.model.dto.auth.TelegramLinkRequest;
import ru.oparin.troyka.model.dto.auth.TelegramLoginRequest;
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
    public Mono<AuthResponse> loginWithTelegram(TelegramLoginRequest request) {
        log.info("Попытка входа через Telegram для пользователя с ID: {}", request.getId());

        return validateTelegramData(request)
                .then(Mono.defer(() -> {
                    // Ищем пользователя по telegram_id
                    return userService.findByTelegramId(request.getId())
                            .flatMap(existingUser -> {
                                log.info("Найден существующий пользователь с telegram_id: {}", request.getId());
                                return updateTelegramData(existingUser, request)
                                        .map(this::createAuthResponse);
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                // Создаем нового пользователя
                                log.info("Создание нового пользователя для telegram_id: {}", request.getId());
                                return createUserFromTelegram(request)
                                        .flatMap(user -> userService.saveUser(user)
                                                .flatMap(savedUser -> userPointsService.addPointsToUser(savedUser.getId(), 6)
                                                        .then(Mono.just(createAuthResponse(savedUser)))));
                            }));
                }))
                .doOnSuccess(response -> log.info("Успешный вход через Telegram для пользователя: {}", response.getUsername()))
                .doOnError(error -> log.error("Ошибка входа через Telegram для ID: {}", request.getId()));
    }

    /**
     * Привязка Telegram к существующему аккаунту.
     * Пользователь должен быть авторизован в системе.
     *
     * @param request данные от Telegram Login Widget
     * @param currentUserId ID текущего авторизованного пользователя
     * @return JWT токен и обновленная информация о пользователе
     */
    public Mono<AuthResponse> linkTelegramToExistingUser(TelegramLinkRequest request, Long currentUserId) {
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
    private Mono<Void> validateTelegramData(TelegramLoginRequest request) {
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

            // Вычисляем подпись согласно официальной документации Telegram
            // secret_key = SHA256(bot_token)
            String secretKey = DigestUtils.sha256Hex(telegramBotToken);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));
            String calculatedHash = bytesToHex(hash);

            // Проверяем подпись
            log.debug("Отладка валидации Telegram:");
            log.debug("Строка для проверки: {}", dataCheckString);
            log.debug("Токен бота (первые 10 символов): {}", telegramBotToken.substring(0, Math.min(10, telegramBotToken.length())));
            log.debug("Полный токен бота: {}", telegramBotToken);
            log.debug("Secret key (SHA256 токена): {}", secretKey);
            log.debug("Вычисленная подпись: {}", calculatedHash);
            log.debug("Полученная подпись: {}", request.getHash());
            
            // Пробуем альтернативный алгоритм - прямой токен
            SecretKeySpec directSecretKeySpec = new SecretKeySpec(telegramBotToken.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac directMac = Mac.getInstance("HmacSHA256");
            directMac.init(directSecretKeySpec);
            byte[] directHash = directMac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));
            String directCalculatedHash = bytesToHex(directHash);
            
            log.debug("Альтернативная подпись (прямой токен): {}", directCalculatedHash);
            
            // Пробуем третий алгоритм - фиксированный порядок как в примере
            String fixedOrderHash = calculateFixedOrderHash(request, telegramBotToken);
            log.debug("Фиксированный порядок подпись: {}", fixedOrderHash);
            
            // Проверяем все три варианта
            if (!calculatedHash.equals(request.getHash()) && 
                !directCalculatedHash.equals(request.getHash()) &&
                !fixedOrderHash.equals(request.getHash())) {
                throw new AuthException(HttpStatus.UNAUTHORIZED, "Неверная подпись Telegram");
            }

            return null;
        });
    }

    /**
     * Валидация данных от Telegram Login Widget (для привязки).
     */
    private Mono<Void> validateTelegramData(TelegramLinkRequest request) {
        return Mono.fromCallable(() -> {
            // Проверяем актуальность данных (не старше 24 часов)
            long currentTime = Instant.now().getEpochSecond();
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

            // Вычисляем подпись согласно официальной документации Telegram
            // secret_key = SHA256(bot_token)
            String secretKey = DigestUtils.sha256Hex(telegramBotToken);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));
            String calculatedHash = bytesToHex(hash);

            // Проверяем подпись
            if (!calculatedHash.equals(request.getHash())) {
                throw new AuthException(HttpStatus.UNAUTHORIZED, "Неверная подпись Telegram");
            }

            return null;
        });
    }

    /**
     * Создание нового пользователя из данных Telegram.
     */
    private Mono<User> createUserFromTelegram(TelegramLoginRequest request) {
        return Mono.fromCallable(() -> {
            String username = request.getUsername() != null ? 
                    request.getUsername() : 
                    "tg_" + request.getId();
            
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
     * Обновление данных Telegram для существующего пользователя.
     */
    private Mono<User> updateTelegramData(User user, TelegramLoginRequest request) {
        return Mono.fromCallable(() -> {
            user.setTelegramId(request.getId());
            user.setTelegramUsername(request.getUsername());
            user.setTelegramFirstName(request.getFirst_name());
            user.setTelegramPhotoUrl(request.getPhoto_url());
            if (user.getTelegramNotificationsEnabled() == null) {
                user.setTelegramNotificationsEnabled(true);
            }
            return user;
        });
    }

    /**
     * Обновление данных Telegram для существующего пользователя (для привязки).
     */
    private Mono<User> updateTelegramData(User user, TelegramLinkRequest request) {
        return Mono.fromCallable(() -> {
            user.setTelegramId(request.getId());
            user.setTelegramUsername(request.getUsername());
            user.setTelegramFirstName(request.getFirst_name());
            user.setTelegramPhotoUrl(request.getPhoto_url());
            if (user.getTelegramNotificationsEnabled() == null) {
                user.setTelegramNotificationsEnabled(true);
            }
            return user;
        });
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

    /**
     * Альтернативный алгоритм с фиксированным порядком параметров (как в примере из интернета).
     */
    private String calculateFixedOrderHash(TelegramLoginRequest request, String botToken) {
        try {
            // Создаем строку в фиксированном порядке как в примере
            StringBuilder dataCheckString = new StringBuilder();
            
            // Добавляем поля в определенном порядке (как в примере)
            addField(dataCheckString, "auth_date", request.getAuth_date().toString());
            addField(dataCheckString, "first_name", request.getFirst_name());
            addField(dataCheckString, "id", request.getId().toString());
            addField(dataCheckString, "last_name", request.getLast_name());
            addField(dataCheckString, "photo_url", request.getPhoto_url());
            addField(dataCheckString, "username", request.getUsername());
            
            // Убираем последний символ новой строки
            if (!dataCheckString.isEmpty()) {
                dataCheckString.setLength(dataCheckString.length() - 1);
            }

            log.debug("Фиксированный порядок строка: {}", dataCheckString.toString());
            
            // Используем прямой токен как секретный ключ
            SecretKeySpec secretKeySpec = new SecretKeySpec(botToken.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(dataCheckString.toString().getBytes(StandardCharsets.UTF_8));
            
            return bytesToHex(hash);
        } catch (Exception e) {
            log.error("Ошибка при вычислении фиксированного порядка подписи", e);
            return "";
        }
    }

    /**
     * Добавление поля в строку для проверки (как в примере).
     */
    private void addField(StringBuilder sb, String key, String value) {
        if (value != null && !value.isEmpty()) {
            sb.append(key).append("=").append(value).append("\n");
        }
    }

}
