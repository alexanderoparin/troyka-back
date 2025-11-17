package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.admin.AdminPaymentDTO;
import ru.oparin.troyka.model.dto.admin.AdminStatsDTO;
import ru.oparin.troyka.model.dto.admin.AdminUserDTO;
import ru.oparin.troyka.service.AdminService;
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
}

