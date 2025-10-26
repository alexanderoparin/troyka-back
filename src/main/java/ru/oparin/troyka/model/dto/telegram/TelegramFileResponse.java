package ru.oparin.troyka.model.dto.telegram;

import lombok.Data;

/**
 * DTO для ответа от Telegram getFile API.
 */
@Data
public class TelegramFileResponse {
    private boolean ok;
    private String description;
    private TelegramFile result;
}

