package ru.oparin.troyka.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.UserStyle;

/**
 * Repository для работы с выбранными стилями пользователей.
 */
public interface UserStyleRepository extends ReactiveCrudRepository<UserStyle, Long> {

    /**
     * Находит выбранный стиль для пользователя по его ID.
     * @param userId ID пользователя
     * @return Mono с пользовательским стилем или пустой Mono если стиль не найден
     */
    Mono<UserStyle> findByUserId(Long userId);
}

