package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.exception.AuthException;
import ru.oparin.troyka.model.dto.auth.AuthResponse;
import ru.oparin.troyka.model.dto.auth.LoginRequest;
import ru.oparin.troyka.model.dto.auth.RegisterRequest;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.model.enums.Role;
import ru.oparin.troyka.util.PhoneUtil;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
@Slf4j
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserPointsService userPointsService;

    @Value("${jwt.expiration}")
    private long expiration;

    public Mono<AuthResponse> register(RegisterRequest request) {
        return userService.existsByUsernameOrEmail(request.getUsername(), request.getEmail())
                .then(Mono.defer(() -> {

                    User user = User.builder()
                            .username(request.getUsername())
                            .email(request.getEmail())
                            .password(passwordEncoder.encode(request.getPassword()))
                            .firstName(request.getFirstName())
                            .lastName(request.getLastName())
                            .phone(PhoneUtil.normalizePhone(request.getPhone()))
                            .role(Role.USER)
                            .build();

                    return userService.saveUser(user)
                            .flatMap(savedUser -> {
                                // Добавляем 6 бесплатных баллов пользователю (метод сам создаст запись если её нет)
                                return userPointsService.addPointsToUser(savedUser.getId(), 6)
                                        .then(Mono.fromCallable(() -> {
                                            String token = jwtService.generateToken(savedUser);
                                            log.info("Пользователь {} зарегистрирован с 6 бесплатными баллами", savedUser.getUsername());
                                            return new AuthResponse(
                                                    token,
                                                    savedUser.getUsername(),
                                                    savedUser.getEmail(),
                                                    savedUser.getRole().name(),
                                                    LocalDateTime.now().plusSeconds(expiration / 1000)
                                            );
                                        }));
                            });
                }));
    }

    public Mono<AuthResponse> login(LoginRequest request) {
        return userService.findByUsernameOrThrow(request.getUsername())
                .flatMap(user -> {
                    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        return Mono.error(new AuthException(
                                HttpStatus.UNAUTHORIZED,
                                "Неверный пароль"
                        ));
                    }

                    String token = jwtService.generateToken(user);
                    return Mono.just(new AuthResponse(
                            token,
                            user.getUsername(),
                            user.getEmail(),
                            user.getRole().name(),
                            LocalDateTime.now().plusSeconds(expiration / 1000)
                    ));
                });
    }
}