package ru.oparin.troyka.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.Session;
import ru.oparin.troyka.model.entity.TelegramBotSession;
import ru.oparin.troyka.repository.SessionRepository;
import ru.oparin.troyka.repository.TelegramBotSessionRepository;
import ru.oparin.troyka.service.SessionService;


/**
 * Сервис для управления специальными сессиями Telegram бота.
 * Каждый пользователь имеет одну специальную сессию для всех генераций через бота.
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class TelegramBotSessionService {

    private final TelegramBotSessionRepository telegramBotSessionRepository;
    private final SessionRepository sessionRepository;
    private final SessionService sessionService;

    /**
     * Получить или создать специальную сессию для Telegram чата.
     * Если у пользователя нет специальной сессии, создается новая с названием "Telegram Bot Chat".
     *
     * @param userId ID пользователя
     * @param chatId ID чата в Telegram
     * @return специальная сессия для бота
     */
    public Mono<Session> getOrCreateTelegramBotSession(Long userId, Long chatId) {
        log.info("Получение или создание специальной сессии для пользователя {} и чата {}", userId, chatId);

        return telegramBotSessionRepository.findByUserId(userId)
                .flatMap(telegramBotSession -> {
                    // Обновляем chat_id если изменился
                    if (!telegramBotSession.getChatId().equals(chatId)) {
                        telegramBotSession.setChatId(chatId);
                        telegramBotSession.setUpdatedAt(java.time.LocalDateTime.now());
                        return telegramBotSessionRepository.save(telegramBotSession)
                                .then(sessionRepository.findById(telegramBotSession.getSessionId()));
                    }
                    return sessionRepository.findById(telegramBotSession.getSessionId());
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Создаем новую специальную сессию
                    log.info("Создание новой специальной сессии для пользователя {}", userId);
                    return sessionService.createSession(userId, "Telegram Bot Chat")
                            .flatMap(createResponse -> {
                                // Создаем запись в telegram_bot_session
                                TelegramBotSession telegramBotSession = TelegramBotSession.builder()
                                        .userId(userId)
                                        .sessionId(createResponse.getId())
                                        .chatId(chatId)
                                        .build();

                                return telegramBotSessionRepository.save(telegramBotSession)
                                        .then(sessionRepository.findById(createResponse.getId()));
                            });
                }))
                .doOnSuccess(session -> log.info("Получена специальная сессия {} для пользователя {}", session.getId(), userId))
                .doOnError(error -> log.error("Ошибка получения специальной сессии для пользователя {}", userId, error));
    }

    /**
     * Получить специальную сессию по ID пользователя.
     *
     * @param userId ID пользователя
     * @return специальная сессия или пустой результат
     */
    public Mono<Session> getTelegramBotSessionByUserId(Long userId) {
        return telegramBotSessionRepository.findByUserId(userId)
                .flatMap(telegramBotSession -> sessionRepository.findById(telegramBotSession.getSessionId()))
                .doOnSuccess(session -> log.info("Найдена специальная сессия {} для пользователя {}", session.getId(), userId))
                .doOnError(error -> log.error("Ошибка поиска специальной сессии для пользователя {}", userId, error));
    }

    /**
     * Получить специальную сессию по ID чата.
     *
     * @param chatId ID чата в Telegram
     * @return специальная сессия или пустой результат
     */
    public Mono<Session> getTelegramBotSessionByChatId(Long chatId) {
        return telegramBotSessionRepository.findByChatId(chatId)
                .flatMap(telegramBotSession -> sessionRepository.findById(telegramBotSession.getSessionId()))
                .doOnSuccess(session -> log.info("Найдена специальная сессия {} для чата {}", session.getId(), chatId))
                .doOnError(error -> log.error("Ошибка поиска специальной сессии для чата {}", chatId, error));
    }


    /**
     * Проверить существование специальной сессии для пользователя.
     *
     * @param userId ID пользователя
     * @return true если сессия существует, false иначе
     */
    public Mono<Boolean> existsTelegramBotSession(Long userId) {
        return telegramBotSessionRepository.existsByUserId(userId)
                .doOnSuccess(exists -> log.debug("Специальная сессия для пользователя {} существует: {}", userId, exists))
                .doOnError(error -> log.error("Ошибка проверки существования специальной сессии для пользователя {}", userId, error));
    }
}
