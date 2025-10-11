package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.PageResponseDTO;
import ru.oparin.troyka.model.dto.SessionDTO;
import ru.oparin.troyka.model.dto.SessionDetailDTO;
import ru.oparin.troyka.model.dto.SessionMessageDTO;
import ru.oparin.troyka.model.entity.ImageGenerationHistory;
import ru.oparin.troyka.model.entity.Session;
import ru.oparin.troyka.repository.ImageGenerationHistoryRepository;
import ru.oparin.troyka.repository.SessionRepository;
import ru.oparin.troyka.util.JsonUtils;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис для работы с сессиями генерации изображений.
 * Предоставляет методы для создания, получения, обновления и удаления сессий.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final ImageGenerationHistoryRepository imageHistoryRepository;

    /**
     * Создать новую сессию для пользователя.
     * Название будет сгенерировано автоматически как "Сессия {id}".
     *
     * @param userId идентификатор пользователя
     * @return созданная сессия
     */
    public Mono<Session> createSession(Long userId) {
        log.info("Создание новой сессии для пользователя {}", userId);
        
        Session newSession = Session.builder()
                .userId(userId)
                .name("Сессия") // Временно, будет обновлено после сохранения
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .isActive(true)
                .build();
        
        return sessionRepository.save(newSession)
        .flatMap(savedSession -> {
            // Обновляем название с реальным ID
            String sessionName = "Сессия " + savedSession.getId();
            savedSession.setName(sessionName);
            
            return sessionRepository.save(savedSession);
        })
        .doOnSuccess(session -> log.info("Сессия {} успешно создана для пользователя {}", session.getId(), userId))
        .doOnError(error -> log.error("Ошибка при создании сессии для пользователя {}", userId, error));
    }

    /**
     * Получить список сессий пользователя с пагинацией.
     * Включает превью последнего изображения и количество сообщений.
     *
     * @param userId идентификатор пользователя
     * @param page номер страницы (начиная с 0)
     * @param size размер страницы
     * @return список сессий с метаинформацией
     */
    public Mono<PageResponseDTO<SessionDTO>> getSessionsList(Long userId, int page, int size) {
        log.info("Получение списка сессий для пользователя {}, страница {}, размер {}", userId, page, size);
        
        Pageable pageable = PageRequest.of(page, size);
        
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable)
                .collectList()
                .flatMap(sessions -> {
                    if (sessions.isEmpty()) {
                        return Mono.just(PageResponseDTO.<SessionDTO>builder()
                                .content(List.of())
                                .page(page)
                                .size(size)
                                .totalElements(0L)
                                .totalPages(0)
                                .hasNext(false)
                                .hasPrevious(false)
                                .isFirst(true)
                                .isLast(true)
                                .build());
                    }
                    
                    // Получаем детали для каждой сессии
                    return Flux.fromIterable(sessions)
                            .flatMap(session -> enrichSessionWithDetails(session))
                            .collectList()
                            .flatMap(sessionDTOs -> {
                                // Подсчитываем общее количество сессий
                                return sessionRepository.countByUserId(userId)
                                        .map(totalCount -> {
                                            int totalPages = (int) Math.ceil((double) totalCount / size);
                                            
                                            return PageResponseDTO.<SessionDTO>builder()
                                                    .content(sessionDTOs)
                                                    .page(page)
                                                    .size(size)
                                                    .totalElements(totalCount)
                                                    .totalPages(totalPages)
                                                    .hasNext(page < totalPages - 1)
                                                    .hasPrevious(page > 0)
                                                    .isFirst(page == 0)
                                                    .isLast(page >= totalPages - 1)
                                                    .build();
                                        });
                            });
                })
                .doOnSuccess(result -> log.info("Получено {} сессий для пользователя {}", result.getContent().size(), userId))
                .doOnError(error -> log.error("Ошибка при получении списка сессий для пользователя {}", userId, error));
    }

    /**
     * Получить детальную информацию о сессии с историей сообщений.
     *
     * @param sessionId идентификатор сессии
     * @param userId идентификатор пользователя
     * @param page номер страницы истории (начиная с 0)
     * @param size размер страницы истории
     * @return детальная информация о сессии
     */
    public Mono<SessionDetailDTO> getSessionDetail(Long sessionId, Long userId, int page, int size) {
        log.info("Получение деталей сессии {} для пользователя {}", sessionId, userId);
        
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .switchIfEmpty(Mono.error(new RuntimeException("Сессия не найдена или не принадлежит пользователю")))
                .flatMap(session -> {
                    Pageable pageable = PageRequest.of(page, size);
                    
                    return imageHistoryRepository.findBySessionIdOrderByIterationNumberAsc(sessionId)
                            .collectList()
                            .map(histories -> {
                                List<SessionMessageDTO> messages = histories.stream()
                                        .map(this::convertToSessionMessageDTO)
                                        .collect(Collectors.toList());
                                
                                return SessionDetailDTO.builder()
                                        .id(session.getId())
                                        .name(session.getName())
                                        .createdAt(session.getCreatedAt())
                                        .updatedAt(session.getUpdatedAt())
                                        .history(messages)
                                        .totalMessages(messages.size())
                                        .hasMore(false) // TODO: Реализовать пагинацию
                                        .build();
                            });
                })
                .doOnSuccess(result -> log.info("Получены детали сессии {} с {} сообщениями", sessionId, result.getTotalMessages()))
                .doOnError(error -> log.error("Ошибка при получении деталей сессии {} для пользователя {}", sessionId, userId, error));
    }

    /**
     * Переименовать сессию.
     *
     * @param sessionId идентификатор сессии
     * @param userId идентификатор пользователя
     * @param newName новое название сессии
     * @return обновленная сессия
     */
    public Mono<Session> renameSession(Long sessionId, Long userId, String newName) {
        log.info("Переименование сессии {} в '{}' для пользователя {}", sessionId, newName, userId);
        
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .switchIfEmpty(Mono.error(new RuntimeException("Сессия не найдена или не принадлежит пользователю")))
                .flatMap(session -> {
                    session.setName(newName);
                    session.setUpdatedAt(Instant.now());
                    
                    return sessionRepository.save(session);
                })
                .doOnSuccess(session -> log.info("Сессия {} успешно переименована в '{}'", sessionId, newName))
                .doOnError(error -> log.error("Ошибка при переименовании сессии {} для пользователя {}", sessionId, userId, error));
    }

    /**
     * Удалить сессию и все связанные записи истории.
     *
     * @param sessionId идентификатор сессии
     * @param userId идентификатор пользователя
     * @return количество удаленных записей истории
     */
    public Mono<Integer> deleteSession(Long sessionId, Long userId) {
        log.info("Удаление сессии {} для пользователя {}", sessionId, userId);
        
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .switchIfEmpty(Mono.error(new RuntimeException("Сессия не найдена или не принадлежит пользователю")))
                .flatMap(session -> {
                    // Получаем количество записей истории для удаления
                    return imageHistoryRepository.findBySessionIdOrderByIterationNumberAsc(sessionId)
                            .collectList()
                            .flatMap(histories -> {
                                int historyCount = histories.size();
                                
                                // Удаляем сессию (каскадное удаление истории)
                                return sessionRepository.deleteByIdAndUserId(sessionId, userId)
                                        .map(deletedSessions -> {
                                            log.info("Сессия {} удалена вместе с {} записями истории", sessionId, historyCount);
                                            return historyCount;
                                        });
                            });
                })
                .doOnError(error -> log.error("Ошибка при удалении сессии {} для пользователя {}", sessionId, userId, error));
    }

    /**
     * Получить или создать дефолтную сессию для пользователя.
     * Если у пользователя нет активных сессий, создается новая.
     *
     * @param userId идентификатор пользователя
     * @return дефолтная сессия
     */
    public Mono<Session> getOrCreateDefaultSession(Long userId) {
        log.info("Получение или создание дефолтной сессии для пользователя {}", userId);
        
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .collectList()
                .flatMap(sessions -> {
                    if (sessions.isEmpty()) {
                        log.info("У пользователя {} нет сессий, создаем дефолтную", userId);
                        return createSession(userId);
                    } else {
                        Session defaultSession = sessions.get(0); // Самая новая сессия
                        log.info("Найдена дефолтная сессия {} для пользователя {}", defaultSession.getId(), userId);
                        return Mono.just(defaultSession);
                    }
                })
                .doOnError(error -> log.error("Ошибка при получении дефолтной сессии для пользователя {}", userId, error));
    }

    /**
     * Обновить время последнего обновления сессии.
     * Вызывается при каждой генерации изображений в сессии.
     *
     * @param sessionId идентификатор сессии
     * @return количество обновленных записей
     */
    public Mono<Integer> updateSessionTimestamp(Long sessionId) {
        return sessionRepository.updateUpdatedAt(sessionId, Instant.now())
                .doOnSuccess(count -> log.debug("Обновлено время сессии {}, обновлено записей: {}", sessionId, count))
                .doOnError(error -> log.error("Ошибка при обновлении времени сессии {}", sessionId, error));
    }

    /**
     * Обогатить сессию дополнительной информацией (последнее изображение, количество сообщений).
     */
    private Mono<SessionDTO> enrichSessionWithDetails(Session session) {
        return imageHistoryRepository.findBySessionIdOrderByIterationNumberAsc(session.getId())
                .collectList()
                .map(histories -> {
                    String lastImageUrl = null;
                    if (!histories.isEmpty()) {
                        // Берем последнее изображение (с наибольшим номером итерации)
                        lastImageUrl = histories.stream()
                                .max((h1, h2) -> Integer.compare(
                                        h1.getIterationNumber() != null ? h1.getIterationNumber() : 0,
                                        h2.getIterationNumber() != null ? h2.getIterationNumber() : 0))
                                .map(ImageGenerationHistory::getImageUrl)
                                .orElse(null);
                    }
                    
                    return SessionDTO.builder()
                            .id(session.getId())
                            .name(session.getName())
                            .createdAt(session.getCreatedAt())
                            .updatedAt(session.getUpdatedAt())
                            .lastImageUrl(lastImageUrl)
                            .messageCount(histories.size())
                            .build();
                });
    }

    /**
     * Преобразовать запись истории в DTO сообщения сессии.
     */
    private SessionMessageDTO convertToSessionMessageDTO(ImageGenerationHistory history) {
        return SessionMessageDTO.builder()
                .id(history.getId())
                .prompt(history.getPrompt())
                .imageUrls(List.of(history.getImageUrl())) // TODO: Поддержать множественные изображения
                .inputImageUrls(JsonUtils.parseJsonToList(history.getInputImageUrlsJson())) // Парсим JSON в список
                .iterationNumber(history.getIterationNumber())
                .createdAt(history.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())
                .imageCount(1) // TODO: Подсчитать реальное количество
                .outputFormat("JPEG") // TODO: Получить из истории
                .build();
    }

}
