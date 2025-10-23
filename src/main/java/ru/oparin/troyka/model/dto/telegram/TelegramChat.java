package ru.oparin.troyka.model.dto.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO для чата Telegram.
 */
@Data
public class TelegramChat {
    
    private Long id;
    private String type;
    private String title;
    private String username;
    @JsonProperty("first_name")
    private String firstName;
    @JsonProperty("last_name")
    private String lastName;
}
