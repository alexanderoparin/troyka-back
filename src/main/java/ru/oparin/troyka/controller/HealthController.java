package ru.oparin.troyka.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.service.HealthService;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> healthCheck() {
        return Mono.fromCallable(() -> ResponseEntity.ok(healthService.getBasicHealth()));
    }

    @GetMapping("/system")
    public Mono<ResponseEntity<Map<String, Object>>> systemHealth() {
        return Mono.fromCallable(() -> ResponseEntity.ok(healthService.getSystemHealth()));
    }

    @GetMapping("/database")
    public Mono<ResponseEntity<Map<String, Object>>> databaseHealth() {
        return Mono.fromCallable(() -> {
            Map<String, Object> dbHealth = healthService.getDatabaseHealth();

            if ("ERROR".equals(dbHealth.get("status")) || "DISCONNECTED".equals(dbHealth.get("status"))) {
                return ResponseEntity.status(503).body(dbHealth);
            }

            return ResponseEntity.ok(dbHealth);
        });
    }

    @GetMapping("/full")
    public Mono<ResponseEntity<Map<String, Object>>> fullHealthCheck() {
        return Mono.fromCallable(() -> {
            log.debug("Запрос состояния сервера");
            Map<String, Object> fullHealth = new HashMap<>();

            fullHealth.put("basic", healthService.getBasicHealth());
            fullHealth.put("system", healthService.getSystemHealth());
            fullHealth.put("database", healthService.getDatabaseHealth());

            // Проверяем общий статус
            boolean allHealthy = !fullHealth.containsValue("ERROR") && !fullHealth.containsValue("DISCONNECTED");
            fullHealth.put("overallStatus", allHealthy ? "HEALTHY" : "DEGRADED");

            log.debug("{}", fullHealth);
            return allHealthy ?
                    ResponseEntity.ok(fullHealth) :
                    ResponseEntity.status(503).body(fullHealth);
        });
    }
}