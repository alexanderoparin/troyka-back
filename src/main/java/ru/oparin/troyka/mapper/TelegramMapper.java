package ru.oparin.troyka.mapper;

import org.springframework.stereotype.Component;
import ru.oparin.troyka.model.dto.auth.TelegramAuthRequest;
import ru.oparin.troyka.model.entity.User;

/**
 * Компонент для преобразования Telegram данных.
 * Предоставляет методы для маппинга Telegram запросов в данные пользователя.
 */
@Component
public class TelegramMapper {

    /**
     * Обновляет данные пользователя из TelegramAuthRequest.
     * Обновляет только те поля, которые изменились.
     * 
     * @param user сущность пользователя
     * @param request данные от Telegram Login Widget
     * @return обновленная сущность пользователя
     */
    public User updateUserFromTelegramRequest(User user, TelegramAuthRequest request) {
        if (user == null || request == null) {
            return user;
        }
        
        // Обновляем telegramId, если изменился
        if (!request.getId().equals(user.getTelegramId())) {
            user.setTelegramId(request.getId());
        }
        
        // Обновляем username, если передан и изменился
        if (request.getUsername() != null && !request.getUsername().equals(user.getTelegramUsername())) {
            user.setTelegramUsername(request.getUsername());
        }
        
        // Обновляем firstName, если передан и изменился
        if (request.getFirst_name() != null && !request.getFirst_name().equals(user.getTelegramFirstName())) {
            user.setTelegramFirstName(request.getFirst_name());
        }
        
        // Обновляем photoUrl, если передан и изменился
        if (request.getPhoto_url() != null && !request.getPhoto_url().equals(user.getTelegramPhotoUrl())) {
            user.setTelegramPhotoUrl(request.getPhoto_url());
        }
        
        return user;
    }
}
