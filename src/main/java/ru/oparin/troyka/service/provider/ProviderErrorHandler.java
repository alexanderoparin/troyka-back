package ru.oparin.troyka.service.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.exception.FalAIException;
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
     * @param userId      идентификатор пользователя
     * @param error       исключение
     * @param pointsNeeded количество поинтов, которое нужно вернуть
     * @return Mono с ошибкой для дальнейшей обработки
     */
    public Mono<ImageRs> handleError(Long userId, Throwable error, Integer pointsNeeded) {
        log.error("Ошибка при работе с провайдером для userId={}, pointsNeeded={}: {}",
                userId, pointsNeeded, error.getMessage(), error);

        // Если это уже наш кастомный эксепшн, возвращаем его без изменений
        if (error instanceof FalAIException) {
            return Mono.error(error);
        }

        // Обрабатываем таймауты
        if (isTimeoutError(error)) {
            return handleTimeout(userId, pointsNeeded);
        }

        // Обрабатываем ошибки подключения
        if (error instanceof WebClientRequestException) {
            return handleConnectionError(userId, pointsNeeded);
        }

        // Обрабатываем HTTP ошибки от провайдера
        if (error instanceof WebClientResponseException webError) {
            return handleProviderError(userId, pointsNeeded, webError);
        }

        // Обрабатываем неизвестные ошибки
        return handleUnknownError(userId, pointsNeeded, error);
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
    private Mono<ImageRs> handleTimeout(Long userId, Integer pointsNeeded) {
        log.warn("Timeout при запросе к провайдеру для userId={}. Возвращаем поинты.", userId);
        return refundPoints(userId, pointsNeeded)
                .then(Mono.error(new FalAIException(
                        ProviderConstants.ErrorMessages.TIMEOUT_MESSAGE,
                        HttpStatus.REQUEST_TIMEOUT)));
    }

    /**
     * Обработать ошибку подключения.
     */
    private Mono<ImageRs> handleConnectionError(Long userId, Integer pointsNeeded) {
        log.warn("Ошибка подключения к провайдеру для userId={}. Возвращаем поинты.", userId);
        return refundPoints(userId, pointsNeeded)
                .then(Mono.error(new FalAIException(
                        ProviderConstants.ErrorMessages.CONNECTION_ERROR,
                        HttpStatus.SERVICE_UNAVAILABLE)));
    }

    /**
     * Обработать HTTP ошибку от провайдера.
     */
    private Mono<ImageRs> handleProviderError(Long userId, Integer pointsNeeded,
                                              WebClientResponseException webError) {
        String responseBody = webError.getResponseBodyAsString();
        log.warn("Ошибка от провайдера для userId={}. Статус: {}, тело: {}. Возвращаем поинты.",
                userId, webError.getStatusCode(), responseBody);

        String message = buildProviderErrorMessage(webError, responseBody);
        return refundPoints(userId, pointsNeeded)
                .then(Mono.error(new FalAIException(message, HttpStatus.UNPROCESSABLE_ENTITY)));
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
    private Mono<ImageRs> handleUnknownError(Long userId, Integer pointsNeeded, Throwable error) {
        log.warn("Неизвестная ошибка при работе с провайдером для userId={}. Возвращаем поинты.", userId);
        String message = String.format(
                ProviderConstants.ErrorMessages.UNKNOWN_ERROR_TEMPLATE,
                error.getMessage()
        );
        return refundPoints(userId, pointsNeeded)
                .then(Mono.error(new FalAIException(message, HttpStatus.INTERNAL_SERVER_ERROR)));
    }

    /**
     * Вернуть поинты пользователю.
     */
    private Mono<Void> refundPoints(Long userId, Integer pointsNeeded) {
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
