package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.*;
import ru.oparin.troyka.service.SessionService;

/**
 * REST контроллер для работы с сессиями генерации изображений.
 * Предоставляет API для создания, получения, обновления и удаления сессий.
 */
@Slf4j
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@Tag(name = "Sessions", description = "API для работы с сессиями генерации изображений")
public class SessionController {

    private final SessionService sessionService;

    /**
     * Получить список сессий пользователя с пагинацией.
     *
     * @param page номер страницы (начиная с 0)
     * @param size размер страницы
     * @param authentication данные аутентификации пользователя
     * @return список сессий с метаинформацией
     */
    @GetMapping
    @Operation(summary = "Получить список сессий", description = "Возвращает список сессий пользователя с пагинацией")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список сессий успешно получен"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    public Mono<ResponseEntity<PageResponseDTO<SessionDTO>>> getSessionsList(
            @Parameter(description = "Номер страницы (начиная с 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы") @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        
        Long userId = getUserIdFromAuthentication(authentication);
        log.info("Запрос списка сессий для пользователя {}, страница {}, размер {}", userId, page, size);
        
        return sessionService.getSessionsList(userId, page, size)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.info("Список сессий успешно возвращен для пользователя {}", userId))
                .doOnError(error -> log.error("Ошибка при получении списка сессий для пользователя {}", userId, error));
    }

    /**
     * Создать новую сессию.
     *
     * @param request данные для создания сессии
     * @param authentication данные аутентификации пользователя
     * @return созданная сессия
     */
    @PostMapping
    @Operation(summary = "Создать новую сессию", description = "Создает новую сессию для пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Сессия успешно создана"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные запроса"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    public Mono<ResponseEntity<CreateSessionResponseDTO>> createSession(
            @Valid @RequestBody CreateSessionRequestDTO request,
            Authentication authentication) {
        
        Long userId = getUserIdFromAuthentication(authentication);
        log.info("Создание новой сессии для пользователя {}", userId);
        
        return sessionService.createSession(userId)
                .map(session -> {
                    CreateSessionResponseDTO response = CreateSessionResponseDTO.builder()
                            .id(session.getId())
                            .name(session.getName())
                            .createdAt(session.getCreatedAt())
                            .message("Сессия успешно создана")
                            .build();
                    
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                .doOnSuccess(response -> log.info("Сессия {} успешно создана для пользователя {}", response.getBody().getId(), userId))
                .doOnError(error -> log.error("Ошибка при создании сессии для пользователя {}", userId, error));
    }

    /**
     * Получить детальную информацию о сессии.
     *
     * @param sessionId идентификатор сессии
     * @param page номер страницы истории (начиная с 0)
     * @param size размер страницы истории
     * @param authentication данные аутентификации пользователя
     * @return детальная информация о сессии
     */
    @GetMapping("/{sessionId}")
    @Operation(summary = "Получить детали сессии", description = "Возвращает детальную информацию о сессии с историей сообщений")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Детали сессии успешно получены"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    public Mono<ResponseEntity<SessionDetailDTO>> getSessionDetail(
            @Parameter(description = "Идентификатор сессии") @PathVariable Long sessionId,
            @Parameter(description = "Номер страницы истории (начиная с 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы истории") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        
        Long userId = getUserIdFromAuthentication(authentication);
        log.info("Запрос деталей сессии {} для пользователя {}", sessionId, userId);
        
        return sessionService.getSessionDetail(sessionId, userId, page, size)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.info("Детали сессии {} успешно возвращены для пользователя {}", sessionId, userId))
                .doOnError(error -> log.error("Ошибка при получении деталей сессии {} для пользователя {}", sessionId, userId, error));
    }

    /**
     * Переименовать сессию.
     *
     * @param sessionId идентификатор сессии
     * @param request данные для переименования
     * @param authentication данные аутентификации пользователя
     * @return обновленная сессия
     */
    @PutMapping("/{sessionId}/rename")
    @Operation(summary = "Переименовать сессию", description = "Изменяет название сессии")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Сессия успешно переименована"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные запроса"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    public Mono<ResponseEntity<RenameSessionResponseDTO>> renameSession(
            @Parameter(description = "Идентификатор сессии") @PathVariable Long sessionId,
            @Valid @RequestBody RenameSessionRequestDTO request,
            Authentication authentication) {
        
        Long userId = getUserIdFromAuthentication(authentication);
        log.info("Переименование сессии {} в '{}' для пользователя {}", sessionId, request.getName(), userId);
        
        return sessionService.renameSession(sessionId, userId, request.getName())
                .map(session -> {
                    RenameSessionResponseDTO response = RenameSessionResponseDTO.builder()
                            .id(session.getId())
                            .name(session.getName())
                            .message("Сессия успешно переименована")
                            .build();
                    
                    return ResponseEntity.ok(response);
                })
                .doOnSuccess(response -> log.info("Сессия {} успешно переименована в '{}' для пользователя {}", sessionId, request.getName(), userId))
                .doOnError(error -> log.error("Ошибка при переименовании сессии {} для пользователя {}", sessionId, userId, error));
    }

    /**
     * Удалить сессию.
     *
     * @param sessionId идентификатор сессии
     * @param authentication данные аутентификации пользователя
     * @return результат удаления
     */
    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Удалить сессию", description = "Удаляет сессию и все связанные записи истории")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Сессия успешно удалена"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    public Mono<ResponseEntity<DeleteSessionResponseDTO>> deleteSession(
            @Parameter(description = "Идентификатор сессии") @PathVariable Long sessionId,
            Authentication authentication) {
        
        Long userId = getUserIdFromAuthentication(authentication);
        log.info("Удаление сессии {} для пользователя {}", sessionId, userId);
        
        return sessionService.deleteSession(sessionId, userId)
                .map(deletedHistoryCount -> {
                    DeleteSessionResponseDTO response = DeleteSessionResponseDTO.builder()
                            .id(sessionId)
                            .message("Сессия успешно удалена")
                            .deletedHistoryCount(deletedHistoryCount)
                            .build();
                    
                    return ResponseEntity.ok(response);
                })
                .doOnSuccess(response -> log.info("Сессия {} успешно удалена вместе с {} записями истории для пользователя {}", sessionId, response.getBody().getDeletedHistoryCount(), userId))
                .doOnError(error -> log.error("Ошибка при удалении сессии {} для пользователя {}", sessionId, userId, error));
    }

    /**
     * Получить или создать дефолтную сессию пользователя.
     *
     * @param authentication данные аутентификации пользователя
     * @return дефолтная сессия
     */
    @GetMapping("/default")
    @Operation(summary = "Получить дефолтную сессию", description = "Возвращает или создает дефолтную сессию пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Дефолтная сессия получена"),
            @ApiResponse(responseCode = "201", description = "Дефолтная сессия создана"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    public Mono<ResponseEntity<SessionDTO>> getDefaultSession(Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);
        log.info("Запрос дефолтной сессии для пользователя {}", userId);
        
        return sessionService.getOrCreateDefaultSession(userId)
                .flatMap(session -> {
                    // Обогащаем сессию дополнительной информацией
                    return sessionService.getSessionsList(userId, 0, 1)
                            .map(pageResponse -> {
                                SessionDTO sessionDTO = pageResponse.getContent().isEmpty() 
                                        ? SessionDTO.builder()
                                                .id(session.getId())
                                                .name(session.getName())
                                                .createdAt(session.getCreatedAt())
                                                .updatedAt(session.getUpdatedAt())
                                                .lastImageUrl(null)
                                                .messageCount(0)
                                                .build()
                                        : pageResponse.getContent().get(0);
                                
                                HttpStatus status = pageResponse.getContent().isEmpty() 
                                        ? HttpStatus.CREATED 
                                        : HttpStatus.OK;
                                
                                return ResponseEntity.status(status).body(sessionDTO);
                            });
                })
                .doOnSuccess(response -> log.info("Дефолтная сессия {} успешно возвращена для пользователя {}", response.getBody().getId(), userId))
                .doOnError(error -> log.error("Ошибка при получении дефолтной сессии для пользователя {}", userId, error));
    }

    /**
     * Извлечь ID пользователя из данных аутентификации.
     *
     * @param authentication данные аутентификации
     * @return ID пользователя
     */
    private Long getUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("Пользователь не авторизован");
        }
        
        try {
            // Предполагаем, что principal содержит ID пользователя
            return Long.parseLong(authentication.getPrincipal().toString());
        } catch (NumberFormatException e) {
            log.error("Не удалось извлечь ID пользователя из аутентификации: {}", authentication.getPrincipal());
            throw new RuntimeException("Некорректные данные аутентификации");
        }
    }
}
