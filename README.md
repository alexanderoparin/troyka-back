# Troyka Backend

Backend-приложение на Java 21 с использованием Spring Boot 3.5.5 и Spring WebFlux для генерации изображений товаров с помощью ИИ.

## 🚀 Описание

Это backend-приложение предоставляет REST API для работы с различными функциями, включая:
- **Аутентификацию и авторизацию** пользователей с JWT токенами
- **Генерацию изображений** с помощью FAL AI (2 поинта за изображение)
- **Редактирование изображений** с помощью FAL AI
- **Систему сессий** для организации генераций в диалогах
- **Систему поинтов** для оплаты генерации изображений
- **Интеграцию с Робокассой** для приема платежей
- **Интеграцию с Telegram Bot** для работы через Telegram
- **Загрузку и хранение файлов** (изображения, аватары)
- **Проксирование изображений** от FAL AI
- **Историю генераций** и платежей
- **Тарифные планы** для покупки поинтов
- **Стили генерации** из базы данных

## 📡 API Эндпоинты

### 🔐 Аутентификация
- `POST /api/auth/register` - Регистрация нового пользователя (4 бесплатных поинта)
- `POST /api/auth/login` - Вход в систему
- `POST /api/auth/telegram/login` - Вход через Telegram (регистрация при необходимости)
- `POST /api/auth/forgot-password` - Запрос восстановления пароля
- `POST /api/auth/reset-password` - Сброс пароля по токену
- `POST /api/auth/logout` - Выход из системы
- `GET /api/auth/verify-email` - Подтверждение email
- `POST /api/auth/resend-verification` - Повторная отправка письма подтверждения

### 👤 Пользователи
- `GET /api/users/me` - Информация о текущем пользователе
- `PUT /api/users/me/username` - Обновление имени пользователя
- `PUT /api/users/me/email` - Обновление email
- `POST /api/users/avatar/upload` - Загрузка аватара
- `GET /api/users/avatar` - Получение аватара
- `DELETE /api/users/avatar` - Удаление аватара
- `GET /api/users/me/image-history` - История генераций пользователя

### 📱 Telegram интеграция
- `POST /api/users/me/telegram/link` - Привязка Telegram к аккаунту
- `DELETE /api/users/me/telegram/unlink` - Отвязка Telegram от аккаунта
- `POST /api/telegram/bot/webhook` - Webhook для Telegram Bot (обработка команд)

### 📂 Сессии генерации
- `GET /api/sessions/default` - Получить или создать дефолтную сессию
- `GET /api/sessions` - Список сессий пользователя с пагинацией
- `POST /api/sessions` - Создание новой сессии
- `GET /api/sessions/{id}` - Детали сессии с историей сообщений
- `PUT /api/sessions/{id}/rename` - Переименование сессии
- `DELETE /api/sessions/{id}` - Удаление сессии и всей истории

### 🎨 Генерация изображений
- `POST /api/fal/image/run/create` - Создание изображения (2 поинта за изображение)
- `GET /api/fal/user/points` - Баланс поинтов пользователя
- При ошибках генерации поинты автоматически возвращаются пользователю

### 🖼️ Стили генерации
- `GET /api/art-styles` - Получение списка доступных стилей из БД

### 💰 Платежи и поинты
- `POST /api/payment/create` - Создание платежа через Робокассу
- `GET /api/payment/history` - История платежей пользователя
- `GET /api/payment/result` - Callback от Робокассы (GET)

### 📁 Файлы
- `POST /api/files/upload` - Загрузка файлов (требуется аутентификация)
- `GET /api/files/{filename}` - Получение файлов (публичный доступ)

### 🖼️ Проксирование изображений
- `GET /api/images/v1/{path}` - Проксирование изображений от FAL AI v3.fal.media
- `GET /api/images/v2/{path}` - Проксирование изображений от FAL AI v3b.fal.media

### 💳 Тарифы
- `GET /api/pricing/plans` - Активные тарифные планы
- `GET /api/pricing/plans/all` - Все тарифные планы (включая неактивные)

### 📞 Контакты
- `POST /api/contact/send` - Отправка сообщения в поддержку

### 🏥 Мониторинг
- `GET /api/health` - Проверка состояния приложения

## ⚙️ Настройка окружения

### 🔧 Переменные окружения

