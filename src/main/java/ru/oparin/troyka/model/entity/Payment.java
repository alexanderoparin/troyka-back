package ru.oparin.troyka.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;
import ru.oparin.troyka.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table(value = "payment", schema = "troyka")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    private Long id;

    /**
     * Связь с пользователем
     */
    private Long userId;

    /**
     * Сумма платежа в рублях
     */
    private BigDecimal amount;

    /**
     * Описание платежа
     */
    private String description;

    /**
     * Статус платежа
     */
    private PaymentStatus status = PaymentStatus.CREATED;

    /**
     * Подпись от Робокассы
     */
    private String robokassaSignature;

    /**
     * URL для оплаты
     */
    private String paymentUrl;

    /**
     * Количество поинтов, которые будут начислены
     */
    private Integer creditsAmount;

    /**
     * Время успешной оплаты
     */
    private LocalDateTime paidAt;

    /**
     * Ответ от Робокассы при callback
     */
    private String robokassaResponse;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
