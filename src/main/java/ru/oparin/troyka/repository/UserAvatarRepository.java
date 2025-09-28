package ru.oparin.troyka.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.UserAvatar;

@Repository
public interface UserAvatarRepository extends R2dbcRepository<UserAvatar, Long> {
    Mono<UserAvatar> findByUserId(Long userId);
    Mono<Void> deleteByUserId(Long userId);
}