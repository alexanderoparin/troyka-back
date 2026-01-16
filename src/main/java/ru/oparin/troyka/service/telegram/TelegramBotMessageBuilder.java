package ru.oparin.troyka.service.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.troyka.config.properties.GenerationProperties;
import ru.oparin.troyka.model.dto.pricing.PricingPlanResponse;
import ru.oparin.troyka.model.entity.ArtStyle;

import java.util.List;

/**
 * Класс для построения текстовых сообщений для Telegram бота.
 */
@Component
@RequiredArgsConstructor
public class TelegramBotMessageBuilder {

    private static final String PRICING_URL = "https://24reshai.ru/pricing";
    private static final String SUPPORT_URL = "https://24reshai.ru/contacts";
    private static final String SITE_URL = "https://24reshai.ru";
    private static final String DEFAULT_STYLE_NAME = "none";

    private final GenerationProperties generationProperties;

    public String buildHelpMessage() {
        return String.format("""
                🤖 *Справка по боту 24reshai*
                
                📝 *Основные команды:*
                • /start - Начать работу с ботом
                • /help - Показать эту справку
                • /balance - Проверить баланс поинтов
                • /buy - Пополнить баланс
                
                🎨 *Генерация изображений:*
                • Отправьте текстовое описание
                • Или приложите фото с подписью
                • Каждая генерация стоит %s поинтов
                • Результат готов за 5-10 секунд
                
                💡 *Советы:*
                • Чем подробнее описание, тем лучше результат
                • Используйте качественные референсы
                
                🌐 *Сайт:* %s
                """, generationProperties.getPointsPerImage(), SITE_URL);
    }

    public String buildWelcomeMessage(String username) {
        return String.format("""
                👋 *Добро пожаловать обратно, %s!*
                
                🎨 Ваш аккаунт уже привязан к Telegram.
                Вы можете генерировать изображения прямо здесь!
                
                📝 *Как использовать:*
                • Отправьте текстовое описание
                • Приложите фото + описание
                
                💰 *Стоимость:* %s поинта за 1 изображение
                • Используйте /help для справки
                """, username, generationProperties.getPointsPerImage());
    }

    public String buildBalanceMessage(Integer points) {
        return String.format("""
                💰 *Ваш баланс поинтов*
                
                🔢 *Текущий баланс:* %d поинтов
                🎨 *Доступно генераций:* %d
                
                💳 *Пополнить баланс:* %s
                """, points, points / generationProperties.getPointsPerImage(), PRICING_URL);
    }

    public String buildInsufficientPointsMessage(Integer points) {
        return String.format("""
                ❌ *Недостаточно поинтов*
                
                💰 *Текущий баланс:* %s поинтов
                🎨 *Требуется:* %s поинтов для генерации
                
                💳 *Пополнить баланс:* %s
                """, points, generationProperties.getPointsPerImage(), PRICING_URL);
    }

    public String buildUserNotFoundMessage() {
        return "❌ Пользователь не найден. Используйте /start для регистрации.";
    }

    public String buildPromptUpdatedMessage(String prompt) {
        return String.format("""
                ✅ *Промпт обновлен!*
                
                📝 *Новый промпт:* %s
                """, prompt);
    }

    public String buildImageGeneratedCaption(String displayPrompt) {
        return String.format("""
                🎨 *Изображение сгенерировано!*
                
                📝 *Промпт:* %s
                💰 *Стоимость:* %s поинта
                
                🔄 *Хотите еще?* Отправьте новое описание для генерации!
                
                ✏️ *Редактировать изображение?* Ответьте на это сообщение с новым промптом
                """, displayPrompt, generationProperties.getPointsPerImage());
    }

    public String buildGenerationErrorMessage() {
        return """
                ❌ *Ошибка генерации*
                Произошла ошибка при создании изображения. Попробуйте еще раз.""";
    }

    public String buildErrorMessage() {
        return String.format("""
                ❌ *Произошла ошибка*
                
                Попробуйте еще раз или обратитесь в поддержку: %s
                """, SUPPORT_URL);
    }

    public String buildPhotoErrorMessage() {
        return "❌ *Ошибка загрузки фото*\n\nНе удалось обработать изображение. Попробуйте еще раз.";
    }

    public String buildUnknownMessageTypeMessage() {
        return """
                🤔 *Не понимаю*
                
                Отправьте текстовое описание для генерации изображения или фото с подписью.
                """;
    }

