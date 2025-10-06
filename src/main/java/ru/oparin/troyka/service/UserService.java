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

@RequiredArgsConstructor
@Service
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final ImageGenerationHistoryRepository imageGenerationHistoryRepository;

    public Mono<UserInfoDTO> getCurrentUser() {
        return SecurityUtil.getCurrentUsername()
                .flatMap(username -> {
                    log.info("Поиск пользователя по имени: {}", username);
                    return userRepository.findByUsername(username);
                })
                .map(user -> {
                    UserInfoDTO userInfoDTO = UserInfoDTO.fromUser(user);
                    log.info("Найден пользователь: {}", userInfoDTO);
                    return userInfoDTO;
                });
    }

    public Flux<ImageGenerationHistoryDTO> getCurrentUserImageHistory() {
        return SecurityUtil.getCurrentUsername()
                .flatMap(userRepository::findByUsername)
                .flatMapMany(user -> imageGenerationHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .map(ImageGenerationHistoryDTO::fromEntity);
    }

    public Mono<User> saveUser(User user) {
        return userRepository.save(user);
    }

    public Mono<User> findByUsernameOrThrow(String username) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new AuthException(
                        HttpStatus.NOT_FOUND,
                        "Пользователь не найден"
                )));
    }

    public Mono<Void> existsByUsernameOrEmail(String username, String email) {
        return userRepository.existsByUsername(username)
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
        return userRepository.existsByEmail(email)
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