#### База данных
- `DB_HOST` - Хост PostgreSQL (по умолчанию: localhost)
- `DB_PORT` - Порт PostgreSQL (по умолчанию: 5432)
- `DB_NAME` - Имя базы данных (по умолчанию: postgres)
- `DB_SCHEMA` - Схема базы данных (по умолчанию: troyka)
- `DB_USERNAME` - Пользователь PostgreSQL
- `DB_PASSWORD` - Пароль PostgreSQL

#### JWT токены
- `JWT_SECRET` - Секрет для подписи JWT токенов
- `JWT_EXPIRATION` - Время жизни токена в мс (по умолчанию: 86400000)

#### FAL AI
- `fal_ai_api_key` - API ключ для FAL AI

#### Файлы
- `UPLOAD_DIR` - Директория для файлов (по умолчанию: /var/www/uploads/)
- `file.host` - Домен для доступа к файлам (24reshai.ru)

#### Email
- `SMTP_HOST` - SMTP сервер (по умолчанию: smtp.timeweb.ru)
- `SMTP_PORT` - SMTP порт (по умолчанию: 587)
- `SMTP_USERNAME` - Email пользователь
- `SMTP_PASSWORD` - Email пароль
- `EMAIL_FROM` - Отправитель (по умолчанию: noreply@24reshai.ru)
- `EMAIL_SUPPORT` - Поддержка (по умолчанию: support@24reshai.ru)

#### Робокасса
- `robokassa_pass_1` - Пароль 1 для Робокассы
- `robokassa_pass_2` - Пароль 2 для Робокассы
- `robokassa_is_test` - Тестовый режим (по умолчанию: true)

#### Telegram
- `TELEGRAM_BOT_TOKEN` - Токен Telegram бота
- `TELEGRAM_BOT_USERNAME` - Username бота (по умолчанию: reshai24_bot)
- `TELEGRAM_LOGIN_BOT_TOKEN` - Токен для Telegram Login Widget (по умолчанию: TELEGRAM_BOT_TOKEN)

#### Генерация
- `GENERATION_POINTS_PER_IMAGE` - Поинтов за одно изображение (по умолчанию: 2)
- `REGISTRATION_POINTS` - Поинтов при регистрации (по умолчанию: 4)

#### Логирование
- `log_level` - Уровень логирования (по умолчанию: info)
- `log_file_name` - Путь к файлу логов

