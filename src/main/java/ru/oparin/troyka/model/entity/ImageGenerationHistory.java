package ru.oparin.troyka.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Сущность истории генерации изображений.
 * Хранит информацию о всех сгенерированных пользователем изображениях,
 * включая URL изображения и использованный промпт.
 */
@Table(value = "image_generation_history", schema = "troyka")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationHistory {

    /**
     * Уникальный идентификатор записи истории.
     * Автоматически генерируется базой данных.
     */
    @Id
    private Long id;

    /**
     * Идентификатор пользователя, сгенерировавшего изображение.
     * Ссылается на таблицу user.
     */
    private Long userId;

    /**
     * URL сгенерированного изображения.
     * Ссылка на файл изображения, хранящийся в файловой системе или облаке.
     */
    private String imageUrl;

    /**
     * Текстовое описание (промпт), использованное для генерации изображения.
     * Содержит инструкции для ИИ по созданию изображения.
     */
    private String prompt;

    /**
     * Дата и время генерации изображения.
     * Автоматически устанавливается при создании записи.
     */
    @CreatedDate
    private LocalDateTime createdAt;
}