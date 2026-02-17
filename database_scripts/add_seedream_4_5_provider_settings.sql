-- Добавить настройки провайдера для модели Seedream 4.5 (пока только FAL_AI).
INSERT INTO troyka.generation_provider_settings (id, model_type, active_provider, created_at, updated_at)
SELECT
  (SELECT COALESCE(MAX(id), 0) + 1 FROM troyka.generation_provider_settings),
  'SEEDREAM_4_5',
  'FAL_AI',
  NOW(),
  NOW()
WHERE NOT EXISTS (SELECT 1 FROM troyka.generation_provider_settings WHERE model_type = 'SEEDREAM_4_5');
