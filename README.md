# Troyka Backend

Backend-приложение на Java 21 с использованием Spring Boot 3.5.5 и Spring WebFlux для генерации изображений товаров с помощью ИИ.

## 🚀 Описание

Это backend-приложение предоставляет REST API для работы с различными функциями, включая:
- **Аутентификацию и авторизацию** пользователей с JWT токенами
- **Генерацию изображений** с помощью FAL AI (3 поинта за изображение)
- **Редактирование изображений** с помощью FAL AI
- **Систему поинтов** для оплаты генерации изображений
- **Интеграцию с Робокассой** для приема платежей
- **Загрузку и хранение файлов** (изображения, аватары)
- **Историю генераций** и платежей
- **Тарифные планы** для покупки поинтов

## 📡 API Эндпоинты

### 🔐 Аутентификация
- `POST /api/auth/register` - Регистрация нового пользователя (6 бесплатных поинтов)
- `POST /api/auth/login` - Вход в систему
- `POST /api/auth/forgot-password` - Запрос восстановления пароля
- `POST /api/auth/reset-password` - Сброс пароля по токену

### 👤 Пользователи
- `GET /api/users/me` - Информация о текущем пользователе
- `POST /api/users/avatar/upload` - Загрузка аватара
- `GET /api/users/avatar` - Получение аватара
- `DELETE /api/users/avatar` - Удаление аватара
- `GET /api/users/me/image-history` - История генераций пользователя

### 🎨 Генерация изображений
- `POST /api/fal/image/run/create` - Создание изображения (3 поинта за изображение)
- `GET /api/fal/user/points` - Баланс поинтов пользователя

### 💰 Платежи и поинты
- `POST /api/payment/create` - Создание платежа через Робокассу
- `GET /api/payment/history` - История платежей пользователя
- `GET /api/payment/result` - Callback от Робокассы (GET)

### 📁 Файлы
- `POST /api/files/upload` - Загрузка файлов (требуется аутентификация)
- `GET /api/files/{filename}` - Получение файлов (публичный доступ)

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

#### Логирование
- `log_level` - Уровень логирования (по умолчанию: info)
- `sql_log_level` - Уровень SQL логов (по умолчанию: info)
- `log_file_name` - Путь к файлу логов

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
- `first_name` - имя
- `last_name` - фамилия
- `phone` - телефон (опционально)
- `role` - роль пользователя (USER/ADMIN)
- `created_at` - дата создания
- `updated_at` - дата обновления

### 💰 Поинты пользователей (user_points)
- `user_id` - ссылка на пользователя (первичный ключ)
- `points` - количество поинтов пользователя
- `created_at` - дата создания записи
- `updated_at` - дата обновления записи

### 🎨 История генераций (image_generation_history)
- `id` - уникальный идентификатор
- `user_id` - ссылка на пользователя
- `image_url` - URL сгенерированного изображения
- `prompt` - текстовое описание
- `created_at` - дата создания

### 💳 Платежи (payment)
- `id` - уникальный идентификатор
- `user_id` - ссылка на пользователя
- `amount` - сумма платежа
- `description` - описание платежа
- `credits_amount` - количество поинтов
- `status` - статус платежа (PENDING/COMPLETED/FAILED)
- `robokassa_signature` - подпись Робокассы
- `robokassa_response` - ответ от Робокассы
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

## 🚀 Запуск приложения

### 🛠️ Локальная разработка

1. **Установите переменные окружения:**
```bash
export DB_HOST=localhost
export DB_USERNAME=your_username
export DB_PASSWORD=your_password
export JWT_SECRET=your_jwt_secret
export fal_ai_api_key=your_fal_ai_key
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
├── repository/      # Репозитории для работы с БД
├── model/           # Модели данных (entity, dto)
├── security/        # Конфигурация безопасности
├── exception/       # Обработка исключений
├── util/            # Утилиты
└── validation/      # Валидация данных
```

### 🔄 Основные сервисы
- **AuthService** - аутентификация и регистрация
- **UserService** - управление пользователями
- **UserPointsService** - система поинтов
- **FalAIService** - генерация изображений
- **PaymentService** - обработка платежей
- **RobokassaService** - интеграция с Робокассой
- **FileService** - работа с файлами
- **PricingService** - тарифные планы

## 📋 Последние изменения

### v1.2.0 - Интеграция с Робокассой
- ✅ Добавлена система поинтов для оплаты генерации
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