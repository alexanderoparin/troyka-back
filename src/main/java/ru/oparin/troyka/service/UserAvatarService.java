package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.UserAvatar;
import ru.oparin.troyka.repository.UserAvatarRepository;

@Service
@RequiredArgsConstructor
public class UserAvatarService {

    private final UserAvatarRepository userAvatarRepository;

    public Mono<UserAvatar> saveUserAvatar(Long userId, String avatarUrl) {
        return userAvatarRepository.findByUserId(userId)
                .switchIfEmpty(Mono.fromCallable(() -> {
                    UserAvatar newUserAvatar = UserAvatar.builder()
                            .userId(userId)
                            .avatarUrl(avatarUrl)
                            .build();
                    userAvatarRepository.save(newUserAvatar);
                    return newUserAvatar;
                }))
                .flatMap(avatar -> {
                    avatar.setAvatarUrl(avatarUrl);
                    return userAvatarRepository.save(avatar);
                });
    }

    public Mono<UserAvatar> getUserAvatarByUserId(Long userId) {
        return userAvatarRepository.findByUserId(userId);
    }

    public Mono<Void> deleteUserAvatarByUserId(Long userId) {
        return userAvatarRepository.deleteByUserId(userId);
    }
}