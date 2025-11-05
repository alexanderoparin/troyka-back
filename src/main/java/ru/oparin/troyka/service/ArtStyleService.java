package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
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

    private static final Long DEFAULT_STYLE_ID = 1L;
    private static final String DEFAULT_STYLE_NAME = "Без стиля";
    private static final String DEFAULT_STYLE_PROMPT = "";

    private final ArtStyleRepository artStyleRepository;
    private final UserStyleRepository userStyleRepository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    /**
     * Получить все стили, отсортированные по идентификатору.
     *
     * @return Flux со стилями
     */
    public Flux<ArtStyle> getAllStyles() {
        return artStyleRepository.findAllByOrderById();
    }

    /**
     * Получить стиль по идентификатору.
     * Если стиль не найден, возвращает дефолтный стиль "Без стиля" с id = 1.
     *
     * @param styleId идентификатор стиля
     * @return Mono со стилем
     */
    public Mono<ArtStyle> getStyleById(Long styleId) {
        if (styleId == null) {
            return Mono.just(getDefaultStyle());
        }
        return artStyleRepository.findById(styleId)
                .switchIfEmpty(Mono.just(getDefaultStyle()));
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
     * Получить дефолтный стиль "Без стиля".
     *
     * @return дефолтный стиль
     */
    private ArtStyle getDefaultStyle() {
        return ArtStyle.builder()
                .id(DEFAULT_STYLE_ID)
                .name(DEFAULT_STYLE_NAME)
                .prompt(DEFAULT_STYLE_PROMPT)
                .build();
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
        log.debug("Сохраняем или обновляем стиль для userId={}, styleName={}", userId, styleName);
        
        // Пытаемся найти существующую запись
        return userStyleRepository.findByUserId(userId)
                .flatMap(existing -> {
                    // Обновляем существующий стиль через r2dbcEntityTemplate
                    log.debug("Обновляем существующий стиль для userId={}", userId);
                    return r2dbcEntityTemplate.update(UserStyle.class)
                            .matching(Query.query(Criteria.where("userId").is(userId)))
                            .apply(Update.update("styleName", styleName)
                                    .set("updatedAt", LocalDateTime.now()))
                            .then(userStyleRepository.findByUserId(userId));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Создаем новый стиль
                    log.debug("Создаем новый стиль для userId={}", userId);
                    UserStyle userStyle = UserStyle.builder()
                            .userId(userId)
                            .styleName(styleName)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return r2dbcEntityTemplate.insert(UserStyle.class)
                            .using(userStyle)
                            .thenReturn(userStyle);
                }));
    }
}

