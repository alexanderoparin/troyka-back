package ru.oparin.troyka.model.dto.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO для фото в Telegram.
 */
@Data
public class TelegramPhoto {
    
    @JsonProperty("file_id")
    private String fileId;
    
    @JsonProperty("file_unique_id")
    private String fileUniqueId;
    
    private Integer width;
    private Integer height;
    
    @JsonProperty("file_size")
    private Integer fileSize;
}
