package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import ru.oparin.troyka.model.entity.ImageGenerationHistory;
import ru.oparin.troyka.repository.ImageGenerationHistoryRepository;
import ru.oparin.troyka.repository.UserRepository;
import ru.oparin.troyka.util.JsonUtils;
import ru.oparin.troyka.util.SecurityUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для работы с историей генерации изображений.
 * Предоставляет методы для сохранения и получения истории генераций с поддержкой сессий.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImageGenerationHistoryService {

    private final ImageGenerationHistoryRepository imageGenerationHistoryRepository;
    private final UserRepository userRepository;

    /**
     * Сохранить историю генерации изображений в рамках сессии.
     * Для веб-сайта, где есть Spring Security контекст.
     *
     * @param imageUrls список URL сгенерированных изображений
     * @param prompt промпт пользователя
     * @param sessionId идентификатор сессии
     * @param inputImageUrls список URL входных изображений (для отображения в истории)
     * @return сохраненные записи истории
     */
    public Flux<ImageGenerationHistory> saveHistories(Iterable<String> imageUrls, String prompt, Long sessionId, List<String> inputImageUrls) {
        log.info("Сохранение истории генерации для сессии {}, промпт: {}, используемые в запросе изображения {}", sessionId, prompt, inputImageUrls);
        
        return SecurityUtil.getCurrentUsername()
                .flatMap(userRepository::findByUsername)
                .flatMapMany(user -> {
                    // Вызываем перегруженный метод с userId
                    return saveHistories(user.getId(), imageUrls, prompt, sessionId, inputImageUrls);
                });
    }

    /**
     * Сохранить историю генерации изображений для конкретного пользователя.
     * Перегрузка метода для случаев, когда userId известен напрямую (например, Telegram бот).
     *
     * @param userId ID пользователя
     * @param imageUrls список URL сгенерированных изображений
     * @param prompt промпт пользователя
     * @param sessionId идентификатор сессии
     * @param inputImageUrls список URL входных изображений (для отображения в истории)
     * @return сохраненные записи истории
     */
    public Flux<ImageGenerationHistory> saveHistories(Long userId, Iterable<String> imageUrls, String prompt, Long sessionId, List<String> inputImageUrls) {
        log.info("Сохранение истории генерации для пользователя {} в сессии {}, промпт: {}, используемые в запросе изображения {}", userId, sessionId, prompt, inputImageUrls);
        
        // Преобразуем списки в JSON строки
        List<String> imageUrlsList = new ArrayList<>();
        imageUrls.forEach(imageUrlsList::add);
        String imageUrlsJson = JsonUtils.convertListToJson(imageUrlsList);
        String inputImageUrlsJson = JsonUtils.convertListToJson(inputImageUrls);
        
        log.info("Сгенерированные изображения: {}", imageUrlsJson);
        log.info("Используемые в запросе изображения: {}", inputImageUrlsJson);
        
        // Создаем ОДНУ запись истории для всех сгенерированных изображений
        return imageGenerationHistoryRepository.saveWithJsonb(
                userId,
                imageUrlsJson,
                prompt,
                LocalDateTime.now(),
                sessionId,
                inputImageUrlsJson
        )
        .doOnNext(history -> 
                log.info("Запись истории сохранена: ID={}, пользователь={}, сессия={}, изображений={}", 
                        history.getId(), userId, sessionId, imageUrlsList.size()))
        .flux();
    }


    /**
     * Сохранить историю генерации изображений (старый метод для обратной совместимости).
     * Создает или получает дефолтную сессию пользователя.
     *
     * @param imageUrls список URL сгенерированных изображений
     * @param prompt промпт пользователя
     * @return сохраненные записи истории
     */
    public Flux<ImageGenerationHistory> saveHistories(Iterable<String> imageUrls, String prompt) {
        log.info("Сохранение истории генерации (legacy метод), промпт: {}", prompt);
        
        return SecurityUtil.getCurrentUsername()
                .flatMap(userRepository::findByUsername)
                .flatMapMany(user -> {
                    // Создаем записи без сессии (для обратной совместимости)
                    List<String> imageUrlsList = new ArrayList<>();
                    imageUrls.forEach(imageUrlsList::add);
                    String imageUrlsJson = JsonUtils.convertListToJson(imageUrlsList);
                    Flux<ImageGenerationHistory> histories = Flux.just(ImageGenerationHistory.builder()
                            .userId(user.getId())
                            .imageUrlsJson(imageUrlsJson)
                            .prompt(prompt)
                            .createdAt(LocalDateTime.now())
                            .build());
                    
                    return imageGenerationHistoryRepository.saveAll(histories)
                            .doOnNext(history -> 
                                    log.info("Запись истории сохранена (legacy): ID={}", history.getId()));
                });
    }

    /**
     * Получить историю генераций пользователя.
     *
     * @param userId идентификатор пользователя
     * @return поток записей истории пользователя
     */
    public Flux<ImageGenerationHistory> getUserImageHistory(Long userId) {
        log.info("Получение истории генераций для пользователя {}", userId);
        
        return imageGenerationHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .doOnNext(history -> log.debug("Получена запись истории: ID={}, сессия={}", history.getId(), history.getSessionId()))
                .doOnError(error -> log.error("Ошибка при получении истории генераций для пользователя {}", userId, error));
    }

    /**
     * Получить историю генераций сессии.
     *
     * @param sessionId идентификатор сессии
     * @return поток записей истории сессии
     */
    public Flux<ImageGenerationHistory> getSessionImageHistory(Long sessionId) {
        log.info("Получение истории генераций для сессии {}", sessionId);
        
        return imageGenerationHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .doOnNext(history -> log.debug("Получена запись истории сессии: ID={}, изображений={}", history.getId(), history.getImageUrls().size()))
                .doOnError(error -> log.error("Ошибка при получении истории генераций для сессии {}", sessionId, error));
    }

}