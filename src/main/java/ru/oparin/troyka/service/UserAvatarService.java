package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.UserAvatar;
import ru.oparin.troyka.repository.UserAvatarRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAvatarService {

    private final UserAvatarRepository userAvatarRepository;

    public Mono<UserAvatar> saveUserAvatar(Long userId, String avatarUrl) {
        return userAvatarRepository.findByUserId(userId)
                .flatMap(existingAvatar -> {
                    log.info("Обновление URL аватара для пользователя с ID: {}", userId);
                    existingAvatar.setAvatarUrl(avatarUrl);
                    return userAvatarRepository.save(existingAvatar);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Сохранение URL аватара для пользователя с ID: {}", userId);
                    UserAvatar newUserAvatar = UserAvatar.builder()
                            .userId(userId)
                            .avatarUrl(avatarUrl)
                            .build();
                    Mono<UserAvatar> saved = userAvatarRepository.save(newUserAvatar);
                    log.info("URL аватара для пользователя с ID {} сохранен", userId);
                    return saved;
                }));
    }

    public Mono<UserAvatar> getUserAvatarByUserId(Long userId) {
        return userAvatarRepository.findByUserId(userId);
    }

    public Mono<Void> deleteUserAvatarByUserId(Long userId) {
        return userAvatarRepository.deleteByUserId(userId);
    }
}