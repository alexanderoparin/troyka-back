package ru.oparin.solving.service;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class HealthService {

    private final DataSource dataSource;
    private final long appStart = System.currentTimeMillis();

    public HealthService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, Object> getBasicHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("service", "solving-backend");
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

    public Map<String, Object> getDatabaseHealth() {
        Map<String, Object> health = new HashMap<>();

        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) { // 2 second timeout
                health.put("status", "CONNECTED");
                health.put("database", "PostgreSQL");
                health.put("isValid", true);
            } else {
                health.put("status", "DISCONNECTED");
                health.put("error", "Database connection is not valid");
            }
        } catch (SQLException e) {
            health.put("status", "ERROR");
            health.put("error", e.getMessage());
            health.put("database", "PostgreSQL");
        }

        return health;
    }
}