#### Frontend
- `FRONTEND_URL` - URL фронтенда (по умолчанию: https://24reshai.ru)

### Подготовка директории для загрузки файлов

Перед запуском приложения необходимо создать директорию для хранения загруженных файлов:

```bash
# Запустите скрипт из корня проекта
chmod +x scripts/create-upload-dir.sh
sudo ./scripts/create-upload-dir.sh
```

Или вручную создайте директорию:
```bash
sudo mkdir -p /var/www/uploads
sudo chmod 755 /var/www/uploads
```

## 🗄️ Структура базы данных

### 👤 Пользователи (user)
- `id` - уникальный идентификатор
- `username` - имя пользователя (уникальное)
- `email` - email пользователя (уникальный)
- `password` - хэш пароля (bcrypt)
- `role` - роль пользователя (USER/ADMIN)
- `email_verified` - подтвержден ли email
- `telegram_id` - ID Telegram аккаунта (опционально)
- `telegram_username` - имя пользователя в Telegram (опционально)
- `telegram_first_name` - имя в Telegram (опционально)
- `telegram_photo_url` - URL фото профиля Telegram (опционально)
- `created_at` - дата создания
- `updated_at` - дата обновления

### 💰 Поинты пользователей (user_points)
- `user_id` - ссылка на пользователя (первичный ключ)
- `points` - количество поинтов пользователя
- `created_at` - дата создания записи
- `updated_at` - дата обновления записи

### 📂 Сессии (sessions)
- `id` - уникальный идентификатор сессии
- `user_id` - ссылка на пользователя
- `name` - название сессии
- `is_active` - флаг активности (true - активна, false - удалена)
- `created_at` - дата создания
- `updated_at` - дата последнего обновления

### 💬 Сообщения сессий (session_messages)
- `id` - уникальный идентификатор сообщения
- `session_id` - ссылка на сессию
- `user_id` - ссылка на пользователя
- `prompt` - текстовое описание (промпт)
- `image_urls` - массив URL сгенерированных изображений (JSON)
- `input_image_urls` - массив URL входных изображений для редактирования (JSON)
- `num_images` - количество сгенерированных изображений
- `output_format` - формат изображений (JPEG/PNG)
- `created_at` - дата создания

### 🎨 История генераций (image_generation_history)
- `id` - уникальный идентификатор
- `user_id` - ссылка на пользователя
- `session_id` - ссылка на сессию (опционально)
- `image_urls` - массив URL сгенерированных изображений (JSON)
- `input_image_urls` - массив URL входных изображений (JSON)
- `prompt` - текстовое описание
- `created_at` - дата создания

### 🖼️ Стили изображений (art_styles)
- `name` - название стиля (первичный ключ, например "Реалистичный", "Аниме")
- `prompt` - промпт для добавления к основному промпту пользователя

### 💳 Платежи (payment)
- `id` - уникальный идентификатор
- `user_id` - ссылка на пользователя
- `amount` - сумма платежа
- `description` - описание платежа
- `credits_amount` - количество поинтов
- `status` - статус платежа (PENDING/COMPLETED/FAILED)
- `robokassa_signature` - подпись Робокассы
- `robokassa_response` - ответ от Робокассы
- `is_test` - тестовый ли платеж
- `paid_at` - дата оплаты
- `created_at` - дата создания

### 💳 Тарифные планы (pricing_plan)
- `id` - уникальный идентификатор
- `name` - название плана
- `description` - описание плана
- `credits` - количество поинтов
- `price_rub` - цена в копейках
- `is_active` - активен ли план
- `is_popular` - популярный ли план
- `sort_order` - порядок сортировки
- `created_at` - дата создания
- `updated_at` - дата обновления

### 🔐 Токены подтверждения email (email_verification_token)
- `id` - уникальный идентификатор
- `user_id` - ссылка на пользователя
- `token` - токен подтверждения
- `expires_at` - дата истечения
- `created_at` - дата создания

### 🔐 Токены сброса пароля (password_reset_token)
- `id` - уникальный идентификатор
- `user_id` - ссылка на пользователя
- `token` - токен сброса пароля
- `expires_at` - дата истечения
- `created_at` - дата создания

### 🖼️ Аватары пользователей (user_avatar)
- `id` - уникальный идентификатор
- `user_id` - ссылка на пользователя
- `file_path` - путь к файлу аватара
- `file_size` - размер файла
- `mime_type` - MIME тип файла
- `created_at` - дата создания
- `updated_at` - дата обновления

### 🤖 Telegram Bot сессии (telegram_bot_session)
- `id` - уникальный идентификатор
- `user_id` - ссылка на пользователя
- `chat_id` - ID чата в Telegram
- `created_at` - дата создания
- `updated_at` - дата обновления

## 🚀 Запуск приложения

### 🛠️ Локальная разработка

1. **Установите переменные окружения:**
```bash
export DB_HOST=localhost
export DB_USERNAME=your_username
export DB_PASSWORD=your_password
export JWT_SECRET=your_jwt_secret
export fal_ai_api_key=your_fal_ai_key
export TELEGRAM_BOT_TOKEN=your_telegram_bot_token
```

2. **Запустите приложение:**
```bash
mvn spring-boot:run
```

### 📦 Сборка и продакшен

1. **Соберите проект:**
```bash
mvn clean package -DskipTests
```

2. **Запустите JAR-файл:**
```bash
java -jar target/troyka-back-*.jar
```

### 🐳 Docker

```bash
# Сборка образа
docker build -t troyka-backend .

# Запуск контейнера
docker run -p 8080:8080 \
  -e DB_HOST=your_db_host \
  -e DB_USERNAME=your_username \
  -e DB_PASSWORD=your_password \
  -e JWT_SECRET=your_jwt_secret \
  -e fal_ai_api_key=your_fal_ai_key \
  -e TELEGRAM_BOT_TOKEN=your_telegram_bot_token \
  troyka-backend
```

## 📚 Технологии

- **Java 21** - язык программирования
- **Spring Boot 3.5.5** - основной фреймворк
- **Spring WebFlux** - реактивный веб-фреймворк
- **Spring Security** - аутентификация и авторизация
- **R2DBC** - реактивный доступ к базе данных
- **PostgreSQL** - база данных
- **JWT** - токены аутентификации
- **Maven** - управление зависимостями
- **Swagger/OpenAPI** - документация API
- **Telegram Bot API** - интеграция с Telegram

## 📖 Документация API

После запуска приложения документация доступна по адресам:
- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI spec:** `http://localhost:8080/v3/api-docs`
- **ReDoc:** `http://localhost:8080/swagger-ui/index.html`

## 🔧 Архитектура

### 🏗️ Структура проекта
```
src/main/java/ru/oparin/troyka/
├── config/          # Конфигурация приложения
├── controller/      # REST контроллеры
├── service/         # Бизнес-логика
│   └── telegram/    # Telegram Bot сервисы
├── repository/      # Репозитории для работы с БД
├── model/           # Модели данных (entity, dto)
├── security/         # Конфигурация безопасности
├── exception/       # Обработка исключений
├── util/            # Утилиты
└── validation/       # Валидация данных
```

### 🔄 Основные сервисы
- **AuthService** - аутентификация и регистрация
- **UserService** - управление пользователями
- **UserPointsService** - система поинтов
- **FalAIService** - генерация изображений (с возвратом поинтов при ошибках)
- **SessionService** - управление сессиями генерации
- **ImageGenerationHistoryService** - история генераций
- **PaymentService** - обработка платежей
- **RobokassaService** - интеграция с Робокассой
- **FileService** - работа с файлами
- **ImageProxyService** - проксирование изображений от Fal.ai
- **PricingService** - тарифные планы
- **ArtStyleService** - управление стилями генерации
- **TelegramBotService** - обработка команд Telegram бота
- **TelegramAuthService** - авторизация через Telegram
- **EmailVerificationService** - подтверждение email

## 🔄 Бизнес-логика

### Генерация изображений
1. Проверка достаточности поинтов
2. Создание/получение сессии
3. Списание поинтов
4. Вызов Fal.ai API
5. При ошибке - автоматический возврат поинтов
6. Сохранение истории в сессии и общую историю
7. Обновление времени сессии

### Возврат поинтов
- Поинты возвращаются автоматически при любых ошибках генерации
- Защита от двойного возврата через единую обработку ошибок

### Проксирование изображений
- Проксирование изображений от Fal.ai через `/api/images/v1/` и `/api/images/v2/`
- Необходимо для корректной работы download атрибута и CORS

## 📋 Последние изменения

### v1.5.0 - Система сессий и стили из БД
- ✅ Система сессий для организации генераций
- ✅ История диалогов в каждой сессии
- ✅ API для управления сессиями (создание, переименование, удаление)
- ✅ Загрузка стилей генерации из БД
- ✅ Опция "Без стиля" для генерации
- ✅ Автоматический возврат поинтов при ошибках генерации
- ✅ Проксирование изображений от Fal.ai
- ✅ Улучшенная обработка ошибок с детальными сообщениями

### v1.4.0 - Telegram Bot интеграция
- ✅ Полноценный Telegram Bot для работы с сайтом
- ✅ Обработка команд через webhook
- ✅ Сессии для Telegram бота

### v1.3.0 - Telegram интеграция и улучшения
- ✅ Интеграция с Telegram Login Widget для быстрого входа
- ✅ Привязка/отвязка Telegram аккаунта в личном кабинете
- ✅ Обновление профиля пользователя (имя, email)
- ✅ Пометки для тестовых платежей в истории
- ✅ Улучшенная обработка JSON/текстовых ответов API
- ✅ Финализация платежей через Робокассу

### v1.2.0 - Интеграция с Робокассой
- ✅ Добавлена система поинтов для оплаты генерации (2 поинта за изображение)
- ✅ Интеграция с Робокассой для приема платежей
- ✅ Callback обработка от Робокассы (GET)
- ✅ История платежей пользователя
- ✅ Тарифные планы с различными количествами поинтов
- ✅ Восстановление пароля через email

### v1.1.0 - UI/UX улучшения
- ✅ Загрузка и управление аватарами пользователей
- ✅ Улучшенная обработка ошибок
- ✅ Валидация данных

### v1.0.0 - Базовая функциональность
- ✅ Генерация изображений через FAL AI
- ✅ Система аутентификации с JWT
- ✅ История генераций
- ✅ Загрузка файлов
- ✅ REST API с документацией

## 📝 Лицензия

Private
