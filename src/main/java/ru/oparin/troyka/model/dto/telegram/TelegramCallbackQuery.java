package ru.oparin.troyka.model.dto.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO для callback query от Telegram Bot API.
 */
@Data
public class TelegramCallbackQuery {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("from")
    private TelegramUser from;
    
    @JsonProperty("message")
    private TelegramMessage message;
    
    @JsonProperty("data")
    private String data;
}

