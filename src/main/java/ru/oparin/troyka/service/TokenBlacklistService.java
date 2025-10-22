package ru.oparin.troyka.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для управления черным списком JWT токенов.
 * Позволяет инвалидировать токены при logout и проверять их валидность.
 */
@Service
@Slf4j
public class TokenBlacklistService {

    // Хранилище заблокированных токенов: token -> время блокировки
    private final ConcurrentHashMap<String, Instant> blacklistedTokens = new ConcurrentHashMap<>();

    /**
     * Добавляет токен в черный список.
     * 
     * @param token JWT токен для блокировки
     * @return Mono<Void> - результат операции
     */
    public Mono<Void> blacklistToken(String token) {
        return Mono.fromRunnable(() -> {
            blacklistedTokens.put(token, Instant.now());
        });
    }

    /**
     * Проверяет, находится ли токен в черном списке.
     * 
     * @param token JWT токен для проверки
     * @return true, если токен заблокирован
     */
    public boolean isTokenBlacklisted(String token) {
        return blacklistedTokens.containsKey(token);
    }

    /**
     * Очищает устаревшие токены из черного списка.
     * Токены старше 24 часов удаляются автоматически.
     * Выполняется каждые 30 минут.
     */
    @Scheduled(fixedRate = 30 * 60 * 1000) // 30 минут в миллисекундах
    public void cleanupExpiredTokens() {
        Instant cutoff = Instant.now().minusSeconds(24 * 60 * 60); // 24 часа назад
        
        int removedCount = 0;
        var iterator = blacklistedTokens.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isBefore(cutoff)) {
                iterator.remove();
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            log.info("Очищено {} устаревших токенов из черного списка", removedCount);
        }
    }
}
