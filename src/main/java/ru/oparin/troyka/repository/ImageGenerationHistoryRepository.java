package ru.oparin.troyka.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import ru.oparin.troyka.model.entity.ImageGenerationHistory;

public interface ImageGenerationHistoryRepository extends ReactiveCrudRepository<ImageGenerationHistory, Long> {

    Flux<ImageGenerationHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}