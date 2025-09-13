package ru.oparin.solving.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.oparin.solving.repository.UserRepository;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/test")
public class TestController {

    private final UserRepository userRepository;

    @GetMapping("/db/userCount")
    public String testConnection() {
        long count = userRepository.count();
        return "Database connection successful! Users count: " + count;
    }
}