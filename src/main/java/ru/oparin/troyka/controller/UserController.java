package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.ImageGenerationHistoryDTO;
import ru.oparin.troyka.model.dto.UserInfoDTO;
import ru.oparin.troyka.model.dto.auth.TelegramLinkRequest;
import ru.oparin.troyka.service.FileService;
import ru.oparin.troyka.service.UserService;
import ru.oparin.troyka.service.telegram.TelegramAuthService;
import ru.oparin.troyka.util.SecurityUtil;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
@Tag(name = "Пользователи", description = "API для работы с информацией о пользователях")
public class UserController {

    private final UserService userService;
    private final FileService fileService;
    private final TelegramAuthService telegramAuthService;

    @Operation(summary = "Получение информации о текущем пользователе",
            description = "Возвращает информацию о текущем авторизованном пользователе")
    @GetMapping("/me")
    public Mono<ResponseEntity<UserInfoDTO>> getCurrentUserInfo() {
        return userService.getCurrentUser()
                .doOnNext(userInfoDTO -> log.info("Получен запрос на получение информации о пользователе: {}", userInfoDTO.getUsername()))
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "Получение истории генерации изображений",
            description = "Возвращает историю генерации изображений текущего пользователя")
    @GetMapping("/me/image-history")
    public Flux<ImageGenerationHistoryDTO> getCurrentUserImageHistory() {
        return userService.getCurrentUser()
                .doOnNext(userInfoDTO -> log.info("Получен запрос на получение истории генерации изображений от пользователя: {}", userInfoDTO.getUsername()))
                .flatMapMany(userInfoDTO -> userService.getCurrentUserImageHistory());
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

    @Operation(summary = "Привязка Telegram к аккаунту",
            description = "Привязывает Telegram аккаунт к существующему пользователю")
    @PostMapping("/me/telegram/link")
    public Mono<ResponseEntity<String>> linkTelegram(@Valid @RequestBody TelegramLinkRequest request) {
        return SecurityUtil.getCurrentUsername()
                .flatMap(userService::findByUsernameOrThrow)
                .flatMap(user -> telegramAuthService.linkTelegramToExistingUser(request, user.getId()))
                .then(Mono.just(ResponseEntity.ok("Telegram успешно привязан к аккаунту")))
                .doOnNext(response -> log.info("Telegram привязан к аккаунту пользователя"))
                .onErrorResume(e -> {
                    log.error("Ошибка при привязке Telegram", e);
                    return Mono.just(ResponseEntity.badRequest().body("Ошибка при привязке Telegram: " + e.getMessage()));
                });
    }

    @Operation(summary = "Отвязка Telegram от аккаунта",
            description = "Отвязывает Telegram аккаунт от пользователя")
    @DeleteMapping("/me/telegram/unlink")
    public Mono<ResponseEntity<String>> unlinkTelegram() {
        return SecurityUtil.getCurrentUsername()
                .flatMap(userService::findByUsernameOrThrow)
                .flatMap(user -> telegramAuthService.unlinkTelegram(user.getId()))
                .then(Mono.just(ResponseEntity.ok("Telegram отвязан от аккаунта")))
                .doOnNext(response -> log.info("Telegram отвязан от аккаунта пользователя"))
                .onErrorResume(e -> {
                    log.error("Ошибка при отвязке Telegram", e);
                    return Mono.just(ResponseEntity.badRequest().body("Ошибка при отвязке Telegram: " + e.getMessage()));
                });
    }
}