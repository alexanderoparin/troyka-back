package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.ImageGenerationHistoryDTO;
import ru.oparin.troyka.model.dto.UserInfoDTO;
import ru.oparin.troyka.service.FileService;
import ru.oparin.troyka.service.UserService;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
@Tag(name = "Пользователи", description = "API для работы с информацией о пользователях")
public class UserController {

    private final UserService userService;
    private final FileService fileService;

    @Operation(summary = "Получение информации о текущем пользователе",
            description = "Возвращает информацию о текущем авторизованном пользователе")
    @GetMapping("/me")
    public Mono<ResponseEntity<UserInfoDTO>> getCurrentUserInfo() {
        log.info("Получен запрос на получение информации о текущем пользователе");
        return userService.getCurrentUser()
                .doOnNext(userInfoDTO -> log.debug("Отправка информации о пользователе: {}", userInfoDTO))
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "Получение истории генерации изображений",
            description = "Возвращает историю генерации изображений текущего пользователя")
    @GetMapping("/me/image-history")
    public Flux<ImageGenerationHistoryDTO> getCurrentUserImageHistory() {
        log.info("Получен запрос на получение истории генерации изображений текущего пользователя");
        return userService.getCurrentUserImageHistory();
    }

    @PostMapping("/avatar/upload")
    public Mono<ResponseEntity<String>> uploadAvatar(@RequestPart("file") FilePart filePart) {
        return fileService.saveAvatar(filePart)
                .map(fileUrl -> ResponseEntity.ok("Аватар успешно загружен и сохранен: " + fileUrl))
                .onErrorResume(e -> {
                    log.error("Ошибка при загрузке аватара", e);
                    return Mono.just(ResponseEntity.badRequest().body("Ошибка при загрузке аватара: " + e.getMessage()));
                });
    }

    @GetMapping("/avatar")
    public Mono<ResponseEntity<String>> getUserAvatar(){
        return fileService.getCurrentUserAvatar()
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(e -> {
                    log.error("Ошибка при получении аватара пользователя", e);
                    return Mono.just(ResponseEntity.badRequest().body("Ошибка при получении аватара: " + e.getMessage()));
                });
    }

    @DeleteMapping("/avatar")
    public Mono<ResponseEntity<String>> deleteUserAvatar() {
        return fileService.deleteCurrentUserAvatar()
                .then(Mono.just(ResponseEntity.ok("Аватар успешно удален")))
                .onErrorResume(e -> {
                    log.error("Ошибка при удалении аватара пользователя", e);
                    return Mono.just(ResponseEntity.badRequest().body("Ошибка при удалении аватара: " + e.getMessage()));
                });
    }
}