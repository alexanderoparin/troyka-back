package ru.oparin.troyka.model.dto.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO для ответа от Telegram Bot API.
 */
@Data
public class TelegramApiResponse {
    
    private Boolean ok;
    private TelegramMessage result;
    
    @JsonProperty("error_code")
    private Integer errorCode;
    
    private String description;
}
