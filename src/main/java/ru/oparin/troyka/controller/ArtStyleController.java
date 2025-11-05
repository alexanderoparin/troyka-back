package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.ArtStyleDTO;
import ru.oparin.troyka.model.dto.UserArtStyleDTO;
import ru.oparin.troyka.model.dto.auth.MessageResponse;
import ru.oparin.troyka.model.dto.auth.UpdateUserArtStyleRequest;
import ru.oparin.troyka.repository.ArtStyleRepository;
import ru.oparin.troyka.service.ArtStyleService;
import ru.oparin.troyka.service.UserService;
import ru.oparin.troyka.util.SecurityUtil;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/art-styles")
@Tag(name = "Стили изображений", description = "API для работы со стилями изображений")
public class ArtStyleController {

    private final ArtStyleRepository artStyleRepository;
    private final ArtStyleService artStyleService;
    private final UserService userService;

    @Operation(summary = "Получить все стили изображений",
            description = "Возвращает список всех доступных стилей для генерации изображений, отсортированных по идентификатору")
    @GetMapping
    public Mono<ResponseEntity<List<ArtStyleDTO>>> getAllArtStyles() {
        log.info("Получен запрос на получение всех стилей изображений");
        return artStyleRepository.findAllByOrderById()
                .map(ArtStyleDTO::fromEntity)
                .collectList()
                .map(artStyles -> {
                    log.info("Возвращено {} стилей изображений", artStyles.size());
                    return ResponseEntity.ok(artStyles);
                })
                .doOnError(error -> log.error("Ошибка при получении стилей изображений", error));
    }

    @Operation(summary = "Получение выбранного стиля пользователя",
            description = "Возвращает выбранный стиль генерации изображений для текущего пользователя")
    @GetMapping("/user")
    public Mono<ResponseEntity<UserArtStyleDTO>> getUserArtStyle() {
        return SecurityUtil.getCurrentUserId(userService)
                .flatMap(artStyleService::getUserStyleDTO)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Ошибка при получении стиля пользователя", e);
                    // В случае ошибки возвращаем дефолтный стиль
                    return artStyleService.getDefaultUserStyleDTO()
                            .map(ResponseEntity::ok);
                });
    }

    @Operation(summary = "Обновление выбранного стиля пользователя",
            description = "Сохраняет или обновляет выбранный стиль генерации изображений для текущего пользователя")
    @PutMapping("/user")
    public Mono<ResponseEntity<MessageResponse>> updateUserArtStyle(@Valid @RequestBody UpdateUserArtStyleRequest request) {
        return SecurityUtil.getCurrentUserId(userService)
                .flatMap(userId -> artStyleService.saveOrUpdateUserStyleById(userId, request.getStyleId()))
                .map(userStyle -> ResponseEntity.ok(new MessageResponse("Стиль успешно сохранен")))
                .onErrorResume(e -> {
                    log.error("Ошибка при сохранении стиля пользователя", e);
                    return Mono.just(ResponseEntity.badRequest().body(new MessageResponse("Ошибка при сохранении стиля: " + e.getMessage())));
                });
    }
}

