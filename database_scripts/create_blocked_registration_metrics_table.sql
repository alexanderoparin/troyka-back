-- Таблица для метрик блокированных регистраций с временных email доменов
-- Сохраняет информацию о попытках регистрации с заблокированными доменами

CREATE TABLE IF NOT EXISTS troyka.blocked_registration_metrics (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    email_domain VARCHAR(255) NOT NULL,
    username VARCHAR(255),
    ip_address VARCHAR(45), -- IPv6 может быть до 45 символов
    user_agent TEXT,
    registration_method VARCHAR(50) NOT NULL, -- 'EMAIL' или 'TELEGRAM'
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Комментарии к таблице и полям
COMMENT ON TABLE troyka.blocked_registration_metrics IS 'Метрики блокированных регистраций с временных email доменов';
COMMENT ON COLUMN troyka.blocked_registration_metrics.email IS 'Email адрес, с которого была попытка регистрации';
COMMENT ON COLUMN troyka.blocked_registration_metrics.email_domain IS 'Домен email адреса (для быстрой фильтрации)';
COMMENT ON COLUMN troyka.blocked_registration_metrics.username IS 'Имя пользователя, которое пытались использовать (если было указано)';
COMMENT ON COLUMN troyka.blocked_registration_metrics.ip_address IS 'IP адрес, с которого была попытка регистрации';
COMMENT ON COLUMN troyka.blocked_registration_metrics.user_agent IS 'User-Agent браузера/клиента';
COMMENT ON COLUMN troyka.blocked_registration_metrics.registration_method IS 'Метод регистрации: EMAIL или TELEGRAM';
COMMENT ON COLUMN troyka.blocked_registration_metrics.created_at IS 'Дата и время попытки регистрации';