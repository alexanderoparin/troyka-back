package ru.oparin.troyka.util;

import lombok.experimental.UtilityClass;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.User;

/**
 * Утилитный класс для работы с Spring Security.
 * Предоставляет методы для получения информации о текущем пользователе.
 */
@UtilityClass
public class SecurityUtil {

    /**
     * Получить логин текущего пользователя
     */
    public static Mono<String> getCurrentUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(authentication -> (String) authentication.getPrincipal());
    }

    /**
     * Получить ID текущего пользователя.
     * Этот метод требует инжекции UserService в контроллерах.
     * 
     * @param userService сервис для работы с пользователями
     * @return Mono с ID пользователя
     */
    public static Mono<Long> getCurrentUserId(ru.oparin.troyka.service.UserService userService) {
        return getCurrentUsername()
                .flatMap(userService::findByUsernameOrThrow)
                .map(User::getId);
    }
}