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
        return Mono.just(ResponseEntity.ok(healthService.getBasicHealth()));
    }

    @GetMapping("/system")
    public Mono<ResponseEntity<Map<String, Object>>> systemHealth() {
        return Mono.just(ResponseEntity.ok(healthService.getSystemHealth()));
    }

    @GetMapping("/database")
    public Mono<ResponseEntity<Map<String, Object>>> databaseHealth() {
        return healthService.getDatabaseHealth()
                .map(dbHealth -> {
                    if ("ERROR".equals(dbHealth.get("status")) || "DISCONNECTED".equals(dbHealth.get("status"))) {
                        return ResponseEntity.status(503).body(dbHealth);
                    }
                    return ResponseEntity.ok(dbHealth);
                });
    }

    @GetMapping("/full")
    public Mono<ResponseEntity<Map<String, Object>>> fullHealthCheck() {
        return Mono.zip(
                Mono.just(healthService.getBasicHealth()),
                Mono.just(healthService.getSystemHealth()),
                healthService.getDatabaseHealth()
        ).map(tuple -> {
            Map<String, Object> basicHealth = tuple.getT1();
            Map<String, Object> systemHealth = tuple.getT2();
            Map<String, Object> databaseHealth = tuple.getT3();

            Map<String, Object> fullHealth = new HashMap<>();
            fullHealth.put("basic", basicHealth);
            fullHealth.put("system", systemHealth);
            fullHealth.put("database", databaseHealth);

            // Проверяем общий статус
            boolean allHealthy = !"ERROR".equals(databaseHealth.get("status")) &&
                    !"DISCONNECTED".equals(databaseHealth.get("status"));
            fullHealth.put("overallStatus", allHealthy ? "HEALTHY" : "DEGRADED");

            log.debug("{}", fullHealth);
            return allHealthy ?
                    ResponseEntity.ok(fullHealth) :
                    ResponseEntity.status(503).body(fullHealth);
        });
    }
}