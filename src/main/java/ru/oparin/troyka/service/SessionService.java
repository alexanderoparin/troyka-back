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

    private static final String DEFAULT_SESSION_NAME_PREFIX = "Сессия ";
    private static final int FIRST_ELEMENT_INDEX = 0;
    private static final String SESSION_NOT_FOUND_MESSAGE = "Сессия не найдена или не принадлежит пользователю";
    private static final String EMPTY_IMAGE_URL = "";

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
                .flatMap(savedSession -> updateSessionNameIfNeeded(savedSession, sessionName))
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
        Pageable pageable = PageRequest.of(page, size);

        return sessionRepository.findByUserIdAndDeletedFalseOrderByUpdatedAtDesc(userId, pageable)
                .collectList()
                .flatMap(sessions -> buildSessionsPageResponse(sessions, userId, page, size))
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
                .flatMap(session -> buildSessionDetailDTO(session, sessionId, page, size))
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
                .flatMap(session -> updateSessionName(session, newName))
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
                .flatMap(session -> markSessionAsDeleted(sessionId, userId))
                .doOnError(error -> log.error("Ошибка при удалении сессии {} для пользователя {}", sessionId, userId, error));
    }

    /**
     * Получить или создать дефолтную сессию для пользователя.
     * Если у пользователя нет активных сессий, создается новая.
     *
     * @param userId идентификатор пользователя
     * @return дефолтная сессия в виде DTO
     */
    public Mono<SessionDTO> getOrCreateDefaultSession(Long userId) {
        return sessionRepository.findByUserIdAndDeletedFalseOrderByUpdatedAtDesc(userId)
                .collectList()
                .flatMap(sessions -> getOrCreateDefaultSessionFromList(sessions, userId))
                .doOnError(error -> log.error("Ошибка при получении дефолтной сессии для пользователя {}", userId, error));
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
            return getSessionByIdOrCreateDefault(sessionId, userId);
        } else {
            return getDefaultSessionOrCreate(userId);
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

    // ==================== Приватные вспомогательные методы ====================

    /**
     * Получить сессию или выбросить исключение.
     * Проверяет только не удаленные сессии.
     *
     * @param sessionId идентификатор сессии
     * @param userId    идентификатор пользователя
     * @return сессия или исключение, если не найдена
     */
    private Mono<Session> getSessionMonoOrThrow(Long sessionId, Long userId) {
        return sessionRepository.findByIdAndUserIdAndDeletedFalse(sessionId, userId)
                .switchIfEmpty(Mono.error(new SessionNotFoundException(SESSION_NOT_FOUND_MESSAGE)));
    }

    /**
     * Обогатить сессию дополнительной информацией (последнее изображение, количество сообщений).
     *
     * @param session сессия для обогащения
     * @return обогащенная сессия в виде DTO
     */
    private Mono<SessionDTO> enrichSessionWithDetails(Session session) {
        return Mono.zip(
                getLastImageUrlForSession(session.getId()),
                getMessageCountForSession(session.getId())
        ).map(tuple -> sessionMapper.toSessionDTOWithDetails(session, tuple.getT1(), tuple.getT2()));
    }

    /**
     * Обновить название сессии, если оно не было указано при создании.
     *
     * @param savedSession сохраненная сессия
     * @param sessionName   исходное название сессии
     * @return обновленная сессия
     */
    private Mono<Session> updateSessionNameIfNeeded(Session savedSession, String sessionName) {
        if (isSessionNameEmpty(sessionName)) {
            String sessionNameWithId = buildDefaultSessionName(savedSession.getId());
            savedSession.setName(sessionNameWithId);
            return sessionRepository.save(savedSession);
        }
        return Mono.just(savedSession);
    }

    /**
     * Обновить название сессии.
     *
     * @param session сессия для обновления
     * @param newName новое название
     * @return обновленная сессия
     */
    private Mono<Session> updateSessionName(Session session, String newName) {
        session.setName(newName);
        session.setUpdatedAt(Instant.now());
        return sessionRepository.save(session);
    }

    /**
     * Построить ответ со списком сессий с пагинацией.
     *
     * @param sessions список сессий
     * @param userId   идентификатор пользователя
     * @param page     номер страницы
     * @param size     размер страницы
     * @return ответ со списком сессий
     */
    private Mono<PageResponseDTO<SessionDTO>> buildSessionsPageResponse(List<Session> sessions, Long userId, int page, int size) {
        if (sessions.isEmpty()) {
            return Mono.just(PageResponseDTO.of(List.of(), page, size, 0L));
        }

        return Flux.fromIterable(sessions)
                .flatMap(this::enrichSessionWithDetails)
                .collectList()
                .flatMap(sessionDTOs -> sessionRepository.countByUserIdAndDeletedFalse(userId)
                        .map(totalCount -> PageResponseDTO.of(sessionDTOs, page, size, totalCount)));
    }

    /**
     * Построить DTO детальной информации о сессии.
     *
     * @param session   сессия
     * @param sessionId идентификатор сессии
     * @param page      номер страницы истории
     * @param size      размер страницы истории
     * @return DTO детальной информации о сессии
     */
    private Mono<SessionDetailDTO> buildSessionDetailDTO(Session session, Long sessionId, int page, int size) {
        return imageHistoryRepository.findBySessionIdAndDeletedFalseOrderByCreatedAtAsc(sessionId)
                .collectList()
                .flatMap(histories -> {
                    int totalCount = histories.size();
                    boolean hasMore = calculateHasMore(page, size, totalCount);

                    return artStyleService.getAllStyles()
                            .collectMap(ArtStyle::getId)
                            .map(stylesMap -> sessionMapper.toSessionMessageDTOList(histories, stylesMap))
                            .map(dtos -> buildSessionDetailDTO(session, dtos, totalCount, hasMore));
                });
    }

    /**
     * Пометить сессию и связанные истории как удаленные.
     *
     * @param sessionId идентификатор сессии
     * @param userId    идентификатор пользователя
     * @return DTO ответа при удалении
     */
    private Mono<DeleteSessionResponseDTO> markSessionAsDeleted(Long sessionId, Long userId) {
        return imageHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .collectList()
                .flatMap(histories -> {
                    int historyCount = histories.size();
                    log.debug("Найдено {} записей истории для сессии {}", historyCount, sessionId);

                    return imageHistoryRepository.markAsDeletedBySessionId(sessionId)
                            .doOnNext(markedCount -> log.info("Помечено {} записей истории как удаленные для сессии {}", markedCount, sessionId))
                            .then(sessionRepository.markAsDeletedByIdAndUserId(sessionId, userId))
                            .doOnNext(updatedCount -> logDeletionResult(sessionId, updatedCount))
                            .map(updatedCount -> sessionMapper.toDeleteSessionResponseDTO(sessionId, historyCount));
                });
    }

    /**
     * Получить или создать дефолтную сессию из списка существующих.
     *
     * @param sessions список сессий пользователя
     * @param userId   идентификатор пользователя
     * @return дефолтная сессия в виде DTO
     */
    private Mono<SessionDTO> getOrCreateDefaultSessionFromList(List<Session> sessions, Long userId) {
        if (sessions.isEmpty()) {
            return createSession(userId, null)
                    .map(sessionMapper::createResponseToSessionDTO);
        } else {
            Session defaultSession = sessions.get(FIRST_ELEMENT_INDEX);
            return Mono.just(sessionMapper.toSessionDTO(defaultSession));
        }
    }

    /**
     * Получить сессию по ID или создать дефолтную, если не найдена.
     *
     * @param sessionId идентификатор сессии
     * @param userId    идентификатор пользователя
     * @return сессия
     */
    private Mono<Session> getSessionByIdOrCreateDefault(Long sessionId, Long userId) {
        log.info("Получение сессии {} для пользователя {}", sessionId, userId);
        return sessionRepository.findByIdAndUserIdAndDeletedFalse(sessionId, userId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Сессия {} не найдена для пользователя {}, создаем новую дефолтную сессию", sessionId, userId);
                    return createDefaultSession(userId);
                }));
    }

    /**
     * Получить дефолтную сессию или создать новую, если нет сессий.
     *
     * @param userId идентификатор пользователя
     * @return сессия
     */
    private Mono<Session> getDefaultSessionOrCreate(Long userId) {
        log.info("Получение дефолтной сессии для пользователя {}", userId);
        return sessionRepository.findByUserIdAndDeletedFalseOrderByUpdatedAtDesc(userId)
                .next()
                .switchIfEmpty(Mono.defer(() -> {

                    return createDefaultSession(userId);
                }));
    }

    /**
     * Создать новую дефолтную сессию с автоматическим названием.
     * Название будет установлено как "Сессия {id}" после сохранения.
     *
     * @param userId идентификатор пользователя
     * @return созданная сессия
     */
    private Mono<Session> createDefaultSession(Long userId) {
        log.info("У пользователя {} нет сессий, создаем дефолтную", userId);
        Session newSession = sessionMapper.createSessionEntity(userId, null);
        return sessionRepository.save(newSession)
                .flatMap(savedSession -> {
                    String sessionNameWithId = buildDefaultSessionName(savedSession.getId());
                    savedSession.setName(sessionNameWithId);
                    return sessionRepository.save(savedSession);
                });
    }

    /**
     * Получить URL последнего изображения для сессии.
     *
     * @param sessionId идентификатор сессии
     * @return URL последнего изображения или пустая строка
     */
    private Mono<String> getLastImageUrlForSession(Long sessionId) {
        return imageHistoryRepository.findFirstBySessionIdAndDeletedFalseOrderByCreatedAtDesc(sessionId)
                .map(history -> extractFirstImageUrl(history.getImageUrls()))
                .switchIfEmpty(Mono.just(EMPTY_IMAGE_URL));
    }

    /**
     * Получить количество сообщений для сессии.
     *
     * @param sessionId идентификатор сессии
     * @return количество сообщений
     */
    private Mono<Integer> getMessageCountForSession(Long sessionId) {
        return imageHistoryRepository.countBySessionIdAndDeletedFalse(sessionId)
                .map(Long::intValue);
    }

    /**
     * Построить DTO детальной информации о сессии.
     *
     * @param session     сессия
     * @param historyDTOs список DTO истории сообщений
     * @param totalCount  общее количество сообщений
     * @param hasMore     есть ли еще сообщения
     * @return DTO детальной информации о сессии
     */
    private SessionDetailDTO buildSessionDetailDTO(Session session, List<SessionMessageDTO> historyDTOs, int totalCount, boolean hasMore) {
        return SessionDetailDTO.builder()
                .id(session.getId())
                .name(session.getName())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .history(historyDTOs)
                .totalMessages(totalCount)
                .hasMore(hasMore)
                .build();
    }

    /**
     * Вычислить, есть ли еще сообщения для загрузки.
     *
     * @param page      номер страницы
     * @param size      размер страницы
     * @param totalCount общее количество сообщений
     * @return true, если есть еще сообщения
     */
    private boolean calculateHasMore(int page, int size, int totalCount) {
        return (page + 1) * size < totalCount;
    }

    /**
     * Проверить, пустое ли название сессии.
     *
     * @param sessionName название сессии
     * @return true, если название пустое или null
     */
    private boolean isSessionNameEmpty(String sessionName) {
        return sessionName == null || sessionName.trim().isEmpty();
    }

    /**
     * Построить название сессии по умолчанию.
     *
     * @param sessionId идентификатор сессии
     * @return название сессии по умолчанию
     */
    private String buildDefaultSessionName(Long sessionId) {
        return DEFAULT_SESSION_NAME_PREFIX + sessionId;
    }

    /**
     * Извлечь первый URL изображения из списка.
     *
     * @param imageUrls список URL изображений
     * @return первый URL или пустая строка
     */
    private String extractFirstImageUrl(List<String> imageUrls) {
        return imageUrls.isEmpty() ? EMPTY_IMAGE_URL : imageUrls.get(FIRST_ELEMENT_INDEX);
    }

    /**
     * Залогировать результат удаления сессии.
     *
     * @param sessionId   идентификатор сессии
     * @param updatedCount количество обновленных записей
     */
    private void logDeletionResult(Long sessionId, Integer updatedCount) {
        if (updatedCount == 0) {
            log.warn("Сессия {} не была помечена как удаленная (updatedCount=0)", sessionId);
        } else {
            log.info("Сессия {} помечена как удаленная", sessionId);
        }
    }

}
