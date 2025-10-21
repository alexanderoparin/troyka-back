package ru.oparin.troyka.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.TelegramBotSession;

/**
 * Репозиторий для работы с сессиями Telegram бота.
 * Предоставляет методы для поиска и управления связями пользователей с их Telegram сессиями.
 */
public interface TelegramBotSessionRepository extends ReactiveCrudRepository<TelegramBotSession, Long> {

    /**
     * Найти сессию бота по ID пользователя.
     *
     * @param userId ID пользователя
     * @return сессия бота или пустой результат
     */
    Mono<TelegramBotSession> findByUserId(Long userId);

    /**
     * Найти сессию бота по ID чата в Telegram.
     *
     * @param chatId ID чата в Telegram
     * @return сессия бота или пустой результат
     */
    Mono<TelegramBotSession> findByChatId(Long chatId);

    /**
     * Проверить существование сессии бота для пользователя.
     *
     * @param userId ID пользователя
     * @return true если сессия существует, false иначе
     */
    Mono<Boolean> existsByUserId(Long userId);

    /**
     * Проверить существование сессии бота для чата.
     *
     * @param chatId ID чата в Telegram
     * @return true если сессия существует, false иначе
     */
    Mono<Boolean> existsByChatId(Long chatId);

    /**
     * Удалить сессию бота по ID пользователя.
     *
     * @param userId ID пользователя
     * @return количество удаленных записей
     */
    Mono<Long> deleteByUserId(Long userId);
}

