package ru.oparin.troyka.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.ImageGenerationHistory;
import ru.oparin.troyka.repository.ImageGenerationHistoryRepository;
import ru.oparin.troyka.repository.UserRepository;
import ru.oparin.troyka.util.SecurityUtil;

@Service
@Slf4j
public class ImageGenerationHistoryService {

    private final ImageGenerationHistoryRepository imageGenerationHistoryRepository;
    private final UserRepository userRepository;

    public ImageGenerationHistoryService(ImageGenerationHistoryRepository imageGenerationHistoryRepository,
                                         UserRepository userRepository) {
        this.imageGenerationHistoryRepository = imageGenerationHistoryRepository;
        this.userRepository = userRepository;
    }

    public Mono<ImageGenerationHistory> saveHistory(String imageUrl, String prompt) {
        return SecurityUtil.getCurrentUsername()
                .flatMap(userRepository::findByUsername)
                .map(user -> {
                    ImageGenerationHistory history = ImageGenerationHistory.builder()
                            .userId(user.getId())
                            .imageUrl(imageUrl)
                            .prompt(prompt)
                            .build();
                    log.info("Сохранение записи истории генерации изображений: {}", history);
                    return history;
                })
                .flatMap(imageGenerationHistoryRepository::save)
                .doOnNext(history -> log.info("Запись истории успешно сохранена с ID: {}", history.getId()));
    }
}