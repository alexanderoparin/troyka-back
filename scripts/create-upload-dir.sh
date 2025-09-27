#!/bin/bash

# Создаем директорию для загрузки файлов
UPLOAD_DIR=${UPLOAD_DIR:-/var/www/uploads}

echo "Создаем директорию для загрузки файлов: $UPLOAD_DIR"

# Создаем директорию, если она не существует
mkdir -p "$UPLOAD_DIR"

# Устанавливаем права доступа
chmod 755 "$UPLOAD_DIR"

# Назначаем владельца (если запускается от root)
if [ "$EUID" -eq 0 ]; then
  chown -R www-data:www-data "$UPLOAD_DIR" 2>/dev/null || echo "Не удалось назначить владельца www-data"
fi

echo "Директория $UPLOAD_DIR создана и готова к использованию"