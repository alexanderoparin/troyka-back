package ru.oparin.troyka.util;

import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.exception.AuthException;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.model.enums.Role;
import ru.oparin.troyka.service.UserService;

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
    public static Mono<Long> getCurrentUserId(UserService userService) {
        return getCurrentUsername()
                .flatMap(userService::findByUsernameOrThrow)
                .map(User::getId);
    }

    /**
     * Получить текущего пользователя и проверить, что он является администратором.
     * 
     * @param userService сервис для работы с пользователями
     * @return Mono с пользователем-администратором
     * @throws AuthException если пользователь не найден или не является администратором
     */
    public static Mono<User> getCurrentAdmin(UserService userService) {
        return getCurrentUsername()
                .flatMap(userService::findByUsernameOrThrow)
                .flatMap(user -> {
                    if (user.getRole() != Role.ADMIN) {
                        return Mono.error(new AuthException(
                                HttpStatus.FORBIDDEN,
                                "Доступ запрещен. Требуется роль администратора."
                        ));
                    }
                    return Mono.just(user);
                });
    }
}