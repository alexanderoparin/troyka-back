package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.admin.AdminPaymentDTO;
import ru.oparin.troyka.model.dto.admin.AdminStatsDTO;
import ru.oparin.troyka.model.dto.admin.AdminUserDTO;
import ru.oparin.troyka.model.dto.auth.MessageResponse;
import ru.oparin.troyka.model.dto.system.SystemStatusHistoryDTO;
import ru.oparin.troyka.model.dto.system.SystemStatusRequest;
import ru.oparin.troyka.service.AdminService;
import ru.oparin.troyka.service.SystemStatusService;
import ru.oparin.troyka.service.UserService;
import ru.oparin.troyka.util.SecurityUtil;

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

    @Operation(summary = "Получить все платежи",
            description = "Возвращает список всех платежей в системе. Требуется роль ADMIN.")
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
}

