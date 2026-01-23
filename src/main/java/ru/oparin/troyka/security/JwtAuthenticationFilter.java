package ru.oparin.troyka.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.exception.AuthException;
import ru.oparin.troyka.model.enums.Role;
import ru.oparin.troyka.repository.UserRepository;
import ru.oparin.troyka.service.JwtService;

import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = extractToken(exchange);

        if (token != null && jwtService.validateToken(token)) {
            String username = jwtService.getUsernameFromToken(token);

            return userRepository.findByUsername(username)
                    .flatMap(user -> {
                        // Проверяем, не заблокирован ли пользователь
                        if (user.getBlocked() != null && user.getBlocked()) {
                            return Mono.error(new AuthException(
                                    HttpStatus.FORBIDDEN,
                                    "Пользователь заблокирован"
                            ));
                        }
                        
                        List<GrantedAuthority> authorities = new ArrayList<>();
                        if (user.getRole() == Role.ADMIN) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                        }
                        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                        
                        return Mono.just(new UsernamePasswordAuthenticationToken(username, null, authorities));
                    })
                    .switchIfEmpty(Mono.just(new UsernamePasswordAuthenticationToken(username, null, new ArrayList<>())))
                    .flatMap(authentication -> {
                        SecurityContext securityContext = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                        securityContext.setAuthentication(authentication);

                        return chain.filter(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
                    });
        }

        return chain.filter(exchange);
    }

    private String extractToken(ServerWebExchange exchange) {
        String bearerToken = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}