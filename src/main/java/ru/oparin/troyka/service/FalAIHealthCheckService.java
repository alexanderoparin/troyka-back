package ru.oparin.troyka.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.oparin.troyka.config.properties.FalAiProperties;
import ru.oparin.troyka.model.enums.SystemStatus;

import java.time.Duration;

/**
 * Сервис для проверки доступности FAL AI API.
 * Выполняет периодические проверки с несколькими ретраями и автоматически обновляет статус системы.
 */
@Slf4j
@Service
public class FalAIHealthCheckService {

    private final WebClient webClient;
    private final SystemStatusService systemStatusService;
    private final FalAiProperties.HealthCheck healthCheckProperties;

    public FalAIHealthCheckService(WebClient.Builder webClientBuilder,
                                   FalAiProperties falAiProperties,
                                   SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
        this.healthCheckProperties = falAiProperties.getHealthCheck() != null 
                ? falAiProperties.getHealthCheck() 
                : new FalAiProperties.HealthCheck();
        this.webClient = webClientBuilder
                .baseUrl(falAiProperties.getApi().getUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Key " + falAiProperties.getApi().getKey())
                .build();
    }

    /**
     * Проверка доступности FAL AI API с несколькими ретраями.
     * Вызывается из планировщика задач.
     */
    public void checkFalAIHealth() {
        if (!healthCheckProperties.isEnabled()) {
            log.debug("Проверка здоровья FAL AI отключена");
            return;
        }

        log.debug("Начало проверки доступности FAL AI API");
        
        checkFalAIWithRetries()
                .subscribe(
                        isHealthy -> {
                            if (isHealthy) {
                                log.debug("FAL AI API доступен, проверяем текущий статус");
                                // Если система была в проблемном состоянии, проверяем, нужно ли вернуть ACTIVE
                                systemStatusService.getCurrentStatusWithMetadata()
                                        .flatMap(currentStatus -> {
                                            if (currentStatus.getStatus() != SystemStatus.ACTIVE && 
                                                currentStatus.getIsSystem() != null && 
                                                currentStatus.getIsSystem()) {
                                                // Автоматически вернули систему в ACTIVE
                                                log.info("FAL AI API снова доступен, автоматически возвращаем статус в ACTIVE");
                                                return systemStatusService.updateStatus(
                                                        SystemStatus.ACTIVE,
                                                        null,
                                                        true
                                                );
                                            }
                                            return Mono.empty();
                                        })
                                        .subscribe();
                            } else {
                                log.warn("FAL AI API недоступен после {} попыток, устанавливаем статус DEGRADED", healthCheckProperties.getRetryCount());
                                systemStatusService.updateStatus(
                                        SystemStatus.DEGRADED,
                                        SystemStatus.DEGRADED.getDefaultMessage(),
                                        true
                                ).subscribe();
                            }
                        },
                        error -> {
                            log.error("Ошибка при проверке доступности FAL AI API", error);
                            systemStatusService.updateStatus(
                                    SystemStatus.DEGRADED,
                                    SystemStatus.DEGRADED.getDefaultMessage(),
                                    true
                            ).subscribe();
                        }
                );
    }

    /**
     * Проверка доступности FAL AI с несколькими ретраями.
     *
     * @return true, если API доступен, false - если недоступен после всех попыток
     */
    private Mono<Boolean> checkFalAIWithRetries() {
        return checkFalAIHealthOnce()
                .retryWhen(Retry.fixedDelay(healthCheckProperties.getRetryCount() - 1, Duration.ofMillis(healthCheckProperties.getRetryDelayMs()))
                        .filter(throwable -> throwable instanceof WebClientRequestException || 
                                            throwable instanceof WebClientResponseException)
                        .doBeforeRetry(retrySignal -> 
                                log.debug("Повторная попытка проверки FAL AI ({}/{})", 
                                        retrySignal.totalRetries() + 1, healthCheckProperties.getRetryCount()))
                )
                .onErrorReturn(false);
    }

    /**
     * Однократная проверка доступности FAL AI API.
     * Выполняет HEAD запрос к базовому URL для проверки доступности.
     * HEAD запрос легче и быстрее, чем GET с телом ответа.
     *
     * @return true, если API доступен, false - если недоступен
     */
    private Mono<Boolean> checkFalAIHealthOnce() {
        // Используем HEAD запрос к базовому URL для проверки доступности
        // Это легче, чем делать полный GET запрос
        return webClient.head()
                .uri("/")
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofMillis(healthCheckProperties.getTimeoutMs()))
                .map(response -> {
                    log.debug("FAL AI API доступен, статус: {}", response.getStatusCode());
                    return true;
                })
                .onErrorResume(WebClientRequestException.class, e -> {
                    log.debug("Ошибка подключения к FAL AI API: {}", e.getMessage());
                    return Mono.just(false);
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    // Если сервер ответил (даже с ошибкой), значит он доступен
                    // 401/403 - сервер работает, но нужна авторизация (это нормально)
                    // 404 - может быть нормально, если эндпоинт не существует
                    // 5XX - проблемы, но сервер отвечает
                    log.debug("FAL AI API ответил с кодом: {}", e.getStatusCode());
                    // Считаем доступным, если получили любой ответ от сервера
                    return Mono.just(e.getStatusCode().is2xxSuccessful() || 
                                    e.getStatusCode().is4xxClientError());
                })
                .onErrorResume(Exception.class, e -> {
                    log.debug("Неожиданная ошибка при проверке FAL AI API: {}", e.getMessage());
                    return Mono.just(false);
                });
    }
}

