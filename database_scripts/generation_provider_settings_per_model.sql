-- Переход на хранение активного провайдера для каждой модели генерации.
-- Было: одна запись (id=1) с полем active_provider.
-- Стало: одна запись на модель (model_type = NANO_BANANA, NANO_BANANA_PRO, ...).

-- 1) Добавить колонку model_type
ALTER TABLE troyka.generation_provider_settings
  ADD COLUMN IF NOT EXISTS model_type VARCHAR(64);

-- 2) Заполнить существующую запись
UPDATE troyka.generation_provider_settings
  SET model_type = 'NANO_BANANA'
  WHERE id = 1;

-- 3) Сделать model_type обязательным и уникальным
ALTER TABLE troyka.generation_provider_settings
  ALTER COLUMN model_type SET NOT NULL;

ALTER TABLE troyka.generation_provider_settings
  ADD CONSTRAINT uq_generation_provider_settings_model_type UNIQUE (model_type);

-- 4) Вставить запись для NANO_BANANA_PRO (активный провайдер LAOZHANG_AI)
-- id задаём явно (MAX+1), т.к. в таблице может не быть sequence / автоинкремент
INSERT INTO troyka.generation_provider_settings (id, model_type, active_provider, created_at, updated_at)
SELECT
  (SELECT COALESCE(MAX(id), 0) + 1 FROM troyka.generation_provider_settings),
  'NANO_BANANA_PRO',
  'LAOZHANG_AI',
  (SELECT created_at FROM troyka.generation_provider_settings WHERE id = 1 LIMIT 1),
  NOW()
WHERE NOT EXISTS (SELECT 1 FROM troyka.generation_provider_settings WHERE model_type = 'NANO_BANANA_PRO');
