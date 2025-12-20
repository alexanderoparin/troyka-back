package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.exception.SessionNotFoundException;
import ru.oparin.troyka.mapper.SessionMapper;
import ru.oparin.troyka.model.dto.*;
import ru.oparin.troyka.model.entity.ArtStyle;
import ru.oparin.troyka.model.entity.Session;
import ru.oparin.troyka.repository.ImageGenerationHistoryRepository;
import ru.oparin.troyka.repository.SessionRepository;

import java.time.Instant;
import java.util.List;

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
    private final SessionMapper sessionMapper;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final ArtStyleService artStyleService;

    /**
     * Создать новую сессию для пользователя.
     * Если название не указано, будет сгенерировано автоматически как "Сессия {id}".
     *
     * @param userId      идентификатор пользователя
     * @param sessionName название сессии (опционально)
     * @return созданная сессия в виде DTO
     */
    public Mono<CreateSessionResponseDTO> createSession(Long userId, String sessionName) {
        log.info("Создание новой сессии для пользователя {} с названием: {}", userId, sessionName);

        Session newSession = sessionMapper.createSessionEntity(userId, sessionName);

        return sessionRepository.save(newSession)
                .flatMap(savedSession -> {
                    // Если название не было указано, обновляем его с ID
                    if (sessionName == null || sessionName.trim().isEmpty()) {
                        String sessionNameWithId = "Сессия " + savedSession.getId();
                        savedSession.setName(sessionNameWithId);
                        return sessionRepository.save(savedSession);
                    }
                    return Mono.just(savedSession);
                })
                .map(sessionMapper::toCreateSessionResponseDTO)
                .doOnSuccess(sessionDTO -> log.info("Сессия {} успешно создана для пользователя {}", sessionDTO.getId(), userId))
                .doOnError(error -> log.error("Ошибка при создании сессии для пользователя {}", userId, error));
    }

    /**
     * Получить список сессий пользователя с пагинацией.
     * Включает превью последнего изображения и количество сообщений.
     *
     * @param userId идентификатор пользователя
     * @param page   номер страницы (начиная с 0)
     * @param size   размер страницы
     * @return список сессий с метаинформацией
     */
    public Mono<PageResponseDTO<SessionDTO>> getSessionsList(Long userId, int page, int size) {
        log.info("Получение списка сессий для пользователя {}, страница {}, размер {}", userId, page, size);

        Pageable pageable = PageRequest.of(page, size);

        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable)
                .collectList()
                .flatMap(sessions -> {
                    if (sessions.isEmpty()) {
                        return Mono.<PageResponseDTO<SessionDTO>>just(PageResponseDTO.<SessionDTO>of(List.of(), page, size, 0L));
                    }

                    // Получаем детали для каждой сессии
                    return Flux.fromIterable(sessions)
                            .flatMap(this::enrichSessionWithDetails)
                            .collectList()
                            .flatMap(sessionDTOs -> {
                                // Подсчитываем общее количество сессий
                                return sessionRepository.countByUserId(userId)
                                        .map(totalCount -> PageResponseDTO.<SessionDTO>of(sessionDTOs, page, size, totalCount));
                            });
                })
                .doOnSuccess(result -> log.info("Получено {} сессий для пользователя {}", result.getContent().size(), userId))
                .doOnError(error -> log.error("Ошибка при получении списка сессий для пользователя {}", userId, error));
    }

    /**
     * Получить детальную информацию о сессии с историей сообщений.
     *
     * @param sessionId идентификатор сессии
     * @param userId    идентификатор пользователя
     * @param page      номер страницы истории (начиная с 0)
     * @param size      размер страницы истории
     * @return детальная информация о сессии
     */
    public Mono<SessionDetailDTO> getSessionDetail(Long sessionId, Long userId, int page, int size) {
        return getSessionMonoOrThrow(sessionId, userId)
                .flatMap(session ->
                        imageHistoryRepository.findBySessionIdAndDeletedFalseOrderByCreatedAtAsc(sessionId)
                                .collectList()
                                .flatMap(histories -> {
                                    int totalCount = histories.size();
                                    boolean hasMore = (page + 1) * size < totalCount;

                                    // Загружаем все стили из кеша и обогащаем историю
                                    return artStyleService.getAllStyles()
                                            .collectMap(ArtStyle::getId)
                                            .map(stylesMap -> sessionMapper.toSessionMessageDTOList(histories, stylesMap))
                                            .map(dtos -> SessionDetailDTO.builder()
                                                    .id(session.getId())
                                                    .name(session.getName())
                                                    .createdAt(session.getCreatedAt())
                                                    .updatedAt(session.getUpdatedAt())
                                                    .history(dtos)
                                                    .totalMessages(totalCount)
                                                    .hasMore(hasMore)
                                                    .build());
                                }))
                .doOnError(error -> log.error("Ошибка при получении деталей сессии с id={} для пользователя с id={}", sessionId, userId));
    }

    /**
     * Переименовать сессию.
     *
     * @param sessionId идентификатор сессии
     * @param userId    идентификатор пользователя
     * @param newName   новое название сессии
     * @return обновленная сессия в виде DTO
     */
    public Mono<RenameSessionResponseDTO> renameSession(Long sessionId, Long userId, String newName) {
        log.info("Переименование сессии '{}' в '{}' для пользователя с id={}", sessionId, newName, userId);

        return getSessionMonoOrThrow(sessionId, userId)
                .flatMap(session -> {
                    session.setName(newName);
                    session.setUpdatedAt(Instant.now());

                    return sessionRepository.save(session);
                })
                .map(sessionMapper::toRenameSessionResponseDTO)
                .doOnSuccess(sessionDTO -> log.info("Сессия {} успешно переименована в '{}'", sessionId, newName))
                .doOnError(error -> log.error("Ошибка при переименовании сессии {} для пользователя {}", sessionId, userId));
    }

    /**
     * Удалить сессию и пометить все связанные записи истории как удаленные (soft delete).
     *
     * @param sessionId идентификатор сессии
     * @param userId    идентификатор пользователя
     * @return DTO ответа при удалении сессии
     */
    public Mono<DeleteSessionResponseDTO> deleteSession(Long sessionId, Long userId) {
        log.info("Удаление сессии {} для пользователя {}", sessionId, userId);

        return getSessionMonoOrThrow(sessionId, userId)
                .flatMap(session -> {
                    // Получаем количество записей истории для пометки как удаленных
                    return imageHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                            .collectList()
                            .flatMap(histories -> {
                                int historyCount = histories.size();

                                // Помечаем все записи истории как удаленные (soft delete)
                                return imageHistoryRepository.markAsDeletedBySessionId(sessionId)
                                        .flatMap(markedCount -> {
                                            // Удаляем сессию
                                            return sessionRepository.deleteByIdAndUserId(sessionId, userId)
                                                    .map(deletedSessions -> {
                                                        log.info("Сессия {} удалена, {} записей истории помечено как удаленные", sessionId, markedCount);
                                                        return sessionMapper.toDeleteSessionResponseDTO(sessionId, markedCount);
                                                    });
                                        });
                            });
                })
                .doOnError(error -> log.error("Ошибка при удалении сессии {} для пользователя {}", sessionId, userId));
    }

    /**
     * Получаем сессию или выбрасываем исключение
     */
    private Mono<Session> getSessionMonoOrThrow(Long sessionId, Long userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .switchIfEmpty(Mono.error(new SessionNotFoundException("Сессия не найдена или не принадлежит пользователю")));
    }

    /**
     * Получить или создать дефолтную сессию для пользователя.
     * Если у пользователя нет активных сессий, создается новая.
     *
     * @param userId идентификатор пользователя
     * @return дефолтная сессия в виде DTO
     */
    public Mono<SessionDTO> getOrCreateDefaultSession(Long userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .collectList()
                .flatMap(sessions -> {
                    if (sessions.isEmpty()) {
                        log.info("У пользователя {} нет сессий, создаем дефолтную", userId);
                        return createSession(userId, null)
                                .map(sessionMapper::createResponseToSessionDTO);
                    } else {
                        Session defaultSession = sessions.get(0); // Самая новая сессия
                        return Mono.just(sessionMapper.toSessionDTO(defaultSession));
                    }
                })
                .doOnError(error -> log.error("Ошибка при получении дефолтной сессии для пользователя {}", userId, error));
    }

    /**
     * Создать новую дефолтную сессию с автоматическим названием.
     * Название будет установлено как "Сессия {id}" после сохранения.
     *
     * @param userId идентификатор пользователя
     * @return созданная сессия
     */
    private Mono<Session> createDefaultSession(Long userId) {
        Session newSession = sessionMapper.createSessionEntity(userId, null);
        return sessionRepository.save(newSession)
                .flatMap(savedSession -> {
                    String sessionNameWithId = "Сессия " + savedSession.getId();
                    savedSession.setName(sessionNameWithId);
                    return sessionRepository.save(savedSession);
                });
    }

    /**
     * Получить сессию по ID или создать/получить дефолтную сессию.
     * Если sessionId указан, возвращает существующую сессию.
     * Если сессия не найдена, создает новую дефолтную сессию.
     * Если sessionId null, возвращает или создает дефолтную сессию пользователя.
     *
     * @param sessionId идентификатор сессии (может быть null)
     * @param userId    идентификатор пользователя
     * @return сессия для генерации
     */
    public Mono<Session> getOrCreateSession(Long sessionId, Long userId) {
        if (sessionId != null) {
            log.info("Получение сессии {} для пользователя {}", sessionId, userId);
            return sessionRepository.findByIdAndUserId(sessionId, userId)
                    .switchIfEmpty(Mono.defer(() -> {
                        log.warn("Сессия {} не найдена для пользователя {}, создаем новую дефолтную сессию", sessionId, userId);
                        return createDefaultSession(userId);
                    }));
        } else {
            log.info("Получение дефолтной сессии для пользователя {}", userId);
            return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                    .next()
                    .switchIfEmpty(Mono.defer(() -> {
                        log.info("У пользователя {} нет сессий, создаем дефолтную", userId);
                        return createDefaultSession(userId);
                    }));
        }
    }

    /**
     * Обновить время последнего обновления сессии.
     * Вызывается при каждой генерации изображений в сессии.
     *
     * @param sessionId идентификатор сессии
     * @return Mono<Void> - операция завершена
     */
    public Mono<Void> updateSessionTimestamp(Long sessionId) {
        return r2dbcEntityTemplate.update(Session.class)
                .matching(Query.query(Criteria.where("id").is(sessionId)))
                .apply(Update.update("updatedAt", Instant.now()))
                .doOnSuccess(count -> log.debug("Обновлено время сессии {}", sessionId))
                .doOnError(error -> log.error("Ошибка при обновлении времени сессии {}", sessionId, error))
                .then();
    }

    /**
     * Обогатить сессию дополнительной информацией (последнее изображение, количество сообщений).
     */
    private Mono<SessionDTO> enrichSessionWithDetails(Session session) {
        return Mono.zip(
                imageHistoryRepository.findFirstBySessionIdAndDeletedFalseOrderByCreatedAtDesc(session.getId())
                        .map(history -> history.getImageUrls().isEmpty() ? "" : history.getImageUrls().get(0))
                        .switchIfEmpty(Mono.just("")),
                imageHistoryRepository.countBySessionIdAndDeletedFalse(session.getId())
                        .map(Long::intValue)
        ).map(tuple -> sessionMapper.toSessionDTOWithDetails(session, tuple.getT1(), tuple.getT2()));
    }

}
