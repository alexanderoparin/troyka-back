package ru.oparin.troyka.service.provider;

import java.time.Duration;

/**
 * Константы для провайдеров генерации изображений.
 * Централизованное хранение всех констант, используемых провайдерами.
 */
public final class ProviderConstants {

    private ProviderConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Константы для LaoZhang AI провайдера.
     */
    public static final class LaoZhang {
        private LaoZhang() {
            throw new UnsupportedOperationException("Utility class");
        }

        /**
         * Шаблон endpoint для Google Native Format API.
         */
        public static final String ENDPOINT_TEMPLATE = "/v1beta/models/%s:generateContent";

        /**
         * Таймаут для HTTP запросов (12 минут для поддержки генерации 4K).
         */
        public static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(12);

        /**
         * Таймаут подключения (30 секунд).
         */
        public static final int CONNECT_TIMEOUT_MS = 30_000;

        /**
         * Поддиректория для сохранения изображений LaoZhang AI.
         */
        public static final String IMAGE_SUBDIRECTORY = "lz";

        /**
         * Префикс для data URL изображений.
         */
        public static final String DATA_URL_PREFIX = "data:";

        /**
         * Разделитель для data URL.
         */
        public static final String DATA_URL_SEPARATOR = ";base64,";

        /**
         * Максимальный размер одного файла (изображения) в запросе к LaoZhang: 7 MB.
         * Изображения больше лимита сжимаются перед отправкой.
         */
        public static final long MAX_SINGLE_IMAGE_SIZE_BYTES = 7 * 1024 * 1024;

        /**
         * Максимальный размер всего request body (все файлы + данные) в запросе к LaoZhang: 20 MB.
         */
        public static final long MAX_REQUEST_BODY_SIZE_BYTES = 20 * 1024 * 1024;
    }

    /**
     * Сообщения об ошибках для провайдеров.
     */
    public static final class ErrorMessages {
        private ErrorMessages() {
            throw new UnsupportedOperationException("Utility class");
        }

        public static final String INSUFFICIENT_POINTS = "Недостаточно поинтов для генерации изображений. Требуется: %d";
        public static final String EMPTY_RESPONSE = "Пустой ответ от провайдера";
        public static final String NO_IMAGES_IN_RESPONSE = "Не найдено изображений в ответе от провайдера";
        public static final String TIMEOUT_MESSAGE = "Превышено время ожидания ответа от сервиса генерации. Попробуйте позже.";
        public static final String CONNECTION_ERROR = "Не удалось подключиться к сервису генерации. Проверьте интернет или попробуйте позже.";
        public static final String PROVIDER_ERROR_TEMPLATE = "Сервис генерации вернул ошибку. Статус: %s, причина: %s";
        public static final String PROVIDER_ERROR_WITH_BODY_TEMPLATE = "Сервис генерации вернул ошибку. Статус: %s, причина: %s, тело ответа: %s";
        public static final String UNKNOWN_ERROR_TEMPLATE = "Произошла ошибка при работе с сервисом генерации: %s";
        public static final String PROVIDER_NOT_FOUND = "Провайдер %s не найден в списке доступных провайдеров";
    }
}
