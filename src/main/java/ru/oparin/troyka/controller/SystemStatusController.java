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
import ru.oparin.troyka.model.dto.system.SystemStatusResponse;
import ru.oparin.troyka.service.SystemStatusService;

/**
 * Публичный контроллер для получения статуса системы.
 * Используется фронтендом для отображения баннеров оповещений.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/system")
@Tag(name = "Статус системы", description = "API для получения статуса системы")
public class SystemStatusController {

    private final SystemStatusService systemStatusService;

    @Operation(summary = "Получить текущий статус системы",
            description = "Возвращает текущий статус системы и сообщение для пользователей. " +
                    "Если статус ACTIVE, сообщение будет null.")
    @GetMapping("/status")
    public Mono<ResponseEntity<SystemStatusResponse>> getStatus() {
        return systemStatusService.getCurrentStatus()
                .map(ResponseEntity::ok);
    }
}

