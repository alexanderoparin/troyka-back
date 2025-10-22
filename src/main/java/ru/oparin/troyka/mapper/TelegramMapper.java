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
     * 
     * @param user сущность пользователя
     * @param request данные от Telegram Login Widget
     * @return обновленная сущность пользователя
     */
    public User updateUserFromTelegramRequest(User user, TelegramAuthRequest request) {
        if (user == null || request == null) {
            return user;
        }
        
        user.setTelegramId(request.getId());
        user.setTelegramUsername(request.getUsername());
        user.setTelegramFirstName(request.getFirst_name());
        user.setTelegramPhotoUrl(request.getPhoto_url());
        
        if (user.getTelegramNotificationsEnabled() == null) {
            user.setTelegramNotificationsEnabled(true);
        }
        
        return user;
    }
}
