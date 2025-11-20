package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.exception.AuthException;
import ru.oparin.troyka.model.dto.*;
import ru.oparin.troyka.service.SessionService;
import ru.oparin.troyka.service.UserService;
import ru.oparin.troyka.util.SecurityUtil;

/**
 * REST контроллер для работы с сессиями генерации изображений.
 * Предоставляет API для создания, получения, обновления и удаления сессий.
 * 
 * <p>Все методы автоматически определяют текущего пользователя через SecurityUtil
 * и работают только с его сессиями, обеспечивая безопасность данных.</p>
 */
@Slf4j
@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
@Tag(name = "Sessions", description = "API для работы с сессиями генерации изображений")
public class SessionController {

    private final SessionService sessionService;
    private final UserService userService;

    /**
     * Получить или создать дефолтную сессию пользователя.
     * Если у пользователя нет активных сессий, создается новая.
     *
     * @return дефолтная сессия пользователя
     */
    @GetMapping("/default")
    @Operation(summary = "Получить дефолтную сессию", 
               description = "Возвращает или создает дефолтную сессию пользователя")
    public Mono<ResponseEntity<SessionDTO>> getDefaultSession() {
        return SecurityUtil.checkStudioAccess(userService)
                .flatMap(sessionService::getOrCreateDefaultSession)
                .map(ResponseEntity::ok)
                .doOnError(error -> {
                    if (error instanceof AuthException) {
                        log.error("Ошибка при получении дефолтной сессии: {}", error.getMessage());
                    } else {
                        log.error("Ошибка при получении дефолтной сессии", error);
                    }
                });
    }

    /**
     * Получить список сессий пользователя с пагинацией.
     * Сессии возвращаются отсортированными по дате обновления (новые первые).
     *
     * @param page номер страницы (начиная с 0)
     * @param size размер страницы
     * @return список сессий с метаинформацией о пагинации
     */
    @GetMapping
    @Operation(summary = "Получить список сессий", 
               description = "Возвращает список сессий пользователя с пагинацией")
    public Mono<ResponseEntity<PageResponseDTO<SessionDTO>>> getSessionsList(
            @Parameter(description = "Номер страницы (начиная с 0)") 
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы") 
            @RequestParam(defaultValue = "10") int size) {
        
        return SecurityUtil.checkStudioAccess(userService)
                .flatMap(userId -> sessionService.getSessionsList(userId, page, size))
                .map(ResponseEntity::ok)
                .doOnError(error -> {
                    if (error instanceof AuthException) {
                        log.error("Ошибка при получении списка сессий: {}", error.getMessage());
                    } else {
                        log.error("Ошибка при получении списка сессий", error);
                    }
                });
    }

    /**
     * Создать новую сессию для пользователя.
     * Если название не указано в запросе, генерируется автоматически как "Сессия {id}".
     *
     * @param request данные для создания сессии (опциональное название)
     * @return созданная сессия с информацией о результате
     */
    @PostMapping
    @Operation(summary = "Создать новую сессию", 
               description = "Создает новую сессию для пользователя")
    public Mono<ResponseEntity<CreateSessionResponseDTO>> createSession(
            @Valid @RequestBody CreateSessionRequestDTO request) {
        
        return SecurityUtil.checkStudioAccess(userService)
                .flatMap(userId -> sessionService.createSession(userId, request.getName()))
                .map(sessionDTO -> ResponseEntity.status(HttpStatus.CREATED).body(sessionDTO))
                .doOnError(error -> {
                    if (error instanceof AuthException) {
                        log.error("Ошибка при создании сессии: {}", error.getMessage());
                    } else {
                        log.error("Ошибка при создании сессии", error);
                    }
                });
    }

    /**
     * Получить детальную информацию о сессии с историей сообщений.
     * История возвращается с пагинацией для оптимизации производительности.
     *
     * @param sessionId идентификатор сессии
     * @param page номер страницы истории (начиная с 0)
     * @param size размер страницы истории
     * @return детальная информация о сессии с историей сообщений
     */
    @GetMapping("/{sessionId}")
    @Operation(summary = "Получить детали сессии", 
               description = "Возвращает детальную информацию о сессии с историей сообщений")
    public Mono<ResponseEntity<SessionDetailDTO>> getSessionDetail(
            @Parameter(description = "Идентификатор сессии") 
            @PathVariable Long sessionId,
            @Parameter(description = "Номер страницы истории (начиная с 0)") 
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы истории") 
            @RequestParam(defaultValue = "20") int size) {
        
        return SecurityUtil.checkStudioAccess(userService)
                .flatMap(userId -> sessionService.getSessionDetail(sessionId, userId, page, size))
                .map(ResponseEntity::ok)
                .doOnError(error -> {
                    if (error instanceof AuthException) {
                        log.error("Ошибка при получении деталей сессии {}: {}", sessionId, error.getMessage());
                    } else {
                        log.error("Ошибка при получении деталей сессии {}", sessionId, error);
                    }
                });
    }

    /**
     * Переименовать сессию.
     * Пользователь может изменить название сессии на любое удобное ему.
     *
     * @param sessionId идентификатор сессии
     * @param request данные для переименования (новое название)
     * @return обновленная сессия с новым названием
     */
    @PutMapping("/{sessionId}/rename")
    @Operation(summary = "Переименовать сессию", 
               description = "Изменяет название сессии")
    public Mono<ResponseEntity<RenameSessionResponseDTO>> renameSession(
            @Parameter(description = "Идентификатор сессии") 
            @PathVariable Long sessionId,
            @Valid @RequestBody RenameSessionRequestDTO request) {
        
        return SecurityUtil.checkStudioAccess(userService)
                .flatMap(userId -> sessionService.renameSession(sessionId, userId, request.getName()))
                .map(ResponseEntity::ok)
                .doOnError(error -> {
                    if (error instanceof AuthException) {
                        log.error("Ошибка при переименовании сессии {}: {}", sessionId, error.getMessage());
                    } else {
                        log.error("Ошибка при переименовании сессии {}", sessionId, error);
                    }
                });
    }

    /**
     * Удалить сессию и все связанные записи истории.
     * Это действие необратимо - все изображения и история диалога будут удалены.
     *
     * @param sessionId идентификатор сессии
     * @return результат удаления с количеством удаленных записей истории
     */
    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Удалить сессию", 
               description = "Удаляет сессию и все связанные записи истории")
    public Mono<ResponseEntity<DeleteSessionResponseDTO>> deleteSession(
            @Parameter(description = "Идентификатор сессии") 
            @PathVariable Long sessionId) {
        
        return SecurityUtil.checkStudioAccess(userService)
                .flatMap(userId -> sessionService.deleteSession(sessionId, userId))
                .map(ResponseEntity::ok)
                .doOnError(error -> {
                    if (error instanceof AuthException) {
                        log.error("Ошибка при удалении сессии {}: {}", sessionId, error.getMessage());
                    } else {
                        log.error("Ошибка при удалении сессии {}", sessionId, error);
                    }
                });
    }
}