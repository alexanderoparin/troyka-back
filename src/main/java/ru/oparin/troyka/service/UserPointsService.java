package ru.oparin.troyka.service;

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
public class UserPointsService {
    
    private final UserPointsRepository userPointsRepository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    
    public UserPointsService(UserPointsRepository userPointsRepository, R2dbcEntityTemplate r2dbcEntityTemplate) {
        this.userPointsRepository = userPointsRepository;
        this.r2dbcEntityTemplate = r2dbcEntityTemplate;
    }
    
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
     * Добавить баллы пользователю
     */
    @Transactional
    public Mono<UserPoints> addPointsToUser(Long userId, Integer points) {
        log.info("Добавление {} баллов пользователю с ID: {}", points, userId);
        
        // Сначала пытаемся найти существующую запись
        return userPointsRepository.findByUserId(userId)
                .flatMap(existingUserPoints -> {
                    // Если запись существует, обновляем её
                    Integer currentPoints = existingUserPoints.getPoints() != null ? existingUserPoints.getPoints() : 0;
                    Integer newPoints = currentPoints + points;
                    
                    return r2dbcEntityTemplate.update(UserPoints.class)
                            .matching(Query.query(Criteria.where("userId").is(userId)))
                            .apply(Update.update("points", newPoints)
                                    .set("updatedAt", LocalDateTime.now()))
                            .then(userPointsRepository.findByUserId(userId))
                            .doOnNext(updated -> log.info("Баллы пользователя с ID {} обновлены: {} -> {}", userId, currentPoints, newPoints));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Если запись не существует, создаем новую
                    log.info("Создание новой записи баллов для пользователя с ID: {}", userId);
                    UserPoints newUserPoints = UserPoints.builder()
                            .userId(userId)
                            .points(points)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return r2dbcEntityTemplate.insert(UserPoints.class)
                            .using(newUserPoints)
                            .doOnNext(saved -> log.info("Запись баллов для пользователя с ID {} создана с {} баллами", userId, points));
                }))
                .doOnSuccess(userPoints -> log.info("Успешно добавлено {} баллов пользователю с ID: {}", points, userId));
    }
    
    /**
     * Списать баллы у пользователя
     */
    @Transactional
    public Mono<UserPoints> deductPointsFromUser(Long userId, Integer points) {
        log.info("Списание {} баллов у пользователя с ID: {}", points, userId);

        return userPointsRepository.findByUserId(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("У пользователя нет записи о баллах")))
                .flatMap(userPoints -> {
                    Integer currentPoints = userPoints.getPoints() != null ? userPoints.getPoints() : 0;
                    if (currentPoints < points) {
                        return Mono.error(new IllegalArgumentException("Недостаточно баллов у пользователя"));
                    }
                    
                    userPoints.setPoints(currentPoints - points);
                    userPoints.setUpdatedAt(LocalDateTime.now());
                    return userPointsRepository.save(userPoints);
                })
                .doOnSuccess(userPoints -> log.info("Успешно списано {} баллов у пользователя с ID: {}", points, userId));
    }
    
    /**
     * Проверить, достаточно ли баллов у пользователя
     */
    public Mono<Boolean> hasEnoughPoints(Long userId, Integer requiredPoints) {
        return getUserPoints(userId)
                .map(points -> points >= requiredPoints)
                .defaultIfEmpty(false);
    }
    
    /**
     * Инициализировать баллы пользователя (при регистрации)
     */
    @Transactional
    public Mono<UserPoints> initializeUserPoints(Long userId) {
        return userPointsRepository.existsByUserId(userId)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalStateException("У пользователя уже есть запись о баллах"));
                    } else {
                        UserPoints userPoints = UserPoints.builder()
                                .userId(userId)
                                .points(0)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        return userPointsRepository.save(userPoints);
                    }
                });
    }
}