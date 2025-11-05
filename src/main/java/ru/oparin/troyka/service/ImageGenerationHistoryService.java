package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.ImageGenerationHistory;
import ru.oparin.troyka.repository.ImageGenerationHistoryRepository;
import ru.oparin.troyka.util.JsonUtils;

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

    /**
     * Сохранить историю генерации изображений для конкретного пользователя.
     * Перегрузка метода для случаев, когда userId известен напрямую (например, Telegram бот).
     *
     * @param userId         ID пользователя
     * @param imageUrls      список URL сгенерированных изображений
     * @param prompt         промпт пользователя
     * @param sessionId      идентификатор сессии
     * @param inputImageUrls список URL входных изображений (для отображения в истории)
     * @param description    описание изображения от ИИ (может быть null)
     * @param styleId        идентификатор стиля (по умолчанию 1 - Без стиля)
     * @return сохраненные записи истории
     */
    public Flux<ImageGenerationHistory> saveHistories(Long userId, Iterable<String> imageUrls, String prompt, Long sessionId, List<String> inputImageUrls, String description, Long styleId) {
        log.info("Сохранение истории генерации для пользователя {} в сессии {}, промпт: {}, используемые в запросе изображения {}, styleId: {}", userId, sessionId, prompt, inputImageUrls, styleId);

        // Преобразуем списки в JSON строки
        List<String> imageUrlsList = new ArrayList<>();
        imageUrls.forEach(imageUrlsList::add);
        String imageUrlsJson = JsonUtils.convertListToJson(imageUrlsList);
        String inputImageUrlsJson = JsonUtils.convertListToJson(inputImageUrls);

        // Устанавливаем дефолтное значение styleId = 1, если не указано
        Long finalStyleId = (styleId != null) ? styleId : 1L;

        log.info("Сгенерированные изображения: {}", imageUrlsJson);
        log.info("Используемые в запросе изображения: {}", inputImageUrlsJson);
        if (description != null && !description.trim().isEmpty()) {
            log.info("Описание от ИИ: {}", description);
        }

        // Создаем ОДНУ запись истории для всех сгенерированных изображений
        return imageGenerationHistoryRepository.saveWithJsonb(
                        userId,
                        imageUrlsJson,
                        prompt,
                        LocalDateTime.now(),
                        sessionId,
                        inputImageUrlsJson,
                        description,
                        finalStyleId
                )
                .doOnNext(history -> log.info("Запись истории сохранена: {}", history))
                .flux();
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
     * Получить URL последнего сгенерированного изображения из конкретной сессии.
     *
     * @param userId    идентификатор пользователя
     * @param sessionId идентификатор сессии
     * @return URL последнего изображения из сессии или пустой результат
     */
    public Mono<String> getLastGeneratedImageUrlFromSession(Long userId, Long sessionId) {
        log.info("Получение URL последнего сгенерированного изображения для пользователя {} из сессии {}", userId, sessionId);

        return imageGenerationHistoryRepository.findByUserIdAndSessionIdOrderByCreatedAtDesc(userId, sessionId)
                .next() // Берем только первую (последнюю) запись из сессии
                .flatMap(history -> {
                    List<String> imageUrls = JsonUtils.parseJsonToList(history.getImageUrls());
                    if (imageUrls != null && !imageUrls.isEmpty()) {
                        String lastImageUrl = imageUrls.get(imageUrls.size() - 1); // Берем последнее изображение
                        log.info("Найден URL последнего изображения для пользователя {} из сессии {}: {}", userId, sessionId, lastImageUrl);
                        return Mono.just(lastImageUrl);
                    } else {
                        log.warn("Нет изображений в последней записи истории для пользователя {} из сессии {}", userId, sessionId);
                        return Mono.empty();
                    }
                })
                .doOnError(error -> log.error("Ошибка при получении URL последнего изображения для пользователя {} из сессии {}", userId, sessionId, error));
    }
}