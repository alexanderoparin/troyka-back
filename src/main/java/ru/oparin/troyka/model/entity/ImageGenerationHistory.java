package ru.oparin.troyka.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import ru.oparin.troyka.util.JsonUtils;

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
     * Массив URL сгенерированных изображений.
     * Хранится в формате JSONB для поддержки множественных изображений от одного промпта.
     */
    @Column("image_urls")
    private Object imageUrlsJson;  // Используем Object для JSONB

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
     * Массив URL изображений, которые были отправлены в FAL AI для обработки.
     * Хранится в формате JSONB для поддержки множественных входных изображений.
     * Может быть null, если генерация выполнена без входных изображений.
     */
    @Column("input_image_urls")
    private String inputImageUrlsJson;  // Храним как String для R2DBC

    /**
     * Описание изображения, сгенерированное ИИ (если предоставлено FalAI).
     * Может содержать дополнительную информацию о сгенерированном изображении.
     */
    private String description;

    /**
     * Идентификатор стиля изображения (ссылка на art_styles.id).
     * По умолчанию 1 (Без стиля).
     */
    @Column("style_id")
    private Long styleId;

    /**
     * Получить список URL сгенерированных изображений из JSON.
     * Вспомогательный метод для работы с JSONB полем.
     */
    @Transient
    public List<String> getImageUrls() {
        if (imageUrlsJson == null) {
            return List.of();
        }
        return JsonUtils.parseJsonToList(imageUrlsJson);
    }

    /**
     * Установить список URL сгенерированных изображений, сериализовав в JSON.
     * Вспомогательный метод для работы с JSONB полем.
     */
    @Transient
    public void setImageUrls(List<String> urls) {
        this.imageUrlsJson = JsonUtils.convertListToJson(urls);
    }

    /**
     * Получить список URL входных изображений из JSON.
     * Вспомогательный метод для работы с JSONB полем.
     */
    @Transient
    public List<String> getInputImageUrls() {
        if (inputImageUrlsJson == null) {
            return List.of();
        }
        return JsonUtils.parseJsonToList(inputImageUrlsJson);
    }

    /**
     * Установить список URL входных изображений, сериализовав в JSON.
     * Вспомогательный метод для работы с JSONB полем.
     */
    @Transient
    public void setInputImageUrls(List<String> urls) {
        this.inputImageUrlsJson = JsonUtils.convertListToJson(urls);
    }
}