package ru.oparin.troyka.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * Идентификатор сессии, к которой относится эта генерация.
     * Ссылается на таблицу sessions.
     */
    @Column("session_id")
    private Long sessionId;

    /**
     * Номер итерации генерации в рамках сессии.
     * Используется для упорядочивания истории диалога.
     */
    @Column("iteration_number")
    private Integer iterationNumber;

    /**
     * Массив URL изображений, которые были отправлены в FAL AI для обработки.
     * Хранится в формате JSONB для поддержки множественных входных изображений.
     * Может быть null, если генерация выполнена без входных изображений.
     */
    @Column("input_image_urls")
    private String inputImageUrlsJson;  // Храним как String для R2DBC

    /**
     * Получить список URL входных изображений из JSON.
     * Вспомогательный метод для работы с JSONB полем.
     */
    @Transient
    public List<String> getInputImageUrls() {
        // TODO: Реализовать парсинг JSON в список
        return null;
    }

    /**
     * Установить список URL входных изображений, сериализовав в JSON.
     * Вспомогательный метод для работы с JSONB полем.
     */
    @Transient
    public void setInputImageUrls(List<String> urls) {
        // TODO: Реализовать сериализацию списка в JSON
    }
}