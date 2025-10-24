package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
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
}