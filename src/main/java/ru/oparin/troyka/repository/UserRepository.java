package ru.oparin.troyka.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.User;

public interface UserRepository extends ReactiveCrudRepository<User, Long> {

    Mono<User> findByUsername(String username);
    Mono<User> findByEmail(String email);
    Mono<User> findByTelegramId(Long telegramId);
    Mono<Boolean> existsByUsername(String username);
    Mono<Boolean> existsByEmail(String email);
    Mono<Boolean> existsByTelegramId(Long telegramId);
    Mono<Boolean> existsByUsernameOrEmail(String username, String email);
    Mono<Boolean> existsByUsernameOrEmailOrTelegramId(String username, String email, Long telegramId);
}