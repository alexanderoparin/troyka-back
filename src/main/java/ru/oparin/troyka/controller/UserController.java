package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.UserInfoDTO;
import ru.oparin.troyka.service.UserService;

@Slf4j
@RestController
@RequestMapping("/users")
@Tag(name = "Пользователи", description = "API для работы с информацией о пользователях")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Получение информации о текущем пользователе",
            description = "Возвращает информацию о текущем авторизованном пользователе")
    @GetMapping("/me")
    public Mono<ResponseEntity<UserInfoDTO>> getCurrentUserInfo() {
        log.info("Получен запрос на получение информации о текущем пользователе");
        return userService.getCurrentUser()
                .doOnNext(userInfoDTO -> log.info("Отправка информации о пользователе: {}", userInfoDTO))
                .map(ResponseEntity::ok);
    }
}