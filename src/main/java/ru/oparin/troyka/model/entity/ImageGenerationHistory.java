package ru.oparin.troyka.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.QueueStatus;
import ru.oparin.troyka.model.enums.Resolution;
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
     * Идентификатор стиля изображения (ссылка на art_styles.id).
     * По умолчанию 1 (Без стиля).
     */
    @Column("style_id")
    private Long styleId;

    /**
     * Соотношение сторон изображения.
     * Возможные значения: 21:9, 16:9, 3:2, 4:3, 5:4, 1:1, 4:5, 3:4, 2:3, 9:16.
     * По умолчанию: 1:1.
     */
    @Column("aspect_ratio")
    private String aspectRatio;

    /**
     * Тип модели, использованной для генерации.
     * По умолчанию NANO_BANANA.
     */
    @Column("model_type")
    private String modelType = GenerationModelType.NANO_BANANA.getName();

    /**
     * Разрешение изображения.
     * Задается только для моделей, которые поддерживают resolution параметр (например, NANO_BANANA_PRO).
     * null для обычных моделей (NANO_BANANA).
     */
    @Column("resolution")
    private String resolution;

    /**
     * Идентификатор запроса в очереди Fal.ai.
     * Используется для отслеживания статуса запроса через API Fal.ai.
     */
    @Column("fal_request_id")
    private String falRequestId;

    /**
     * Статус запроса в очереди Fal.ai.
     * Возможные значения: IN_QUEUE, IN_PROGRESS, COMPLETED, FAILED.
     * null означает, что запрос был выполнен синхронно (старый способ).
     */
    @Column("queue_status")
    private QueueStatus queueStatus;

    /**
     * Позиция запроса в очереди Fal.ai.
     * Показывает, на какой позиции находится запрос в очереди.
     * null, если запрос не в очереди или уже обрабатывается.
     */
    @Column("queue_position")
    private Integer queuePosition;

    /**
     * Количество запрошенных изображений.
     * Используется для корректного расчета возврата поинтов при ошибках.
     */
    @Column("num_images")
    private Integer numImages;

    /**
     * Стоимость генерации в поинтах.
     * Рассчитывается как: points_per_image * num_images.
     * Для обычной модели (NANO_BANANA): 2 * num_images.
     * Для PRO модели (NANO_BANANA_PRO): зависит от разрешения (8/9/15) * num_images.
     */
    @Column("points_cost")
    private Integer pointsCost;

    /**
     * Дата и время последнего обновления статуса запроса.
     * Автоматически обновляется при изменении статуса.
     */
    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    /**
     * Флаг удаления записи.
     * true означает, что запись помечена как удаленная (soft delete).
     * По умолчанию false.
     */
    @Column("deleted")
    @Builder.Default
    private Boolean deleted = false;

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

    /**
     * Проверить, является ли запрос активным (в очереди или обрабатывается).
     * 
     * @return true если запрос активен (IN_QUEUE или IN_PROGRESS), false в противном случае
     */
    @Transient
    public boolean isActive() {
        return QueueStatus.isActive(queueStatus);
    }

    public GenerationModelType getGenerationModelType() {
        return GenerationModelType.fromName(modelType);
    }

    public Resolution getResolutionEnum() {
        return Resolution.fromValue(resolution);
    }
}