    public String buildOldMessageReplyMessage() {
        return "❌ *Нельзя ответить на старое сообщение*\n\nОтвечайте только на последнее сгенерированное изображение.";
    }

    public String buildEmptyPromptMessage() {
        return "❌ *Пустой запрос*\n\nОтправьте текстовое описание для изменения изображения.";
    }

    public String buildUnknownCommandMessage(String command) {
        return String.format("""
                ❓ *Неизвестная команда*
                
                🤖 *Команда:* %s
                📋 *Доступные команды:*
                • /start - Начать работу с ботом
                • /balance - Баланс поинтов
                • /buy - Пополнить баланс
                • /help - Справка
                
                💡 *Или просто отправьте описание изображения для генерации!*
                """, command);
    }

    public String buildStyleSelectionMessage(String styleName) {
        return String.format("""
                💡 *Текущий стиль:* %s
                
                🎨 *Выберите действие:*
                """, styleName);
    }

    public String buildStyleSelectionKeyboard(Long sessionId, Long userId) {
        return String.format("""
                {
                    "inline_keyboard": [
                        [{"text": "💡 Улучшить промпт с помощью ИИ", "callback_data": "enhance_prompt:%d:%d"}],
                        [{"text": "✏️ Редактировать промпт", "callback_data": "edit_prompt:%d:%d"}],
                        [{"text": "🎨 Генерировать с текущим стилем", "callback_data": "generate_current:%d:%d:1"}],
                        [{"text": "🔄 Сменить стиль", "callback_data": "change_style:%d:%d:1"}]
                    ]
                }
                """, sessionId, userId, sessionId, userId, sessionId, userId, sessionId, userId);
    }

    public String buildStyleListMessage(String prompt, List<String> inputImageUrls, List<ArtStyle> allStyles) {
        StringBuilder styleList = new StringBuilder();
        styleList.append("🎨 *Выберите стиль для генерации:*\n\n");
        styleList.append("📝 *Промпт:* ").append(prompt).append("\n\n");
        if (!inputImageUrls.isEmpty()) {
            styleList.append("🖼️ *Референс:* загружен\n\n");
        }
        styleList.append("💡 *Введите номер стиля:*\n\n");

        int index = 1;
        for (ArtStyle style : allStyles) {
            String emoji = style.getName().equals(DEFAULT_STYLE_NAME) ? "⚪" : "🎨";
            styleList.append(index).append(". ").append(emoji).append(" ").append(style.getName()).append("\n");
            index++;
        }
        styleList.append("\nПример: отправьте *1* для выбора без стиля");
        return styleList.toString();
    }

    public String buildGenerationStartMessage(String prompt, String styleDisplay) {
        return String.format("""
                🎨 *Генерация изображения*
                
                📝 *Промпт:* %s
                
                🎨 *Стиль:* %s
                
                ⏱️ *Ожидайте 5-10 секунд*
                """, prompt, styleDisplay);
    }

    public String buildEditPromptMessage() {
        return """
                ✏️ *Редактирование промпта*
                
                📝 Отправьте новый текст промпта для замены текущего.
                
                💡 Вы можете скопировать и скорректировать улучшенный промпт или написать свой.
                """;
    }

    /**
     * Построить сообщение со списком тарифных планов для покупки.
     */
    public String buildPricingPlansMessage(List<PricingPlanResponse> plans) {
        StringBuilder message = new StringBuilder();
        message.append("💰 *Пополнить баланс*\n\n");
        message.append("Выберите тарифный план:\n\n");

        for (PricingPlanResponse plan : plans) {
            String emoji = Boolean.TRUE.equals(plan.getIsPopular()) ? "🔥" : "💎";
            double priceRub = plan.getPriceRub() != null ? plan.getPriceRub() / 100.0 : 0;
            int credits = plan.getCredits() != null ? plan.getCredits() : 0;
            int generations = credits / generationProperties.getPointsPerImage();
            
            message.append(emoji).append(" *").append(plan.getName()).append("*\n");
            if (plan.getDescription() != null && !plan.getDescription().isEmpty()) {
                message.append("   ").append(plan.getDescription()).append("\n");
            }
            message.append("   💰 ").append(String.format("%.2f", priceRub)).append(" ₽\n");
            message.append("   🎨 ").append(credits).append(" поинтов (").append(generations).append(" генераций)\n\n");
        }

        message.append("🌐 *Или перейдите на сайт:* ").append(PRICING_URL);
        return message.toString();
    }

