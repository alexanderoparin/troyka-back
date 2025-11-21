package ru.oparin.troyka.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис для ограничения частоты запросов (rate limiting) по IP адресу.
 * Используется для защиты от злоупотреблений при регистрации.
 */
@Slf4j
@Service
public class RateLimitingService {

    // Кэш для хранения счетчиков регистраций за час (ключ: IP, значение: количество)
    private final Cache<String, AtomicInteger> hourlyRegistrations = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(10_000)
            .build();

    // Кэш для хранения счетчиков регистраций за день (ключ: IP, значение: количество)
    private final Cache<String, AtomicInteger> dailyRegistrations = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofDays(1))
            .maximumSize(10_000)
            .build();

    // Кэш для хранения времени последней регистрации (для логирования)
    private final Cache<String, LocalDateTime> lastRegistrationTime = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofDays(1))
            .maximumSize(10_000)
            .build();

    private static final int MAX_REGISTRATIONS_PER_HOUR = 1;
    private static final int MAX_REGISTRATIONS_PER_DAY = 3;

    /**
     * Проверяет, является ли IP адрес localhost (для пропуска rate limiting при локальной разработке).
     */
    private boolean isLocalhost(String ipAddress) {
        return "127.0.0.1".equals(ipAddress) 
                || "0:0:0:0:0:0:0:1".equals(ipAddress)
                || "::1".equals(ipAddress)
                || "localhost".equalsIgnoreCase(ipAddress);
    }

    /**
     * Проверяет, можно ли зарегистрировать нового пользователя с данного IP адреса.
     * 
     * @param ipAddress IP адрес клиента
     * @return Mono<Boolean> - true если регистрация разрешена, false если превышен лимит
     */
    public Mono<Boolean> isRegistrationAllowed(String ipAddress) {
        return Mono.fromCallable(() -> {
            // Пропускаем rate limiting для localhost (локальная разработка/тестирование)
            if (isLocalhost(ipAddress)) {
                log.debug("Пропуск rate limiting для localhost IP: {}", ipAddress);
                return true;
            }

            // Проверяем лимит за час
            AtomicInteger hourlyCount = hourlyRegistrations.get(ipAddress, key -> new AtomicInteger(0));
            int currentHourlyCount = hourlyCount.get();
            
            if (currentHourlyCount >= MAX_REGISTRATIONS_PER_HOUR) {
                log.warn("Превышен лимит регистраций за час для IP {}: {}/{}", 
                        ipAddress, currentHourlyCount, MAX_REGISTRATIONS_PER_HOUR);
                return false;
            }

            // Проверяем лимит за день
            AtomicInteger dailyCount = dailyRegistrations.get(ipAddress, key -> new AtomicInteger(0));
            int currentDailyCount = dailyCount.get();
            
            if (currentDailyCount >= MAX_REGISTRATIONS_PER_DAY) {
                log.warn("Превышен лимит регистраций за день для IP {}: {}/{}", 
                        ipAddress, currentDailyCount, MAX_REGISTRATIONS_PER_DAY);
                return false;
            }

            return true;
        });
    }

    /**
     * Регистрирует попытку регистрации для данного IP адреса.
     * Увеличивает счетчики после успешной проверки.
     * 
     * @param ipAddress IP адрес клиента
     */
    public Mono<Void> recordRegistrationAttempt(String ipAddress) {
        return Mono.fromRunnable(() -> {
            // Не записываем попытки для localhost (локальная разработка/тестирование)
            if (isLocalhost(ipAddress)) {
                log.debug("Пропуск записи попытки регистрации для localhost IP: {}", ipAddress);
                return;
            }

            // Увеличиваем счетчик за час
            AtomicInteger hourlyCount = hourlyRegistrations.get(ipAddress, key -> new AtomicInteger(0));
            int newHourlyCount = hourlyCount.incrementAndGet();
            
            // Увеличиваем счетчик за день
            AtomicInteger dailyCount = dailyRegistrations.get(ipAddress, key -> new AtomicInteger(0));
            int newDailyCount = dailyCount.incrementAndGet();
            
            // Сохраняем время последней регистрации
            lastRegistrationTime.put(ipAddress, LocalDateTime.now());
            
            log.info("Зарегистрирована попытка регистрации для IP {}: {}/{} за час, {}/{} за день", 
                    ipAddress, newHourlyCount, MAX_REGISTRATIONS_PER_HOUR, newDailyCount, MAX_REGISTRATIONS_PER_DAY);
        });
    }

    /**
     * Получает информацию о текущих лимитах для IP адреса.
     * 
     * @param ipAddress IP адрес клиента
     * @return Mono с информацией о лимитах (для отладки/логирования)
     */
    public Mono<String> getRateLimitInfo(String ipAddress) {
        return Mono.fromCallable(() -> {
            AtomicInteger hourlyCount = hourlyRegistrations.get(ipAddress, key -> new AtomicInteger(0));
            AtomicInteger dailyCount = dailyRegistrations.get(ipAddress, key -> new AtomicInteger(0));
            LocalDateTime lastRegistration = lastRegistrationTime.getIfPresent(ipAddress);
            
            return String.format("IP: %s, За час: %d/%d, За день: %d/%d, Последняя регистрация: %s",
                    ipAddress, 
                    hourlyCount.get(), MAX_REGISTRATIONS_PER_HOUR,
                    dailyCount.get(), MAX_REGISTRATIONS_PER_DAY,
                    lastRegistration != null ? lastRegistration.toString() : "нет данных");
        });
    }
}

