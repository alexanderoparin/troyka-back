package ru.oparin.troyka.service;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class HealthService {

    private final DatabaseClient databaseClient;
    private final long appStart = System.currentTimeMillis();

    public HealthService(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Map<String, Object> getBasicHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("service", "troyka-backend");
        health.put("version", "1.0.0");
        return health;
    }

    public Map<String, Object> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();

        Runtime runtime = Runtime.getRuntime();
        health.put("status", "UP");
        health.put("javaVersion", System.getProperty("java.version"));
        health.put("freeMemoryMB", runtime.freeMemory() / 1024 / 1024);
        health.put("totalMemoryMB", runtime.totalMemory() / 1024 / 1024);
        health.put("maxMemoryMB", runtime.maxMemory() / 1024 / 1024);
        health.put("availableProcessors", runtime.availableProcessors());
        health.put("uptimeSeconds", (System.currentTimeMillis() - appStart) / 1000);

        return health;
    }

    public Mono<Map<String, Object>> getDatabaseHealth() {
        Map<String, Object> health = new HashMap<>();

        return databaseClient.sql("SELECT 1")
                .fetch()
                .first()
                .map(result -> {
                    health.put("status", "CONNECTED");
                    health.put("database", "PostgreSQL");
                    health.put("isValid", true);
                    return health;
                })
                .onErrorResume(e -> {
                    health.put("status", "ERROR");
                    health.put("error", e.getMessage());
                    health.put("database", "PostgreSQL");
                    return Mono.just(health);
                });
    }
}