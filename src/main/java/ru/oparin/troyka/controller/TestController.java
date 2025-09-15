package ru.oparin.troyka.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.oparin.troyka.service.UserService;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/test")
public class TestController {

    private final UserService userService;

    @GetMapping("/db/userCount")
    public String testConnection() {
        long count = userService.allUserCount();
        return "Подключение к базе данных успешно! Всего пользователей  в БД: " + count;
    }
}