package ru.oparin.solving.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.oparin.solving.service.HealthService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(healthService.getBasicHealth());
    }

    @GetMapping("/system")
    public ResponseEntity<Map<String, Object>> systemHealth() {
        return ResponseEntity.ok(healthService.getSystemHealth());
    }

    @GetMapping("/database")
    public ResponseEntity<Map<String, Object>> databaseHealth() {
        Map<String, Object> dbHealth = healthService.getDatabaseHealth();

        if ("ERROR".equals(dbHealth.get("status")) || "DISCONNECTED".equals(dbHealth.get("status"))) {
            return ResponseEntity.status(503).body(dbHealth);
        }

        return ResponseEntity.ok(dbHealth);
    }

    @GetMapping("/full")
    public ResponseEntity<Map<String, Object>> fullHealthCheck() {
        Map<String, Object> fullHealth = new HashMap<>();

        fullHealth.put("basic", healthService.getBasicHealth());
        fullHealth.put("system", healthService.getSystemHealth());
        fullHealth.put("database", healthService.getDatabaseHealth());

        // Проверяем общий статус
        boolean allHealthy = !fullHealth.containsValue("ERROR") && !fullHealth.containsValue("DISCONNECTED");
        fullHealth.put("overallStatus", allHealthy ? "HEALTHY" : "DEGRADED");

        return allHealthy ?
                ResponseEntity.ok(fullHealth) :
                ResponseEntity.status(503).body(fullHealth);
    }
}