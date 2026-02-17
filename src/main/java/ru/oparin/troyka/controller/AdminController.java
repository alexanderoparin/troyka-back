package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.admin.*;
import ru.oparin.troyka.model.dto.auth.MessageResponse;
import ru.oparin.troyka.model.dto.system.SystemStatusHistoryDTO;
import ru.oparin.troyka.model.dto.system.SystemStatusRequest;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.service.*;
import ru.oparin.troyka.service.provider.GenerationProviderRouter;
import ru.oparin.troyka.util.SecurityUtil;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Контроллер для админ-панели.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Админ-панель", description = "API для администраторов системы")
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;
    private final SystemStatusService systemStatusService;
    private final GenerationProviderSettingsService providerSettingsService;
    private final GenerationProviderRouter providerRouter;
    private final ProviderFallbackMetricsService fallbackMetricsService;
    private final BlockedRegistrationMetricsService blockedRegistrationMetricsService;

    @Operation(summary = "Получить все оплаченные платежи",
            description = "Возвращает список всех успешно оплаченных платежей в системе. Требуется роль ADMIN.")
    @GetMapping("/payments")
    public Mono<ResponseEntity<List<AdminPaymentDTO>>> getAllPayments() {
        return SecurityUtil.getCurrentAdmin(userService)
                .flatMapMany(admin -> adminService.getAllPayments())
                .collectList()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Ошибка получения платежей: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(403).build());
                });
    }

    @Operation(summary = "Получить всех пользователей",
            description = "Возвращает список всех пользователей системы. Требуется роль ADMIN.")
    @GetMapping("/users")
    public Mono<ResponseEntity<List<AdminUserDTO>>> getAllUsers() {
        return SecurityUtil.getCurrentAdmin(userService)
                .flatMapMany(admin -> adminService.getAllUsers())
                .collectList()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Ошибка получения пользователей: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(403).build());
                });
    }

    @Operation(summary = "Поиск пользователей",
            description = "Ищет пользователей по фильтру (ID, username, email, telegram). " +
                    "Возвращает список пользователей, соответствующих критериям поиска. Требуется роль ADMIN.")
    @GetMapping("/users/search")
    public Mono<ResponseEntity<List<AdminUserDTO>>> searchUsers(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "20") int limit) {
        return SecurityUtil.getCurrentAdmin(userService)
                .flatMapMany(admin -> adminService.searchUsers(query, limit))
                .collectList()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Ошибка поиска пользователей: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(403).build());
                });
    }

    @Operation(summary = "Получить статистику",
            description = "Возвращает статистику системы. Требуется роль ADMIN.")
    @GetMapping("/stats")
    public Mono<ResponseEntity<AdminStatsDTO>> getStats() {
        return SecurityUtil.getCurrentAdmin(userService)
                .flatMap(admin -> adminService.getStats())
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Ошибка получения статистики: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(403).build());
                });
    }

    @Operation(summary = "Получить текущий статус системы",
            description = "Возвращает текущий статус системы с метаданными. Требуется роль ADMIN.")
    @GetMapping("/system/status")
    public Mono<ResponseEntity<SystemStatusService.CurrentStatusWithMetadata>> getSystemStatus() {
        return SecurityUtil.getCurrentAdmin(userService)
                .flatMap(admin -> systemStatusService.getCurrentStatusWithMetadata())
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Ошибка получения статуса системы: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(403).build());
                });
    }


    @Operation(summary = "Обновить статус системы",
            description = "Обновляет статус системы и создает запись в истории. Требуется роль ADMIN.")
    @PutMapping("/system/status")
    public Mono<ResponseEntity<MessageResponse>> updateSystemStatus(@Valid @RequestBody SystemStatusRequest request) {
        return SecurityUtil.getCurrentAdmin(userService)
                .flatMap(admin -> systemStatusService.updateStatusManually(
                        request.getStatus(),
                        request.getMessage()
                ))
                .map(history -> ResponseEntity.ok(new MessageResponse("Статус системы успешно обновлен")))
                .onErrorResume(e -> {
                    log.error("Ошибка обновления статуса системы: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(403).body(
                            new MessageResponse("Ошибка обновления статуса: " + e.getMessage())
                    ));
                });
    }

    @Operation(summary = "Получить историю изменений статуса системы",
            description = "Возвращает историю изменений статуса системы. Требуется роль ADMIN.")
    @GetMapping("/system/history")
    public Mono<ResponseEntity<List<SystemStatusHistoryDTO>>> getSystemStatusHistory(
            @RequestParam(defaultValue = "50") int limit) {
        return SecurityUtil.getCurrentAdmin(userService)
                .flatMapMany(admin -> systemStatusService.getHistory(limit))
                .collectList()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Ошибка получения истории статуса системы: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(403).build());
                });
    }

    @Operation(summary = "Получить статистику генераций пользователя(ей)",
            description = "Возвращает статистику генераций пользователя(ей) за указанный период. " +
                    "Показывает количество генераций по обычной модели и ПРО модели, " +
                    "разбитую по разрешениям. Можно указать один или несколько userId через параметр userIds. " +
                    "Если указано несколько userId, статистика будет агрегирована. Требуется роль ADMIN.")
    @GetMapping("/users/statistics")
    public Mono<ResponseEntity<UserStatisticsDTO>> getUserStatistics(
            @RequestParam(required = false) List<Long> userIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return SecurityUtil.getCurrentAdmin(userService)
                .flatMap(admin -> adminService.getUserStatistics(userIds, startDate, endDate))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Ошибка получения статистики пользователей {}: {}", userIds, e.getMessage());
                    if (e instanceof IllegalArgumentException) {
                        return Mono.just(ResponseEntity.status(404).build());
                    }
                    return Mono.just(ResponseEntity.status(403).build());
                });
    }

    @Operation(summary = "Получить настройки провайдеров по моделям",
            description = "Возвращает для каждой модели список провайдеров с доступностью и активным провайдером. Требуется роль ADMIN.")
    @GetMapping("/generation-providers")
    public Mono<ResponseEntity<List<ru.oparin.troyka.model.dto.admin.ModelProviderSettingsDTO>>> getGenerationProviders() {
        return SecurityUtil.getCurrentAdmin(userService)
                .flatMap(admin -> buildModelProviderSettingsList())
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Ошибка получения списка провайдеров: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(403).build());
                });
    }

    private Mono<List<ru.oparin.troyka.model.dto.admin.ModelProviderSettingsDTO>> buildModelProviderSettingsList() {
        Mono<Boolean> falAvailable = providerRouter.getProvider(GenerationProvider.FAL_AI).isAvailable().defaultIfEmpty(false);
        Mono<Boolean> laoZhangAvailable = providerRouter.getProvider(GenerationProvider.LAOZHANG_AI).isAvailable().defaultIfEmpty(false);
        return Mono.zip(falAvailable, laoZhangAvailable)
                .flatMapMany(availability -> Flux.fromArray(GenerationModelType.values())
                        .flatMap(modelType -> providerSettingsService.getActiveProvider(modelType)
                                .map(activeProvider -> ru.oparin.troyka.model.dto.admin.ModelProviderSettingsDTO.builder()
                                        .modelType(modelType.name())
                                        .modelDisplayName(getModelDisplayName(modelType))
                                        .providers(List.of(
                                                GenerationProviderDTO.fromProvider(
                                                        GenerationProvider.FAL_AI,
                                                        availability.getT1(),
                                                        activeProvider == GenerationProvider.FAL_AI
                                                ),
                                                GenerationProviderDTO.fromProvider(
                                                        GenerationProvider.LAOZHANG_AI,
                                                        availability.getT2(),
                                                        activeProvider == GenerationProvider.LAOZHANG_AI
                                                )
                                        ))
                                        .build())))
                .collectList();
    }

    private static String getModelDisplayName(GenerationModelType modelType) {
        return switch (modelType) {
            case NANO_BANANA -> "Nano Banana";
            case NANO_BANANA_PRO -> "Nano Banana PRO";
            case SEEDREAM_4_5 -> "Seedream 4.5";
        };
    }

    @Operation(summary = "Установить активного провайдера для модели",
            description = "Переключает активного провайдера генерации для указанной модели. Требуется роль ADMIN.")
    @PutMapping("/generation-providers/active")
    public Mono<ResponseEntity<MessageResponse>> setActiveProvider(@Valid @RequestBody SetActiveProviderRequest request) {
        return SecurityUtil.getCurrentAdmin(userService)
                .flatMap(admin -> {
                    GenerationModelType modelType;
                    try {
                        modelType = GenerationModelType.valueOf(request.getModelType());
                    } catch (IllegalArgumentException e) {
                        modelType = GenerationModelType.fromName(request.getModelType());
                    }
                    GenerationProvider provider = GenerationProvider.fromCode(request.getProvider());
                    if (provider == null) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(new MessageResponse("Неизвестный провайдер: " + request.getProvider())));
                    }
                    GenerationModelType finalModelType = modelType;
                    return providerSettingsService.setActiveProvider(modelType, provider)
                            .map(settings -> ResponseEntity.ok(
                                    new MessageResponse("Для модели " + finalModelType.name() + " установлен провайдер: " + provider.getDisplayName())
                            ));
                })
                .onErrorResume(e -> {
                    log.error("Ошибка установки активного провайдера: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(403)
                            .body(new MessageResponse("Ошибка установки провайдера: " + e.getMessage())));
                });
    }

    @Operation(summary = "Получить статистику fallback переключений между провайдерами",
            description = "Возвращает статистику автоматических переключений на резервный провайдер при ошибках. Требуется роль ADMIN.")
    @GetMapping("/provider-fallback-stats")
    public Mono<ResponseEntity<ProviderFallbackStatsDTO>> getProviderFallbackStats() {
        return SecurityUtil.getCurrentAdmin(userService)
                .flatMap(admin -> fallbackMetricsService.getFallbackStats()
                        .map(ResponseEntity::ok))
                .onErrorResume(e -> {
                    log.error("Ошибка получения статистики fallback: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(403).build());
                });
    }

    @Operation(summary = "Заблокировать или разблокировать пользователя",
            description = "Изменяет статус блокировки пользователя. Требуется роль ADMIN.")
    @PutMapping("/users/{userId}/block")
    public Mono<ResponseEntity<MessageResponse>> blockUser(
            @PathVariable Long userId,
            @RequestParam Boolean blocked) {
        return SecurityUtil.getCurrentAdmin(userService)
                .flatMap(admin -> adminService.setUserBlockedStatus(userId, blocked)
                        .map(user -> {
                            String message = blocked ? "Пользователь заблокирован" : "Пользователь разблокирован";
                            log.info("Администратор {} изменил статус блокировки пользователя {} на {}",
                                    admin.getUsername(), userId, blocked);
                            return ResponseEntity.ok(new MessageResponse(message));
                        }))
                .onErrorResume(e -> {
                    log.error("Ошибка изменения статуса блокировки пользователя {}: {}", userId, e.getMessage());
                    return Mono.just(ResponseEntity.badRequest()
                            .body(new MessageResponse("Ошибка: " + e.getMessage())));
                });
    }

    @Operation(summary = "Получить статистику блокированных регистраций",
            description = "Возвращает статистику блокированных регистраций с временных email доменов. Требуется роль ADMIN.")
    @GetMapping("/blocked-registrations/stats")
    public Mono<ResponseEntity<BlockedRegistrationStatsDTO>> getBlockedRegistrationStats() {
        return SecurityUtil.getCurrentAdmin(userService)
                .flatMap(admin -> blockedRegistrationMetricsService.getStats())
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Ошибка получения статистики блокированных регистраций: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(403).build());
                });
    }
}

