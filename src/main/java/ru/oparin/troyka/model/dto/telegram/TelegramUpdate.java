package ru.oparin.troyka.model.dto.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO для обновления от Telegram Bot API.
 */
@Data
public class TelegramUpdate {
    
    @JsonProperty("update_id")
    private Long updateId;
    
    private TelegramMessage message;
    
    @JsonProperty("edited_message")
    private TelegramMessage editedMessage;
    
    @JsonProperty("channel_post")
    private TelegramMessage channelPost;
    
    @JsonProperty("edited_channel_post")
    private TelegramMessage editedChannelPost;
}
