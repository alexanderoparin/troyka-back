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

    private final UserRepository userRepository;
    private final ImageGenerationHistoryRepository imageGenerationHistoryRepository;

    public Mono<UserInfoDTO> getCurrentUser() {
        return SecurityUtil.getCurrentUsername()
                .flatMap(userRepository::findByUsername)
                .map(UserInfoDTO::fromUser);
    }

    public Flux<ImageGenerationHistoryDTO> getCurrentUserImageHistory() {
        return SecurityUtil.getCurrentUsername()
                .flatMap(userRepository::findByUsername)
                .flatMapMany(user -> imageGenerationHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .map(ImageGenerationHistoryDTO::fromEntity);
    }

    public Mono<User> saveUser(User user) {
        return withRetry(userRepository.save(user));
    }

    public Mono<User> findById(Long id) {
        return withRetry(userRepository.findById(id));
    }

    public Mono<User> findByUsernameOrThrow(String username) {
        return withRetry(userRepository.findByUsername(username))
                .switchIfEmpty(Mono.error(new AuthException(
                        HttpStatus.NOT_FOUND,
                        "Пользователь не найден"
                )));
    }

    public Mono<User> findByIdOrThrow(Long id) {
        return withRetry(userRepository.findById(id))
                .switchIfEmpty(Mono.error(new AuthException(
                        HttpStatus.NOT_FOUND,
                        "Пользователь не найден"
                )));
    }

    public Mono<User> findByEmail(String email) {
        return withRetry(userRepository.findByEmail(email));
    }

    public Mono<Void> existsByUsernameOrEmail(String username, String email) {
        return withRetry(userRepository.existsByUsername(username))
                .flatMap(usernameExists -> {
                    if (usernameExists) {
                        return Mono.error(new AuthException(
                                HttpStatus.CONFLICT,
                                "Пользователь с таким именем уже существует"
                        ));
                    } else return existsByEmail(email);
                });
    }

    public Mono<Boolean> existsByUsername(String username) {
        return withRetry(userRepository.existsByUsername(username));
    }

    public Mono<Void> existsByEmail(String email) {
        return withRetry(userRepository.existsByEmail(email))
                .flatMap(emailExists -> {
                    if (emailExists) {
                        return Mono.error(new AuthException(
                                HttpStatus.CONFLICT,
                                "Пользователь с таким email уже существует"
                        ));
                    } else return Mono.empty();
                });
    }

    public Mono<User> findByTelegramId(Long telegramId) {
        return withRetry(userRepository.findByTelegramId(telegramId));
    }

    public Mono<Boolean> existsByTelegramId(Long telegramId) {
        return withRetry(userRepository.existsByTelegramId(telegramId));
    }

    public Mono<Void> existsByUsernameOrEmailOrTelegramId(String username, String email, Long telegramId) {
        return withRetry(userRepository.existsByUsername(username))
                .flatMap(usernameExists -> {
                    if (usernameExists) {
                        return Mono.error(new AuthException(
                                HttpStatus.CONFLICT,
                                "Пользователь с таким именем уже существует"
                        ));
                    } else {
                        return withRetry(userRepository.existsByEmail(email))
                                .flatMap(emailExists -> {
                                    if (emailExists) {
                                        return Mono.error(new AuthException(
                                                HttpStatus.CONFLICT,
                                                "Пользователь с таким email уже существует"
                                        ));
                                    } else {
                                        return withRetry(userRepository.existsByTelegramId(telegramId))
                                                .flatMap(telegramExists -> {
                                                    if (telegramExists) {
                                                        return Mono.error(new AuthException(
                                                                HttpStatus.CONFLICT,
                                                                "Пользователь с таким Telegram ID уже существует"
                                                        ));
                                                    } else {
                                                        return Mono.empty();
                                                    }
                                                });
                                    }
                                });
                    }
                });
    }

    /**
     * Обновление имени пользователя текущего авторизованного пользователя.
     */
    public Mono<User> updateUsername(String newUsername) {
        return SecurityUtil.getCurrentUsername()
                .flatMap(currentUsername -> {
                    // Проверяем, что новое имя отличается от текущего
                    if (currentUsername.equals(newUsername)) {
                        return Mono.error(new AuthException(
                                HttpStatus.BAD_REQUEST,
                                "Новое имя пользователя должно отличаться от текущего"
                        ));
                    }
                    
                    // Проверяем, что новое имя не занято
                    return existsByUsername(newUsername)
                            .flatMap(exists -> {
                                if (exists) {
                                    return Mono.error(new AuthException(
                                            HttpStatus.CONFLICT,
                                            "Пользователь с таким именем уже существует"
                                    ));
                                }
                                
                                // Обновляем имя пользователя
                                return findByUsernameOrThrow(currentUsername)
                                        .flatMap(user -> {
                                            user.setUsername(newUsername);
                                            return saveUser(user);
                                        });
                            });
                });
    }

    /**
     * Обновление email текущего авторизованного пользователя.
     */
    public Mono<User> updateEmail(String newEmail) {
        return SecurityUtil.getCurrentUsername()
                .flatMap(currentUsername -> {
                    // Получаем текущего пользователя
                    return findByUsernameOrThrow(currentUsername)
                            .flatMap(user -> {
                                // Проверяем, что новый email отличается от текущего
                                if (newEmail.equals(user.getEmail())) {
                                    return Mono.error(new AuthException(
                                            HttpStatus.BAD_REQUEST,
                                            "Новый email должен отличаться от текущего"
                                    ));
                                }
                                
                                // Проверяем, что новый email не занят
                                return withRetry(userRepository.existsByEmail(newEmail))
                                        .flatMap(exists -> {
                                            if (exists) {
                                                return Mono.error(new AuthException(
                                                        HttpStatus.CONFLICT,
                                                        "Пользователь с таким email уже существует"
                                                ));
                                            }
                                            
                                            // Обновляем email и сбрасываем статус подтверждения
                                            user.setEmail(newEmail);
                                            user.setEmailVerified(false);
                                            return saveUser(user);
                                        });
                            });
                });
    }
}