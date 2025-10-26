package ru.oparin.troyka.model.dto.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO для файла Telegram.
 */
@Data
public class TelegramFile {
    @JsonProperty("file_id")
    private String fileId;
    
    @JsonProperty("file_unique_id")
    private String fileUniqueId;
    
    @JsonProperty("file_size")
    private Integer fileSize;
    
    @JsonProperty("file_path")
    private String filePath;
}

