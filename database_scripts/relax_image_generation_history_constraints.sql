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

-- Шаг 2: Исправление.
-- Колонка deleted имеет NOT NULL, но в INSERT при постановке в очередь она не передаётся.
-- Задаём значение по умолчанию — тогда вставка без deleted будет подставлять false.
ALTER TABLE troyka.image_generation_history
  ALTER COLUMN deleted SET DEFAULT false;
