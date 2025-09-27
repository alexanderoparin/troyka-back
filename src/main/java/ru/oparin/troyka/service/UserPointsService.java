package ru.oparin.troyka.service;

import lombok.extern.slf4j.Slf4j;
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
    
    public UserPointsService(UserPointsRepository userPointsRepository) {
        this.userPointsRepository = userPointsRepository;
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
        
        return userPointsRepository.findByUserId(userId)
                .switchIfEmpty(Mono.defer(() -> {
                    // Если запись о баллах пользователя не существует, создаем новую
                    UserPoints newUserPoints = UserPoints.builder()
                            .userId(userId)
                            .points(0)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return userPointsRepository.save(newUserPoints);
                }))
                .flatMap(userPoints -> {
                    Integer currentPoints = userPoints.getPoints() != null ? userPoints.getPoints() : 0;
                    userPoints.setPoints(currentPoints + points);
                    userPoints.setUpdatedAt(LocalDateTime.now());
                    return userPointsRepository.save(userPoints);
                })
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