package ru.oparin.troyka.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.Session;
import ru.oparin.troyka.model.entity.TelegramBotSession;
import ru.oparin.troyka.repository.SessionRepository;
import ru.oparin.troyka.repository.TelegramBotSessionRepository;
import ru.oparin.troyka.service.SessionService;

import java.time.LocalDateTime;


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
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

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
                        log.info("Обновление chat_id для пользователя {}: {} -> {}", userId, telegramBotSession.getChatId(), chatId);
                        return r2dbcEntityTemplate.update(TelegramBotSession.class)
                                .matching(Query.query(Criteria.where("userId").is(userId)))
                                .apply(Update.update("chatId", chatId)
                                        .set("updatedAt", LocalDateTime.now()))
                                .then(sessionRepository.findById(telegramBotSession.getSessionId()));
                    }
                    return sessionRepository.findById(telegramBotSession.getSessionId());
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Создаем новую специальную сессию
                    log.info("Создание новой специальной сессии для пользователя {} и чата {}", userId, chatId);
                    return sessionService.createSession(userId, "Telegram Bot Chat")
                            .flatMap(createResponse -> {
                                // Создаем запись в telegram_bot_session используя upsert подход
                                TelegramBotSession telegramBotSession = TelegramBotSession.builder()
                                        .userId(userId)
                                        .sessionId(createResponse.getId())
                                        .chatId(chatId)
                                        .createdAt(LocalDateTime.now())
                                        .updatedAt(LocalDateTime.now())
                                        .build();

                                return r2dbcEntityTemplate.insert(TelegramBotSession.class)
                                        .using(telegramBotSession)
                                        .then(sessionRepository.findById(createResponse.getId()))
                                        .doOnNext(session -> log.info("Специальная сессия {} создана для пользователя {} и чата {}", 
                                                createResponse.getId(), userId, chatId));
                            });
                }));
    }

    /**
     * Получить специальную сессию по ID пользователя.
     *
     * @param userId ID пользователя
     * @return специальная сессия или пустой результат
     */
    public Mono<Session> getTelegramBotSessionByUserId(Long userId) {
        return telegramBotSessionRepository.findByUserId(userId)
                .flatMap(telegramBotSession -> sessionRepository.findById(telegramBotSession.getSessionId()));
    }

    /**
     * Получить TelegramBotSession по ID пользователя.
     *
     * @param userId ID пользователя
     * @return TelegramBotSession или пустой результат
     */
    public Mono<TelegramBotSession> getTelegramBotSessionEntityByUserId(Long userId) {
        return telegramBotSessionRepository.findByUserId(userId);
    }

    /**
     * Получить специальную сессию по ID чата.
     *
     * @param chatId ID чата в Telegram
     * @return специальная сессия или пустой результат
     */
    public Mono<Session> getTelegramBotSessionByChatId(Long chatId) {
        return telegramBotSessionRepository.findByChatId(chatId)
                .flatMap(telegramBotSession -> sessionRepository.findById(telegramBotSession.getSessionId()));
    }


    /**
     * Проверить существование специальной сессии для пользователя.
     *
     * @param userId ID пользователя
     * @return true если сессия существует, false иначе
     */
    public Mono<Boolean> existsTelegramBotSession(Long userId) {
        return telegramBotSessionRepository.existsByUserId(userId);
    }
}
