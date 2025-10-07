package ru.oparin.troyka.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.exception.AuthException;
import ru.oparin.troyka.model.entity.User;

/**
 * Утилитный класс для проверки подтверждения email пользователя.
 * Содержит методы для валидации статуса подтверждения email.
 */
@Slf4j
public class EmailVerificationUtil {

    /**
     * Проверяет, подтвержден ли email у пользователя.
     * Выбрасывает исключение, если email не подтвержден.
     * 
     * @param user пользователь для проверки
     * @return Mono с пользователем, если email подтвержден
     * @throws AuthException если email не подтвержден
     */
    public static Mono<User> requireEmailVerified(User user) {
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            log.warn("Попытка доступа к защищенным функциям пользователем с неподтвержденным email: {}", user.getUsername());
            return Mono.error(new AuthException(
                    HttpStatus.FORBIDDEN,
                    "Для использования этой функции необходимо подтвердить email адрес. Проверьте почту и перейдите по ссылке в письме."
            ));
        }
        return Mono.just(user);
    }

    /**
     * Проверяет, подтвержден ли email у пользователя.
     * Возвращает true, если email подтвержден, false - если нет.
     * 
     * @param user пользователь для проверки
     * @return true, если email подтвержден, false - если нет
     */
    public static boolean isEmailVerified(User user) {
        return user.getEmailVerified() != null && user.getEmailVerified();
    }
}
