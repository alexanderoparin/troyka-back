package ru.oparin.troyka.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.UserPoints;

@Repository
public interface UserPointsRepository extends ReactiveCrudRepository<UserPoints, Long> {
    Mono<UserPoints> findByUserId(Long userId);
    Mono<Boolean> existsByUserId(Long userId);
    Mono<Void> deleteByUserId(Long userId);
}