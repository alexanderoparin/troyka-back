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
     * Автоматически вычисляет номер итерации и обновляет время сессии.
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
                    // Получаем следующий номер итерации
                    return imageGenerationHistoryRepository.findBySessionIdOrderByIterationNumberAsc(sessionId)
                            .collectList()
                            .map(histories -> {
                                if (histories.isEmpty()) {
                                    return 1;
                                }
                                return histories.stream()
                                        .mapToInt(ImageGenerationHistory::getIterationNumber)
                                        .max()
                                        .orElse(0) + 1;
                            })
                            .flatMapMany(iterationNumber -> {
                                // Преобразуем inputImageUrls в JSON строку
                                String inputImageUrlsJson = JsonUtils.convertListToJson(inputImageUrls);
                                log.info("Используемые в запросе изображения: {}", inputImageUrlsJson);
                                
                                // Создаем записи истории для каждого сгенерированного изображения
                                // Используем кастомный метод с правильным приведением JSONB
                                return Flux.fromIterable(imageUrls)
                                        .flatMap(url -> imageGenerationHistoryRepository.saveWithJsonb(
                                                user.getId(),
                                                url,
                                                prompt,
                                                LocalDateTime.now(),
                                                sessionId,
                                                iterationNumber,
                                                inputImageUrlsJson
                                        ))
                                        .doOnNext(history -> 
                                                log.info("Запись истории сохранена: ID={}, сессия={}, итерация={}", 
                                                        history.getId(), sessionId, iterationNumber));
                            });
                });
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
                    Flux<ImageGenerationHistory> histories = Flux.fromIterable(imageUrls)
                            .map(url -> ImageGenerationHistory.builder()
                                    .userId(user.getId())
                                    .imageUrl(url)
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
        
        return imageGenerationHistoryRepository.findBySessionIdOrderByIterationNumberAsc(sessionId)
                .doOnNext(history -> log.debug("Получена запись истории сессии: ID={}, итерация={}", history.getId(), history.getIterationNumber()))
                .doOnError(error -> log.error("Ошибка при получении истории генераций для сессии {}", sessionId, error));
    }

}