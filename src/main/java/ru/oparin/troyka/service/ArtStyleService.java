package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.ArtStyle;
import ru.oparin.troyka.model.entity.UserStyle;
import ru.oparin.troyka.repository.ArtStyleRepository;
import ru.oparin.troyka.repository.UserStyleRepository;

import java.time.LocalDateTime;

/**
 * Сервис для работы со стилями изображений.
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class ArtStyleService {

    private final ArtStyleRepository artStyleRepository;
    private final UserStyleRepository userStyleRepository;

    /**
     * Получить все стили, отсортированные по имени.
     *
     * @return Flux со стилями
     */
    public Flux<ArtStyle> getAllStyles() {
        return artStyleRepository.findAllByOrderByName();
    }

    /**
     * Получить стиль по имени.
     *
     * @param name название стиля
     * @return Mono со стилем
     */
    public Mono<ArtStyle> getStyleByName(String name) {
        return artStyleRepository.findByName(name);
    }

    /**
     * Получить сохраненный стиль пользователя.
     *
     * @param userId ID пользователя
     * @return Mono со стилем пользователя
     */
    public Mono<UserStyle> getUserStyle(Long userId) {
        return userStyleRepository.findByUserId(userId);
    }

    /**
     * Сохранить или обновить стиль пользователя (upsert).
     *
     * @param userId ID пользователя
     * @param styleName название стиля
     * @return Mono с сохраненным стилем
     */
    public Mono<UserStyle> saveOrUpdateUserStyle(Long userId, String styleName) {
        return userStyleRepository.findByUserId(userId)
                .flatMap(existing -> {
                    // Обновляем существующий
                    existing.setStyleName(styleName);
                    existing.setUpdatedAt(LocalDateTime.now());
                    return userStyleRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Создаем новый
                    UserStyle userStyle = UserStyle.builder()
                            .userId(userId)
                            .styleName(styleName)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return userStyleRepository.save(userStyle);
                }));
    }
}

