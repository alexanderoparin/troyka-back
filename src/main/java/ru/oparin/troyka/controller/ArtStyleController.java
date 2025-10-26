package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.ArtStyleDTO;
import ru.oparin.troyka.repository.ArtStyleRepository;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/art-styles")
@Tag(name = "Стили изображений", description = "API для получения стилей изображений")
public class ArtStyleController {

    private final ArtStyleRepository artStyleRepository;

    @Operation(summary = "Получить все стили изображений",
            description = "Возвращает список всех доступных стилей для генерации изображений, отсортированных по имени")
    @GetMapping
    public Mono<ResponseEntity<List<ArtStyleDTO>>> getAllArtStyles() {
        log.info("Получен запрос на получение всех стилей изображений");
        return artStyleRepository.findAllByOrderByName()
                .map(ArtStyleDTO::fromEntity)
                .collectList()
                .map(artStyles -> {
                    log.info("Возвращено {} стилей изображений", artStyles.size());
                    return ResponseEntity.ok(artStyles);
                })
                .doOnError(error -> log.error("Ошибка при получении стилей изображений", error));
    }
}

