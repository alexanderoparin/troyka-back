-- Добавление поля blocked в таблицу user для возможности блокировки пользователей
-- Все существующие пользователи по умолчанию будут активными (blocked = false)

ALTER TABLE troyka.user 
ADD COLUMN IF NOT EXISTS blocked BOOLEAN NOT NULL DEFAULT false;

-- Комментарий к полю
COMMENT ON COLUMN troyka.user.blocked IS 'Флаг блокировки пользователя. true - пользователь заблокирован, false - активен. По умолчанию false.';