    /**
     * Построить JSON inline-клавиатуру для выбора тарифного плана.
     */
    public String buildPricingPlansKeyboard(List<PricingPlanResponse> plans) {
        StringBuilder keyboard = new StringBuilder();
        keyboard.append("{\n");
        keyboard.append("    \"inline_keyboard\": [\n");

        for (int i = 0; i < plans.size(); i++) {
            PricingPlanResponse plan = plans.get(i);
            String emoji = Boolean.TRUE.equals(plan.getIsPopular()) ? "🔥" : "💎";
            double priceRub = plan.getPriceRub() != null ? plan.getPriceRub() / 100.0 : 0;
            String buttonText = String.format("%s %s - %.0f₽", emoji, plan.getName(), priceRub);
            
            keyboard.append("        [{\"text\": \"").append(buttonText).append("\", \"callback_data\": \"buy_plan:").append(plan.getId()).append("\"}]");
            
            if (i < plans.size() - 1) {
                keyboard.append(",");
            }
            keyboard.append("\n");
        }

        keyboard.append("    ]\n");
        keyboard.append("}");
        return keyboard.toString();
    }

    /**
     * Построить сообщение с информацией об оплате (без URL, так как он в кнопке).
     */
    public String buildPaymentUrlMessage(String planName, Double amount, Integer credits) {
        int generations = credits / generationProperties.getPointsPerImage();
        return String.format("""
                💳 *Оплата тарифа*
                
                📦 *Тариф:* %s
                💰 *Сумма:* %.2f ₽
                🎨 *Поинтов:* %d (%d генераций)
                
                👆 *Нажмите кнопку ниже для оплаты*
                """, planName, amount, credits, generations);
    }

    /**
     * Построить JSON inline-клавиатуру с кнопкой оплаты.
     */
    public String buildPaymentUrlKeyboard(String paymentUrl) {
        return String.format("""
                {
                    "inline_keyboard": [
                        [{"text": "💳 Оплатить", "url": "%s"}],
                        [{"text": "🔙 Назад к тарифам", "callback_data": "back_to_pricing"}]
                    ]
                }
                """, paymentUrl);
    }

    /**
     * Построить сообщение о балансе с кнопкой пополнения (если баланс низкий).
     */
    public String buildBalanceMessageWithTopUp(Integer points) {
        int availableGenerations = points / generationProperties.getPointsPerImage();
        StringBuilder message = new StringBuilder();
        message.append("💰 *Ваш баланс поинтов*\n\n");
        message.append("🔢 *Текущий баланс:* ").append(points).append(" поинтов\n");
        message.append("🎨 *Доступно генераций:* ").append(availableGenerations).append("\n");
        
        if (points < 10) {
            message.append("\n⚠️ *Баланс низкий!* Пополните для продолжения работы.");
        }
        
        return message.toString();
    }

    /**
     * Построить JSON inline-клавиатуру для баланса с кнопкой пополнения (если баланс низкий).
     */
    public String buildBalanceKeyboard(Integer points) {
        if (points < 10) {
            return """
                    {
                        "inline_keyboard": [
                            [{"text": "💳 Пополнить баланс", "callback_data": "show_pricing"}]
                        ]
                    }
                    """;
        }
        return "{\"inline_keyboard\": []}";
    }

    /**
     * Построить сообщение о недостатке поинтов с предложением пополнить.
     */
    public String buildInsufficientPointsMessageWithTopUp(Integer points) {
        return String.format("""
                ❌ *Недостаточно поинтов*
                
                💰 *Текущий баланс:* %d поинтов
                🎨 *Требуется:* %d поинтов для генерации
                
                💳 *Пополните баланс для продолжения работы*
                """, points, generationProperties.getPointsPerImage());
    }

    /**
     * Построить JSON inline-клавиатуру для сообщения о недостатке поинтов.
     */
    public String buildInsufficientPointsKeyboard() {
        return """
                {
                    "inline_keyboard": [
                        [{"text": "💳 Пополнить баланс", "callback_data": "show_pricing"}]
                    ]
                }
                """;
    }

    /**
     * Построить сообщение об успешной оплате.
     */
    public String buildPaymentSuccessMessage(Integer credits, Integer newBalance) {
        int generations = credits / generationProperties.getPointsPerImage();
        return String.format("""
                ✅ *Оплата успешна!*
                
                🎨 *Начислено:* %d поинтов (%d генераций)
                💰 *Новый баланс:* %d поинтов
                
                🎉 *Можете продолжать генерацию изображений!*
                """, credits, generations, newBalance);
    }
}

