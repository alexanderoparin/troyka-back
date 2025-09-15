package ru.oparin.solving.model.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class HealthResponse {
    private String status;
    private String service;
    private String version;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public HealthResponse(String status, String service, String version) {
        this.status = status;
        this.service = service;
        this.version = version;
        this.timestamp = LocalDateTime.now();
    }
}