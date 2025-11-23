package ru.oparin.troyka.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.system.SystemStatusHistoryDTO;
import ru.oparin.troyka.model.dto.system.SystemStatusResponse;
import ru.oparin.troyka.model.entity.SystemStatusHistory;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.model.enums.SystemStatus;
import ru.oparin.troyka.repository.SystemStatusHistoryRepository;
import ru.oparin.troyka.repository.UserRepository;
import ru.oparin.troyka.util.SecurityUtil;

import java.time.LocalDateTime;

/**
 * Сервис для управления статусом системы и историей изменений.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemStatusService {

    private final SystemStatusHistoryRepository statusHistoryRepository;
    private final UserRepository userRepository;

    /**
     * Получить текущий статус системы для публичного API.
     * Возвращает последнюю запись истории, если статус не ACTIVE.
     *
     * @return текущий статус системы или ACTIVE с null сообщением, если записей нет
     */
    public Mono<SystemStatusResponse> getCurrentStatus() {
        return getSystemStatusHistoryWithLog()
                .map(history -> new SystemStatusResponse(
                        history.getStatus(),
                        history.getMessage()
                ))
                .defaultIfEmpty(new SystemStatusResponse(SystemStatus.ACTIVE, null));
    }

    /**
     * Получить текущий статус системы с дополнительной информацией для админки.
     * Включает информацию о том, было ли изменение автоматическим.
     *
     * @return текущий статус с флагом isSystem
     */
    public Mono<CurrentStatusWithMetadata> getCurrentStatusWithMetadata() {
        return getSystemStatusHistoryWithLog()
                .map(history -> CurrentStatusWithMetadata.builder()
                        .status(history.getStatus())
                        .message(history.getMessage())
                        .isSystem(history.getIsSystem())
                        .build())
                .defaultIfEmpty(CurrentStatusWithMetadata.builder()
                        .status(SystemStatus.ACTIVE)
                        .message(null)
                        .isSystem(false)
                        .build());
    }

    private Mono<SystemStatusHistory> getSystemStatusHistoryWithLog() {
        return statusHistoryRepository.findLatest()
                .doOnNext(systemStatusHistory -> {
                            if (!systemStatusHistory.getStatus().isActive())
                                log.debug("Текущий статус FAL AI API: {}", systemStatusHistory.getStatus().name());
                        }
                );
    }


    /**
     * Обновить статус системы (ручное изменение администратором).
     *
     * @param status  новый статус системы
     * @param message сообщение для пользователей
     * @return созданная запись истории
     */
    public Mono<SystemStatusHistory> updateStatusManually(SystemStatus status, String message) {
        return updateStatus(status, message, false);
    }

    /**
     * Обновить статус системы.
     * Создает новую запись в истории с указанным статусом и сообщением.
     *
     * @param status   новый статус системы
     * @param message  сообщение для пользователей (может быть null для ACTIVE)
     * @param isSystem флаг автоматического изменения (true - системное, false - ручное)
     * @return созданная запись истории
     */
    public Mono<SystemStatusHistory> updateStatus(SystemStatus status, String message, boolean isSystem) {
        Mono<Long> userIdMono;

        if (isSystem) {
            userIdMono = Mono.fromCallable(() -> null);
        } else {
            userIdMono = SecurityUtil.getCurrentUsername()
                    .flatMap(userRepository::findByUsername)
                    .map(User::getId)
                    .onErrorResume(e -> {
                        log.warn("Не удалось получить ID пользователя для обновления статуса: {}", e.getMessage());
                        return Mono.just(null);
                    });
        }

        return userIdMono.flatMap(userId -> {
            // Если сообщение не указано и статус не ACTIVE, используем дефолтное сообщение
            String finalMessage = status == SystemStatus.ACTIVE
                    ? null
                    : (message != null && !message.trim().isEmpty()
                    ? message.trim()
                    : status.getDefaultMessage());

            SystemStatusHistory history = SystemStatusHistory.builder()
                    .status(status)
                    .message(finalMessage)
                    .userId(isSystem ? null : userId)
                    .isSystem(isSystem)
                    .createdAt(LocalDateTime.now())
                    .build();

            return statusHistoryRepository.save(history)
                    .doOnSuccess(saved -> {
                        if (isSystem) {
                            log.info("Статус системы автоматически изменен на {}: {}", status, message);
                        } else {
                            log.info("Статус системы изменен пользователем {} на {}: {}", userId, status, message);
                        }
                    });
        });
    }

    /**
     * Получить историю изменений статуса системы.
     *
     * @param limit максимальное количество записей (по умолчанию 50)
     * @return список записей истории
     */
    public Flux<SystemStatusHistoryDTO> getHistory(int limit) {
        return statusHistoryRepository.findAll()
                .sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .take(limit)
                .flatMap(history -> {
                    if (history.getUserId() == null) {
                        return Mono.just(SystemStatusHistoryDTO.builder()
                                .id(history.getId())
                                .status(history.getStatus())
                                .message(history.getMessage())
                                .username(null)
                                .isSystem(history.getIsSystem())
                                .createdAt(history.getCreatedAt())
                                .build());
                    }
                    return userRepository.findById(history.getUserId())
                            .map(user -> SystemStatusHistoryDTO.builder()
                                    .id(history.getId())
                                    .status(history.getStatus())
                                    .message(history.getMessage())
                                    .username(user.getUsername())
                                    .isSystem(history.getIsSystem())
                                    .createdAt(history.getCreatedAt())
                                    .build())
                            .defaultIfEmpty(SystemStatusHistoryDTO.builder()
                                    .id(history.getId())
                                    .status(history.getStatus())
                                    .message(history.getMessage())
                                    .username("Удаленный пользователь")
                                    .isSystem(history.getIsSystem())
                                    .createdAt(history.getCreatedAt())
                                    .build());
                });
    }

    /**
     * Внутренний класс для хранения текущего статуса с метаданными.
     */
    @Data
    @Builder
    public static class CurrentStatusWithMetadata {
        private SystemStatus status;
        private String message;
        private Boolean isSystem;
    }
}

