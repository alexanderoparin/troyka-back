package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.exception.AuthException;
import ru.oparin.troyka.model.dto.ImageGenerationHistoryDTO;
import ru.oparin.troyka.model.dto.UserInfoDTO;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.repository.ImageGenerationHistoryRepository;
import ru.oparin.troyka.repository.UserRepository;
import ru.oparin.troyka.util.SecurityUtil;

import static ru.oparin.troyka.config.DatabaseConfig.withRetry;

@RequiredArgsConstructor
@Service
@Slf4j
public class UserService {

    // Константы для сообщений об ошибках
    private static final String USER_NOT_FOUND = "Пользователь не найден";
    private static final String USERNAME_ALREADY_EXISTS = "Пользователь с таким именем уже существует";
    private static final String EMAIL_ALREADY_EXISTS = "Пользователь с таким email уже существует";
    private static final String USERNAME_SAME_AS_CURRENT = "Новое имя пользователя должно отличаться от текущего";
    private static final String EMAIL_SAME_AS_CURRENT = "Новый email должен отличаться от текущего";

    private final UserRepository userRepository;
    private final ImageGenerationHistoryRepository imageGenerationHistoryRepository;

    public Mono<UserInfoDTO> getCurrentUser() {
        return SecurityUtil.getCurrentUsername()
                .flatMap(userRepository::findByUsername)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Пользователь не найден в БД для валидного токена");
                    return Mono.empty();
                }))
                .map(UserInfoDTO::fromUser);
    }

    public Flux<ImageGenerationHistoryDTO> getCurrentUserImageHistory() {
        return SecurityUtil.getCurrentUsername()
                .flatMap(userRepository::findByUsername)
                .flatMapMany(user -> imageGenerationHistoryRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(user.getId()))
                .map(ImageGenerationHistoryDTO::fromEntity);
    }

    public Mono<User> saveUser(User user) {
        return withRetry(userRepository.save(user));
    }

    public Mono<User> findById(Long id) {
        return withRetry(userRepository.findById(id));
    }

    public Mono<User> findByUsernameOrThrow(String username) {
        return withRetry(userRepository.findByUsernameIgnoreCase(username))
                .switchIfEmpty(Mono.error(new AuthException(HttpStatus.NOT_FOUND, "Пользователь c username" + username + " не найден")));
    }

    public Mono<User> findByIdOrThrow(Long id) {
        return withRetry(userRepository.findById(id))
                .switchIfEmpty(Mono.error(new AuthException(HttpStatus.NOT_FOUND, "Пользователь c id" + id + " не найден")));
    }

    public Mono<User> findByEmail(String email) {
        return withRetry(userRepository.findByEmailIgnoreCase(email));
    }

    /**
     * Найти пользователя по username или email (без учета регистра).
     * Сначала пытается найти по username, если не найден - ищет по email.
     *
     * @param usernameOrEmail username или email адрес
     * @return найденный пользователь или ошибка, если не найден
     */
    public Mono<User> findByUsernameOrEmail(String usernameOrEmail) {
        String trimmed = usernameOrEmail.trim();
        
        // Сначала пытаемся найти по username (без учета регистра)
        return withRetry(userRepository.findByUsernameIgnoreCase(trimmed))
                .switchIfEmpty(
                        // Если не найден по username, пробуем найти по email (без учета регистра)
                        withRetry(userRepository.findByEmailIgnoreCase(trimmed))
                                .switchIfEmpty(Mono.defer(() -> {
                                    log.warn("Пользователь не найден ни по username, ни по email: {}", trimmed);
                                    return Mono.error(new AuthException(
                                            HttpStatus.NOT_FOUND,
                                            "Пользователь " + trimmed + " не найден ни по логину, ни по почте"
                                    ));
                                }))
                );
    }

    public Mono<Void> existsByUsernameOrEmail(String username, String email) {
        return withRetry(userRepository.existsByUsername(username))
                .flatMap(usernameExists -> usernameExists
                        ? Mono.error(createUsernameConflictError())
                        : existsByEmail(email));
    }

    public Mono<Boolean> existsByUsername(String username) {
        return withRetry(userRepository.existsByUsername(username));
    }

    public Mono<User> findByTelegramId(Long telegramId) {
        return withRetry(userRepository.findByTelegramId(telegramId));
    }

    private Mono<Void> existsByEmail(String email) {
        return withRetry(userRepository.existsByEmail(email))
                .flatMap(emailExists -> emailExists
                        ? Mono.error(createEmailConflictError())
                        : Mono.empty());
    }

    /**
     * Обновление имени пользователя текущего авторизованного пользователя.
     */
    public Mono<User> updateUsername(String newUsername) {
        String trimmedUsername = newUsername.trim();
        
        return SecurityUtil.getCurrentUsername()
                .flatMap(currentUsername -> validateUsernameChange(currentUsername, trimmedUsername)
                        .then(findByUsernameOrThrow(currentUsername))
                        .flatMap(user -> updateUserUsername(user, trimmedUsername)));
    }

    private Mono<Void> validateUsernameChange(String currentUsername, String newUsername) {
        if (currentUsername.equals(newUsername)) {
            return Mono.error(createUsernameSameAsCurrentError());
        }
        return existsByUsername(newUsername)
                .flatMap(exists -> exists
                        ? Mono.error(createUsernameConflictError())
                        : Mono.empty());
    }

    private Mono<User> updateUserUsername(User user, String newUsername) {
        user.setUsername(newUsername);
        return saveUser(user);
    }

    /**
     * Обновление email текущего авторизованного пользователя.
     */
    public Mono<User> updateEmail(String newEmail) {
        String trimmedEmail = newEmail.trim();
        
        return SecurityUtil.getCurrentUsername()
                .flatMap(this::findByUsernameOrThrow)
                .flatMap(user -> validateEmailChange(user, trimmedEmail)
                        .then(updateUserEmail(user, trimmedEmail)));
    }

    private Mono<Void> validateEmailChange(User user, String newEmail) {
        if (newEmail.equals(user.getEmail())) {
            return Mono.error(createEmailSameAsCurrentError());
        }
        return withRetry(userRepository.existsByEmail(newEmail))
                .flatMap(exists -> exists
                        ? Mono.error(createEmailConflictError())
                        : Mono.empty());
    }

    private Mono<User> updateUserEmail(User user, String newEmail) {
        user.setEmail(newEmail);
        user.setEmailVerified(false);
        return saveUser(user);
    }

    private AuthException createUsernameConflictError() {
        return new AuthException(HttpStatus.CONFLICT, USERNAME_ALREADY_EXISTS);
    }

    private AuthException createEmailConflictError() {
        return new AuthException(HttpStatus.CONFLICT, EMAIL_ALREADY_EXISTS);
    }

    private AuthException createUsernameSameAsCurrentError() {
        return new AuthException(HttpStatus.BAD_REQUEST, USERNAME_SAME_AS_CURRENT);
    }

    private AuthException createEmailSameAsCurrentError() {
        return new AuthException(HttpStatus.BAD_REQUEST, EMAIL_SAME_AS_CURRENT);
    }
}