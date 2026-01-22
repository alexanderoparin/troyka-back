-- Создание таблицы для метрик fallback переключений между провайдерами генерации изображений
-- Таблица хранит информацию о каждом автоматическом переключении на резервный провайдер

CREATE TABLE IF NOT EXISTS troyka.provider_fallback_metrics (
    id BIGSERIAL PRIMARY KEY,
    active_provider VARCHAR(50) NOT NULL,
    fallback_provider VARCHAR(50) NOT NULL,
    error_type VARCHAR(100) NOT NULL,
    http_status INTEGER,
    error_message TEXT,
    user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Комментарии к таблице и полям
COMMENT ON TABLE troyka.provider_fallback_metrics IS 'Метрики fallback переключений между провайдерами генерации изображений';
COMMENT ON COLUMN troyka.provider_fallback_metrics.active_provider IS 'Активный провайдер, который не смог выполнить запрос (FAL_AI или LAOZHANG_AI)';
COMMENT ON COLUMN troyka.provider_fallback_metrics.fallback_provider IS 'Резервный провайдер, на который произошло переключение';
COMMENT ON COLUMN troyka.provider_fallback_metrics.error_type IS 'Тип ошибки (TIMEOUT, CONNECTION_ERROR, HTTP_5XX, PAYLOAD_TOO_LARGE и т.д.)';
COMMENT ON COLUMN troyka.provider_fallback_metrics.http_status IS 'HTTP статус код ошибки (может быть null для ошибок подключения или таймаутов)';
COMMENT ON COLUMN troyka.provider_fallback_metrics.error_message IS 'Сообщение об ошибке (краткое)';
COMMENT ON COLUMN troyka.provider_fallback_metrics.user_id IS 'Идентификатор пользователя, для которого произошло переключение (может быть null)';
COMMENT ON COLUMN troyka.provider_fallback_metrics.created_at IS 'Дата и время переключения на резервный провайдер';
