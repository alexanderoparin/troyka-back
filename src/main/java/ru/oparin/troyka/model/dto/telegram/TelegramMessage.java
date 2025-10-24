package ru.oparin.troyka.model.dto.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DTO для сообщения от Telegram Bot API.
 */
@Data
public class TelegramMessage {
    
    @JsonProperty("message_id")
    private Long messageId;
    
    private TelegramUser from;
    private TelegramChat chat;
    private Long date;
    private String text;
    private String caption;
    private List<TelegramPhoto> photo;
    
    @JsonProperty("reply_to_message")
    private TelegramMessage replyToMessage;
}
