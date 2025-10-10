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
                .flatMap(username -> withRetry(userRepository.findByUsername(username)))
                .map(UserInfoDTO::fromUser);
    }

    public Flux<ImageGenerationHistoryDTO> getCurrentUserImageHistory() {
        return SecurityUtil.getCurrentUsername()
                .flatMap(username -> withRetry(userRepository.findByUsername(username)))
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
}