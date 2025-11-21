package ru.oparin.troyka.model.enums;

/**
 * Перечисление статусов запросов в очереди Fal.ai.
 * Определяет различные состояния запроса генерации изображения в процессе обработки.
 */
public enum QueueStatus {
    
    /**
     * Запрос находится в очереди.
     * Запрос отправлен в очередь Fal.ai и ожидает обработки.
     */
    IN_QUEUE,
    
    /**
     * Запрос обрабатывается.
     * Запрос взят из очереди и в данный момент генерируется изображение.
     */
    IN_PROGRESS,
    
    /**
     * Запрос успешно завершен.
     * Генерация изображения завершена, результат получен.
     */
    COMPLETED,
    
    /**
     * Запрос завершился с ошибкой.
     * Генерация изображения не удалась из-за ошибки.
     */
    FAILED;

    /**
     * Преобразовать строку в QueueStatus.
     * Используется при получении данных из БД или от Fal.ai API.
     *
     * @param value строковое значение статуса
     * @return QueueStatus или null, если значение не распознано
     */
    public static QueueStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return QueueStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Проверить, является ли статус активным (в очереди или обрабатывается).
     *
     * @param status статус для проверки
     * @return true, если статус IN_QUEUE или IN_PROGRESS, иначе false
     */
    public static boolean isActive(QueueStatus status) {
        return status == IN_QUEUE || status == IN_PROGRESS;
    }
}


