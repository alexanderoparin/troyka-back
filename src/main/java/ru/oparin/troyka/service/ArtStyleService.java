package ru.oparin.troyka.service;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.UserArtStyleDTO;
import ru.oparin.troyka.model.entity.ArtStyle;
import ru.oparin.troyka.model.entity.UserStyle;
import ru.oparin.troyka.repository.ArtStyleRepository;
import ru.oparin.troyka.repository.UserStyleRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для работы со стилями изображений.
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class ArtStyleService {

    private static final Long DEFAULT_STYLE_ID = 1L;
    private static final Long DEFAULT_USER_STYLE_ID = 2L;
    private static final String DEFAULT_STYLE_NAME = "Без стиля";
    private static final String DEFAULT_STYLE_PROMPT = "";
    private static final String CACHE_KEY_ALL_STYLES = "all-styles";

    private final ArtStyleRepository artStyleRepository;
    private final UserStyleRepository userStyleRepository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final Cache<String, List<ArtStyle>> artStylesCache;

    /**
     * Получить дефолтный styleId (1, Без стиля).
     *
     * @return дефолтный styleId (1, Без стиля)
     */
    public Long getDefaultStyleId() {
        return DEFAULT_STYLE_ID;
    }

    /**
     * Получить дефолтный styleId для нового пользователя.
     *
     * @return дефолтный styleId (2, Реалистичный)
     */
    public Long getDefaultUserStyleId() {
        return DEFAULT_USER_STYLE_ID;
    }

    /**
     * Получить все стили, отсортированные по идентификатору.
     * Использует кеш с TTL 30 минут.
     *
     * @return Flux со стилями
     */
    public Flux<ArtStyle> getAllStyles() {
        List<ArtStyle> cached = artStylesCache.getIfPresent(CACHE_KEY_ALL_STYLES);
        
        if (cached != null) {
            log.debug("Возвращаем стили из кеша");
            return Flux.fromIterable(cached);
        }

        // Загружаем из БД и кешируем
        log.debug("Загрузка стилей из БД и обновление кеша");
        return artStyleRepository.findAllByOrderById()
                .collectList()
                .doOnNext(styles -> {
                    artStylesCache.put(CACHE_KEY_ALL_STYLES, styles);
                    log.debug("Стили закешированы, кеш истечет через 30 минут");
                })
                .flatMapMany(Flux::fromIterable);
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
     * Получить сохраненный стиль пользователя с информацией о стиле.
     * Возвращает DTO с styleId и styleName.
     * Если стиль не сохранен, создает новую запись с дефолтным стилем (id = 2, Реалистичный).
     *
     * @param userId ID пользователя
     * @return Mono с DTO стиля пользователя
     */
    public Mono<UserArtStyleDTO> getUserStyleDTO(Long userId) {
        return getUserStyle(userId)
                .flatMap(userStyle -> {
                    // Если есть сохраненный styleId, получаем информацию о стиле
                    Long styleId = userStyle.getStyleId();
                    if (styleId != null) {
                        return getStyleById(styleId)
                                .map(this::toUserArtStyleDTO);
                    }
                    // Если styleId отсутствует, создаем новую запись с дефолтным стилем
                    return createDefaultUserStyle(userId);
                })
                .switchIfEmpty(createDefaultUserStyle(userId));
    }

    /**
     * Получить дефолтный стиль пользователя в виде DTO.
     *
     * @return Mono с DTO дефолтного стиля пользователя
     */
    public Mono<UserArtStyleDTO> getDefaultUserStyleDTO() {
        return getStyleById(DEFAULT_USER_STYLE_ID)
                .map(this::toUserArtStyleDTO);
    }

    /**
     * Создать запись пользователя с дефолтным стилем и вернуть DTO.
     *
     * @param userId ID пользователя
     * @return Mono с DTO стиля пользователя
     */
    private Mono<UserArtStyleDTO> createDefaultUserStyle(Long userId) {
        return saveOrUpdateUserStyleById(userId, DEFAULT_USER_STYLE_ID)
                .flatMap(saved -> getStyleById(DEFAULT_USER_STYLE_ID))
                .map(this::toUserArtStyleDTO);
    }

    /**
     * Преобразовать ArtStyle в UserArtStyleDTO.
     *
     * @param style стиль
     * @return DTO стиля пользователя
     */
    private UserArtStyleDTO toUserArtStyleDTO(ArtStyle style) {
        return UserArtStyleDTO.builder()
                .styleId(style.getId())
                .styleName(style.getName())
                .build();
    }

    /**
     * Сохранить или обновить стиль пользователя по styleId (upsert).
     *
     * @param userId ID пользователя
     * @param styleId идентификатор стиля
     * @return Mono с сохраненным стилем
     */
    public Mono<UserStyle> saveOrUpdateUserStyleById(Long userId, Long styleId) {
        // Пытаемся найти существующую запись
        return userStyleRepository.findByUserId(userId)
                .flatMap(existing -> {
                    Long existingStyleId = existing.getStyleId();
                    // Обновляем только если стиль изменился
                    if (existingStyleId == null || !existingStyleId.equals(styleId)) {
                        log.debug("Обновляем существующий стиль для userId={} с styleId={} на styleId={}", userId, existingStyleId, styleId);
                        return r2dbcEntityTemplate.update(UserStyle.class)
                                .matching(Query.query(Criteria.where("userId").is(userId)))
                                .apply(Update.update("styleId", styleId)
                                        .set("updatedAt", LocalDateTime.now()))
                                .then(userStyleRepository.findByUserId(userId));
                    }
                    return Mono.just(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Создаем новый стиль
                    log.debug("Создаем новый стиль для userId={}", userId);
                    UserStyle userStyle = UserStyle.builder()
                            .userId(userId)
                            .styleId(styleId)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return r2dbcEntityTemplate.insert(UserStyle.class)
                            .using(userStyle)
                            .thenReturn(userStyle);
                }));
    }

}

