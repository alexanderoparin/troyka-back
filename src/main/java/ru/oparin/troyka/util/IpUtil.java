package ru.oparin.troyka.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Утилита для работы с IP адресами в WebFlux.
 */
@Slf4j
@UtilityClass
public class IpUtil {

    /**
     * Извлекает IP адрес клиента из запроса.
     * Учитывает заголовки X-Forwarded-For и X-Real-IP для работы за прокси.
     * 
     * @param exchange ServerWebExchange для получения информации о запросе
     * @return Mono<String> - IP адрес клиента, или ошибка если IP не удалось определить
     */
    public static Mono<String> extractClientIp(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            // Проверяем заголовок X-Forwarded-For (первый IP в списке - это реальный клиент)
            String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isEmpty()) {
                // X-Forwarded-For может содержать несколько IP через запятую
                String firstIp = forwardedFor.split(",")[0].trim();
                if (!firstIp.isEmpty()) {
                    return firstIp;
                }
            }

            // Проверяем заголовок X-Real-IP
            String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
            if (realIp != null && !realIp.isEmpty()) {
                return realIp;
            }

            // Используем remote address из запроса
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                return remoteAddress.getAddress().getHostAddress();
            }

            // Если IP не найден - это проблема конфигурации или необычная ситуация
            // Логируем предупреждение и выбрасываем ошибку, чтобы это было заметно
            log.error("Не удалось определить IP адрес клиента. URI: {}, Headers: {}", 
                    exchange.getRequest().getURI(), exchange.getRequest().getHeaders());
            throw new IllegalStateException("Не удалось определить IP адрес клиента. Проверьте конфигурацию прокси/nginx.");
        });
    }
}

