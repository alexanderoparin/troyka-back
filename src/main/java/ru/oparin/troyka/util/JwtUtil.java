package ru.oparin.troyka.util;

import lombok.experimental.UtilityClass;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Утилита для работы с JWT токенами в WebFlux.
 */
@UtilityClass
public class JwtUtil {

    /**
     * Извлекает JWT токен из заголовка Authorization.
     * 
     * @param exchange ServerWebExchange для получения заголовков
     * @return Mono<String> - токен или пустой Mono если не найден
     */
    public static Mono<String> extractToken(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
            return null;
        });
    }
}
