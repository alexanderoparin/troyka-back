package ru.oparin.troyka.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Сущность для метрик блокированных регистраций с временных email доменов.
 */
@Table(value = "blocked_registration_metrics", schema = "troyka")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockedRegistrationMetric {

    /**
     * Уникальный идентификатор записи.
     */
    @Id
    private Long id;

    /**
     * Email адрес, с которого была попытка регистрации.
     */
    private String email;

    /**
     * Домен email адреса (для быстрой фильтрации).
     */
    private String emailDomain;

    /**
     * Имя пользователя, которое пытались использовать (если было указано).
     */
    private String username;

    /**
     * IP адрес, с которого была попытка регистрации.
     */
    private String ipAddress;

    /**
     * User-Agent браузера/клиента.
     */
    private String userAgent;

    /**
     * Метод регистрации: EMAIL или TELEGRAM.
     */
    private String registrationMethod;

    /**
     * Дата и время попытки регистрации.
     */
    @CreatedDate
    private LocalDateTime createdAt;
}
