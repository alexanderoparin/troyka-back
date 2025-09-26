package ru.oparin.troyka.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
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
    
    public Flux<ImageGenerationHistory> saveHistories(Iterable<String> imageUrls, String prompt) {
        return SecurityUtil.getCurrentUsername()
                .flatMap(userRepository::findByUsername)
                .flatMapMany(user -> {
                    Flux<ImageGenerationHistory> histories = Flux.fromIterable(imageUrls)
                            .map(url -> ImageGenerationHistory.builder()
                                    .userId(user.getId())
                                    .imageUrl(url)
                                    .prompt(prompt)
                                    .build());
                    
                    return imageGenerationHistoryRepository.saveAll(histories)
                            .doOnNext(history ->
                                    log.info("Запись истории успешно сохранена с ID: {}", history.getId()));
                });
    }
}