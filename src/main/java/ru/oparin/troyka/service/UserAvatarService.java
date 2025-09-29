package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.UserAvatar;
import ru.oparin.troyka.repository.UserAvatarRepository;
import ru.oparin.troyka.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAvatarService {

    private final UserRepository userRepository;
    private final UserAvatarRepository userAvatarRepository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    public Mono<UserAvatar> saveUserAvatar(Long userId, String avatarUrl) {
        return userAvatarRepository.findByUserId(userId)
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        // Обновляем существующую запись
                        return userAvatarRepository.findByUserId(userId)
                                .flatMap(avatar -> {
                                    avatar.setAvatarUrl(avatarUrl);
                                    return userAvatarRepository.save(avatar);
                                });
                    } else {
                        // Создаем новую запись
                        UserAvatar newAvatar = UserAvatar.builder()
                                .userId(userId)
                                .avatarUrl(avatarUrl)
                                .build();
                        return userAvatarRepository.save(newAvatar);
                    }
                });
    }

    public Mono<UserAvatar> saveUserAvatar1(Long userId, String avatarUrl) {
        // Сначала пытаемся обновить существующую запись
        return r2dbcEntityTemplate.update(UserAvatar.class)
                .matching(Query.query(Criteria.where("userId").is(userId)))
                .apply(Update.update("avatarUrl", avatarUrl))
                .flatMap(rowsUpdated -> {
                    if (rowsUpdated > 0) {
                        log.info("URL аватара для пользователя с ID {} обновлен", userId);
                        return Mono.just(UserAvatar.builder()
                                .userId(userId)
                                .avatarUrl(avatarUrl)
                                .build());
                    } else {
                        // Если обновление не затронуло ни одной записи, создаем новую
                        log.info("Создание новой записи аватара для пользователя с ID: {}", userId);
                        UserAvatar newUserAvatar = UserAvatar.builder()
                                .userId(userId)
                                .avatarUrl(avatarUrl)
                                .build();
                        return r2dbcEntityTemplate.insert(UserAvatar.class)
                                .using(newUserAvatar)
                                .doOnNext(saved -> log.info("URL аватара для пользователя с ID {} создан", userId));
                    }
                });
    }

    public Mono<UserAvatar> getUserAvatarByUserId(Long userId) {
        return userAvatarRepository.findByUserId(userId);
    }

    public Mono<Void> deleteUserAvatarByUserId(Long userId) {
        return userAvatarRepository.deleteByUserId(userId);
    }
}