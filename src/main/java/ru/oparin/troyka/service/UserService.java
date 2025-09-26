package ru.oparin.troyka.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.ImageGenerationHistoryDTO;
import ru.oparin.troyka.model.dto.UserInfoDTO;
import ru.oparin.troyka.repository.ImageGenerationHistoryRepository;
import ru.oparin.troyka.repository.UserRepository;
import ru.oparin.troyka.util.SecurityUtil;

@Service
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final ImageGenerationHistoryRepository imageGenerationHistoryRepository;

    public UserService(UserRepository userRepository,
                       ImageGenerationHistoryRepository imageGenerationHistoryRepository) {
        this.userRepository = userRepository;
        this.imageGenerationHistoryRepository = imageGenerationHistoryRepository;
    }

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
                .flatMapMany(user -> {
                    log.info("Получение истории генерации изображений для пользователя: {}", user.getUsername());
                    return imageGenerationHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
                })
                .map(ImageGenerationHistoryDTO::fromEntity)
                .doOnNext(history -> log.info("Найдена запись истории: {}", history));
    }
}