package ru.oparin.troyka.service.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.exception.FalAIException;
import ru.oparin.troyka.exception.ProviderFallbackException;
import ru.oparin.troyka.model.dto.fal.ImageRs;
import ru.oparin.troyka.service.UserPointsService;

import java.util.concurrent.TimeoutException;

/**
 * Обработчик ошибок для провайдеров генерации изображений.
 * Централизует логику обработки ошибок и возврата поинтов пользователям.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderErrorHandler {

    private final UserPointsService userPointsService;

    /**
     * Обработать ошибку и вернуть поинты пользователю при необходимости.
     *
     * @param userId         идентификатор пользователя
     * @param error          исключение
     * @param pointsNeeded   количество поинтов, которое нужно вернуть
     * @param pointsDeducted флаг, указывающий, были ли поинты списаны
     * @return Mono с ошибкой для дальнейшей обработки
     */
    public Mono<ImageRs> handleError(Long userId, Throwable error, Integer pointsNeeded, boolean pointsDeducted) {
        // Проверяем, требует ли ошибка fallback - для таких ошибок логируем как WARN без stack trace
        boolean requiresFallback = requiresFallback(error);
        if (requiresFallback) {
            log.warn("Ошибка провайдера (требует fallback) для userId={}, pointsNeeded={}, pointsDeducted={}: {}",
                    userId, pointsNeeded, pointsDeducted, error.getMessage());
        } else {
            // Для ошибок, не требующих fallback, логируем как ERROR с полным stack trace
            log.error("Ошибка при работе с провайдером для userId={}, pointsNeeded={}, pointsDeducted={}: {}",
                    userId, pointsNeeded, pointsDeducted, error.getMessage(), error);
        }

        // Если это уже ProviderFallbackException, возвращаем его без изменений
        if (error instanceof ProviderFallbackException) {
            return Mono.error(error);
        }

        // Если это FalAIException, проверяем, требует ли он fallback
        if (error instanceof FalAIException falError) {
            if (requiresFallback(error)) {
                // Преобразуем в ProviderFallbackException для fallback
                String message = falError.getMessage();
                String errorType = message != null && message.contains("Не найдено изображений") 
                        ? "NO_IMAGES_IN_RESPONSE" 
                        : message != null && message.contains("Пустой ответ")
                        ? "EMPTY_RESPONSE"
                        : "PROVIDER_ERROR";
                return Mono.error(new ProviderFallbackException(
                        message != null ? message : "Ошибка провайдера",
                        falError.getStatus(),
                        errorType,
                        falError.getStatus() != null ? falError.getStatus().value() : null
                ));
            }
            // Если fallback не требуется, возвращаем как есть
            return Mono.error(error);
        }

        // Обрабатываем таймауты (требуют fallback)
        if (isTimeoutError(error)) {
            return handleTimeout(userId, pointsNeeded, pointsDeducted);
        }

        // Обрабатываем ошибки подключения (требуют fallback)
        if (error instanceof WebClientRequestException) {
            return handleConnectionError(userId, pointsNeeded, pointsDeducted);
        }

        // Обрабатываем HTTP ошибки от провайдера
        if (error instanceof WebClientResponseException webError) {
            return handleProviderError(userId, pointsNeeded, pointsDeducted, webError);
        }

        // Обрабатываем неизвестные ошибки (требуют fallback)
        return handleUnknownError(userId, pointsNeeded, pointsDeducted, error);
    }

    /**
     * Проверить, требует ли ошибка fallback переключения на резервный провайдер.
     * <p>
     * Fallback требуется для:
     * - Таймаутов
     * - Ошибок подключения
     * - HTTP ошибок 5xx (серверные ошибки)
     * - HTTP ошибок 413 (Payload Too Large)
     * - HTTP ошибок 503 (Service Unavailable)
     * - Ошибок провайдера (FalAIException с определенными типами)
     * <p>
     * Fallback НЕ требуется для:
     * - HTTP ошибок 4xx (кроме 413, 503) - это ошибки клиента
     * - Ошибок валидации
     * - Недостаточно поинтов
     *
     * @param error ошибка
     * @return true если требуется fallback, false в противном случае
     */
    public boolean requiresFallback(Throwable error) {
        // Если это уже ProviderFallbackException, значит fallback уже требуется
        if (error instanceof ProviderFallbackException) {
            return true;
        }

        // Проверяем FalAIException - некоторые типы ошибок требуют fallback
        if (error instanceof FalAIException falError) {
            String message = falError.getMessage();
            // Ошибки типа "Не найдено изображений" или "Пустой ответ" - это проблемы провайдера, требуют fallback
            if (message != null && (
                    message.contains("Не найдено изображений") ||
                    message.contains("Пустой ответ") ||
                    message.contains("NO_IMAGE") ||
                    message.contains("PROVIDER_REJECTED")
            )) {
                return true;
            }
            // Остальные FalAIException не требуют fallback (например, недостаточно поинтов)
            return false;
        }

        // Таймауты требуют fallback
        if (isTimeoutError(error)) {
            return true;
        }

        // Ошибки подключения требуют fallback
        if (error instanceof WebClientRequestException) {
            return true;
        }

        // HTTP ошибки от провайдера
        if (error instanceof WebClientResponseException webError) {
            HttpStatus status = HttpStatus.resolve(webError.getStatusCode().value());
            if (status == null) {
                return false;
            }

            // Серверные ошибки (5xx) требуют fallback
            if (status.is5xxServerError()) {
                return true;
            }

            // Специфичные клиентские ошибки, которые могут быть проблемой провайдера
            if (status == HttpStatus.PAYLOAD_TOO_LARGE || status == HttpStatus.SERVICE_UNAVAILABLE) {
                return true;
            }

            // Остальные 4xx ошибки - это ошибки клиента, fallback не требуется
            return false;
        }

        // Неизвестные ошибки требуют fallback (на всякий случай)
        return true;
    }

    /**
     * Извлечь информацию об ошибке для метрик fallback.
     *
     * @param error ошибка
     * @return массив [errorType, httpStatus, errorMessage]
     */
    public String[] extractErrorInfo(Throwable error) {
        String errorType;
        Integer httpStatus = null;
        String errorMessage = error.getMessage();

        if (error instanceof ProviderFallbackException fallbackError) {
            errorType = fallbackError.getErrorType();
            httpStatus = fallbackError.getHttpStatus();
        } else if (isTimeoutError(error)) {
            errorType = "TIMEOUT";
        } else if (error instanceof WebClientRequestException) {
            errorType = "CONNECTION_ERROR";
        } else if (error instanceof WebClientResponseException webError) {
            httpStatus = webError.getStatusCode().value();
            HttpStatus status = HttpStatus.resolve(httpStatus);
            if (status != null && status.is5xxServerError()) {
                errorType = "HTTP_5XX";
            } else if (status == HttpStatus.PAYLOAD_TOO_LARGE) {
                errorType = "PAYLOAD_TOO_LARGE";
            } else if (status == HttpStatus.SERVICE_UNAVAILABLE) {
                errorType = "SERVICE_UNAVAILABLE";
            } else {
                errorType = "HTTP_ERROR";
            }
        } else {
            errorType = "UNKNOWN_ERROR";
        }

        return new String[]{errorType, httpStatus != null ? httpStatus.toString() : null, errorMessage};
    }

    /**
     * Проверить, является ли ошибка таймаутом.
     */
    private boolean isTimeoutError(Throwable error) {
        return error instanceof TimeoutException
                || (error.getCause() != null && error.getCause() instanceof TimeoutException)
                || (error.getMessage() != null && error.getMessage().toLowerCase().contains("timeout"));
    }

    /**
     * Обработать таймаут.
     */
    private Mono<ImageRs> handleTimeout(Long userId, Integer pointsNeeded, boolean pointsDeducted) {
        log.warn("Timeout при запросе к провайдеру для userId={}.", userId);
        return refundPointsIfNeeded(userId, pointsNeeded, pointsDeducted)
                .then(Mono.error(new ProviderFallbackException(
                        ProviderConstants.ErrorMessages.TIMEOUT_MESSAGE,
                        HttpStatus.REQUEST_TIMEOUT,
                        "TIMEOUT")));
    }

    /**
     * Обработать ошибку подключения.
     */
    private Mono<ImageRs> handleConnectionError(Long userId, Integer pointsNeeded, boolean pointsDeducted) {
        log.warn("Ошибка подключения к провайдеру для userId={}.", userId);
        return refundPointsIfNeeded(userId, pointsNeeded, pointsDeducted)
                .then(Mono.error(new ProviderFallbackException(
                        ProviderConstants.ErrorMessages.CONNECTION_ERROR,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "CONNECTION_ERROR")));
    }

    /**
     * Обработать HTTP ошибку от провайдера.
     */
    private Mono<ImageRs> handleProviderError(Long userId, Integer pointsNeeded, boolean pointsDeducted,
                                              WebClientResponseException webError) {
        String responseBody = webError.getResponseBodyAsString();
        log.warn("Ошибка от провайдера для userId={}. Статус: {}, тело: {}.",
                userId, webError.getStatusCode(), responseBody);

        String message = buildProviderErrorMessage(webError, responseBody);
        HttpStatus status = HttpStatus.resolve(webError.getStatusCode().value());
        
        // Определяем, требует ли ошибка fallback
        boolean requiresFallback = status != null && (
                status.is5xxServerError() ||
                status == HttpStatus.PAYLOAD_TOO_LARGE ||
                status == HttpStatus.SERVICE_UNAVAILABLE
        );

        if (requiresFallback) {
            String errorType = status.is5xxServerError() ? "HTTP_5XX" :
                    status == HttpStatus.PAYLOAD_TOO_LARGE ? "PAYLOAD_TOO_LARGE" : "SERVICE_UNAVAILABLE";
            return refundPointsIfNeeded(userId, pointsNeeded, pointsDeducted)
                    .then(Mono.error(new ProviderFallbackException(
                            message,
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            errorType,
                            webError.getStatusCode().value())));
        } else {
            // Ошибки клиента (4xx кроме указанных выше) не требуют fallback
            return refundPointsIfNeeded(userId, pointsNeeded, pointsDeducted)
                    .then(Mono.error(new FalAIException(message, HttpStatus.UNPROCESSABLE_ENTITY)));
        }
    }

    /**
     * Построить сообщение об ошибке от провайдера.
     */
    private String buildProviderErrorMessage(WebClientResponseException webError, String responseBody) {
        String baseMessage = String.format(
                ProviderConstants.ErrorMessages.PROVIDER_ERROR_TEMPLATE,
                webError.getStatusCode(),
                webError.getStatusText()
        );

        if (responseBody != null && !responseBody.isEmpty()) {
            return String.format(
                    ProviderConstants.ErrorMessages.PROVIDER_ERROR_WITH_BODY_TEMPLATE,
                    webError.getStatusCode(),
                    webError.getStatusText(),
                    responseBody
            );
        }

        return baseMessage;
    }

    /**
     * Обработать неизвестную ошибку.
     */
    private Mono<ImageRs> handleUnknownError(Long userId, Integer pointsNeeded, boolean pointsDeducted, Throwable error) {
        log.warn("Неизвестная ошибка при работе с провайдером для userId={}.", userId);
        String message = String.format(
                ProviderConstants.ErrorMessages.UNKNOWN_ERROR_TEMPLATE,
                error.getMessage()
        );
        return refundPointsIfNeeded(userId, pointsNeeded, pointsDeducted)
                .then(Mono.error(new ProviderFallbackException(
                        message,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "UNKNOWN_ERROR")));
    }

    /**
     * Вернуть поинты пользователю, если они были списаны.
     */
    private Mono<Void> refundPointsIfNeeded(Long userId, Integer pointsNeeded, boolean pointsDeducted) {
        if (!pointsDeducted) {
            log.debug("Поинты не были списаны для userId={}, возврат не требуется", userId);
            return Mono.empty();
        }
        return refundPoints(userId, pointsNeeded);
    }

    /**
     * Вернуть поинты пользователю.
     */
    private Mono<Void> refundPoints(Long userId, Integer pointsNeeded) {
        log.info("Возвращаем поинты пользователю {}: {}", userId, pointsNeeded);
        return userPointsService.addPointsToUser(userId, pointsNeeded)
                .doOnSuccess(updated -> log.info("Поинты возвращены пользователю {}: {}", userId, pointsNeeded))
                .doOnError(error -> log.error("Ошибка при возврате поинтов пользователю {}: {}",
                        userId, error.getMessage()))
                .then();
    }

    /**
     * Обработать отмену запроса (например, при разрыве соединения с клиентом).
     *
     * @param userId      идентификатор пользователя
     * @param pointsNeeded количество поинтов для возврата
     */
    public void handleCancellation(Long userId, Integer pointsNeeded) {
        log.warn("Запрос к провайдеру отменен для userId={}, возвращаем поинты: {}", userId, pointsNeeded);
        userPointsService.addPointsToUser(userId, pointsNeeded)
                .subscribe(
                        updated -> log.info("Поинты возвращены пользователю {} после отмены запроса: {}",
                                userId, pointsNeeded),
                        error -> log.error("Ошибка при возврате поинтов пользователю {} после отмены: {}",
                                userId, error.getMessage())
                );
    }
}
