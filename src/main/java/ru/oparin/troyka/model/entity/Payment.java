package ru.oparin.troyka.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;
import ru.oparin.troyka.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Сущность платежа.
 * Хранит информацию о платежах пользователей через платежную систему Робокасса,
 * включая статус, сумму, количество поинтов и данные интеграции.
 */
@Table(value = "payment", schema = "troyka")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    /**
     * Уникальный идентификатор платежа.
     * Автоматически генерируется базой данных.
     */
    @Id
    private Long id;

    /**
     * Идентификатор пользователя, совершившего платеж.
     * Ссылается на таблицу user.
     */
    private Long userId;

    /**
     * Сумма платежа в рублях.
     * Используется BigDecimal для точного представления денежных сумм.
     */
    private BigDecimal amount;

    /**
     * Описание платежа.
     * Содержит информацию о том, за что был совершен платеж.
     */
    private String description;

    /**
     * Статус платежа.
     * Определяет текущее состояние платежа в системе.
     * По умолчанию CREATED.
     */
    @Builder.Default
    private PaymentStatus status = PaymentStatus.CREATED;

    /**
     * Подпись от Робокассы.
     * Используется для проверки подлинности callback'ов от платежной системы.
     */
    private String robokassaSignature;

    /**
     * URL для оплаты.
     * Ссылка, по которой пользователь может совершить платеж.
     */
    private String paymentUrl;

    /**
     * Количество поинтов, которые будут начислены.
     * Количество поинтов, которое получит пользователь после успешной оплаты.
     */
    private Integer creditsAmount;

    /**
     * Время успешной оплаты.
     * Дата и время, когда платеж был успешно завершен.
     */
    private LocalDateTime paidAt;

    /**
     * Ответ от Робокассы при callback.
     * JSON-строка с данными, полученными от Робокассы при подтверждении платежа.
     */
    private String robokassaResponse;

    /**
     * Дата и время создания платежа.
     * Автоматически устанавливается при создании.
     */
    @CreatedDate
    private LocalDateTime createdAt;

    /**
     * Дата и время последнего обновления платежа.
     * Автоматически обновляется при изменении статуса или других данных.
     */
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
