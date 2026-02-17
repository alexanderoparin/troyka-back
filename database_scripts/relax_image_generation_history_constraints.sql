-- Шаг 1: Найти ограничения на таблице image_generation_history.
-- Выполните этот запрос в БД и посмотрите результат — по нему видно, что может мешать INSERT.

-- 1a) CHECK-ограничения (часто ограничивают допустимые значения queue_status, model_type, resolution):
SELECT c.conname AS constraint_name,
       c.contype AS type,  -- c = check
       pg_get_constraintdef(c.oid, true) AS definition
FROM pg_constraint c
JOIN pg_class t ON c.conrelid = t.oid
JOIN pg_namespace n ON t.relnamespace = n.oid
WHERE n.nspname = 'troyka'
  AND t.relname = 'image_generation_history'
  AND c.contype = 'c'
ORDER BY c.conname;

-- 1b) Колонки с NOT NULL (если приложение вставляет NULL — вставка упадёт):
SELECT a.attname AS column_name,
       CASE WHEN a.attnotnull THEN 'NOT NULL' ELSE 'nullable' END AS nullability
FROM pg_attribute a
JOIN pg_class t ON a.attrelid = t.oid
JOIN pg_namespace n ON t.relnamespace = n.oid
WHERE n.nspname = 'troyka'
  AND t.relname = 'image_generation_history'
  AND a.attnum > 0
  AND NOT a.attisdropped
ORDER BY a.attnum;

-- 1c) Типы колонок model_type и queue_status (если ENUM — значение 'seedream-4.5' / IN_QUEUE может быть не в списке):
SELECT a.attname AS column_name,
       format_type(a.atttypid, a.atttypmod) AS data_type
FROM pg_attribute a
JOIN pg_class t ON a.attrelid = t.oid
JOIN pg_namespace n ON t.relnamespace = n.oid
WHERE n.nspname = 'troyka'
  AND t.relname = 'image_generation_history'
  AND a.attname IN ('model_type', 'queue_status')
  AND a.attnum > 0
  AND NOT a.attisdropped;

-- 1d) Если в 1c тип оказался enum (например ..._enum), посмотреть допустимые значения:
-- SELECT t.typname AS enum_type, e.enumlabel AS enum_value
-- FROM pg_type t
-- JOIN pg_enum e ON t.oid = e.enumtypid
-- JOIN pg_namespace n ON t.typnamespace = n.oid
-- WHERE n.nspname = 'troyka' AND t.typname LIKE '%model%'
-- ORDER BY t.typname, e.enumsortorder;

-- Шаг 2: Исправление deleted (если ещё не сделано).
-- ALTER TABLE troyka.image_generation_history ALTER COLUMN deleted SET DEFAULT false;

-- Шаг 3: Если в 1c) model_type оказался enum (например troyka.model_type_enum):
-- вставка падает из‑за значения 'seedream-4.5', которого нет в enum. Варианты:
-- A) Добавить значение в enum (подставьте имя типа из 1c):
--    ALTER TYPE troyka.model_type_enum ADD VALUE IF NOT EXISTS 'seedream-4.5';
-- B) Или перевести колонку на varchar (убрать привязку к enum):
--    ALTER TABLE troyka.image_generation_history ALTER COLUMN model_type TYPE varchar(64) USING model_type::text;
