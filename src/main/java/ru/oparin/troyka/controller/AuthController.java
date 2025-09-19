package ru.oparin.troyka.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.AuthResponse;
import ru.oparin.troyka.model.dto.LoginRequest;
import ru.oparin.troyka.model.dto.RegisterRequest;
import ru.oparin.troyka.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return Mono.fromCallable(() -> authService.register(request))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(createErrorResponse(e))));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return Mono.fromCallable(() -> authService.login(request))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    if (e.getMessage().contains("not found") || e.getMessage().contains("Invalid password")) {
                        return Mono.just(ResponseEntity.status(401).body(createErrorResponse(e)));
                    }
                    return Mono.just(ResponseEntity.badRequest().body(createErrorResponse(e)));
                });
    }

    private AuthResponse createErrorResponse(Throwable e) {
        AuthResponse response = new AuthResponse();
        response.setToken(null);
        response.setUsername("error");
        response.setEmail("error");
        response.setRole("ERROR");
        response.setExpiresAt(java.time.LocalDateTime.now());
        // Можно добавить поле errorMessage в AuthResponse
        return response;
    }
}