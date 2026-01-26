package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.UserPoints;
import ru.oparin.troyka.repository.UserPointsRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPointsService {
    
    private final UserPointsRepository userPointsRepository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    
    /**
     * Получить баланс пользователя
     */
    public Mono<Integer> getUserPoints(Long userId) {
        return userPointsRepository.findByUserId(userId)
                .map(UserPoints::getPoints)
                .doOnNext(balance -> log.info("Баланс пользователя c id {}: {} поинтов", userId, balance))
                .defaultIfEmpty(0);
    }
    
    /**
     * Добавить поинты пользователю
     */
    @Transactional
    public Mono<UserPoints> addPointsToUser(Long userId, Integer points) {
        log.info("Добавление {} поинтов пользователю с ID: {}", points, userId);
        return userPointsRepository.findByUserId(userId)
                .flatMap(existingUserPoints -> {
                    Integer currentPoints = existingUserPoints.getPoints() != null ? existingUserPoints.getPoints() : 0;
                    Integer newPoints = currentPoints + points;
                    return r2dbcEntityTemplate.update(UserPoints.class)
                            .matching(Query.query(Criteria.where("userId").is(userId)))
                            .apply(Update.update("points", newPoints)
                                    .set("updatedAt", LocalDateTime.now()))
                            .then(userPointsRepository.findByUserId(userId))
                            .doOnNext(updated -> log.info("Поинты пользователя с ID {} обновлены: {} -> {}", userId, currentPoints, newPoints));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    UserPoints newUserPoints = UserPoints.builder()
                            .userId(userId)
                            .points(points)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return r2dbcEntityTemplate.insert(UserPoints.class)
                            .using(newUserPoints)
                            .doOnNext(saved -> log.info("Запись поинтов для пользователя с ID {} создана с {} поинтами", userId, points));
                }))
                .doOnSuccess(userPoints -> log.info("Успешно добавлено {} поинтов пользователю с ID: {}", points, userId));
    }
    
    /**
     * Списать поинты у пользователя
     */
    @Transactional
    public Mono<UserPoints> deductPointsFromUser(Long userId, Integer points) {
        return userPointsRepository.findByUserId(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("У пользователя нет записи о поинтах")))
                .flatMap(userPoints -> {
                    Integer currentPoints = userPoints.getPoints() != null ? userPoints.getPoints() : 0;
                    if (currentPoints < points) {
                        return Mono.error(new IllegalArgumentException("Недостаточно поинтов у пользователя"));
                    }
                    
                    userPoints.setPoints(currentPoints - points);
                    userPoints.setUpdatedAt(LocalDateTime.now());
                    return userPointsRepository.save(userPoints);
                })
                .doOnSuccess(userPoints -> log.info("Успешно списано {} поинтов у пользователя с ID: {}", points, userId));
    }
    
    /**
     * Проверить, достаточно ли поинтов у пользователя
     */
    public Mono<Boolean> hasEnoughPoints(Long userId, Integer requiredPoints) {
        return getUserPoints(userId)
                .map(points -> points >= requiredPoints)
                .defaultIfEmpty(false);
    }
}