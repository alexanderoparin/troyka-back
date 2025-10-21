package ru.oparin.troyka.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Конфигурация для Telegram бота.
 * Настраивает и регистрирует бота в системе.
 */
@Configuration
@Slf4j
public class TelegramBotConfig {

    @Bean
    public TelegramBotsApi telegramBotsApi() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            log.info("TelegramBotsApi инициализирован");
            return botsApi;
        } catch (TelegramApiException e) {
            log.error("Ошибка инициализации TelegramBotsApi", e);
            throw new RuntimeException("Не удалось инициализировать TelegramBotsApi", e);
        }
    }

}
