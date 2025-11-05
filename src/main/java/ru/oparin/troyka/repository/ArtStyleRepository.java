package ru.oparin.troyka.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.ArtStyle;

public interface ArtStyleRepository extends ReactiveCrudRepository<ArtStyle, Long> {

    /**
     * Находит все стили, отсортированные по имени.
     * @return Flux стилей
     */
    Flux<ArtStyle> findAllByOrderByName();

    /**
     * Находит стиль по имени.
     * @param name название стиля
     * @return Mono стиля
     */
    Mono<ArtStyle> findByName(String name);

    /**
     * Находит стиль по идентификатору.
     * @param id идентификатор стиля
     * @return Mono стиля
     */
    Mono<ArtStyle> findById(Long id);
}

