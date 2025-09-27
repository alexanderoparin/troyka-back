package ru.oparin.troyka.service;

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
import ru.oparin.troyka.repository.UserRepository;

import java.time.LocalDateTime;

@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserPointsService userPointsService;

    @Value("${jwt.expiration}")
    private long expiration;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       UserPointsService userPointsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userPointsService = userPointsService;
    }

    public Mono<AuthResponse> register(RegisterRequest request) {
        return userRepository.existsByUsername(request.getUsername())
                .flatMap(usernameExists -> {
                    if (usernameExists) {
                        return Mono.error(new AuthException(
                                HttpStatus.CONFLICT,
                                "Пользователь с таким именем уже существует"
                        ));
                    }
                    return userRepository.existsByEmail(request.getEmail());
                })
                .flatMap(emailExists -> {
                    if (emailExists) {
                        return Mono.error(new AuthException(
                                HttpStatus.CONFLICT,
                                "Пользователь с таким email уже существует"
                        ));
                    }

                    User user = User.builder()
                            .username(request.getUsername())
                            .email(request.getEmail())
                            .password(passwordEncoder.encode(request.getPassword()))
                            .firstName(request.getFirstName())
                            .lastName(request.getLastName())
                            .role(Role.USER)
                            .build();

                    return userRepository.save(user)
                            .flatMap(savedUser -> {
                                // Инициализируем баллы пользователя с 6 бесплатными баллами
                                return userPointsService.initializeUserPoints(savedUser.getId())
                                        .flatMap(userPoints -> userPointsService.addPointsToUser(savedUser.getId(), 6))
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
                });
    }

    public Mono<AuthResponse> login(LoginRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .switchIfEmpty(Mono.error(new AuthException(
                        HttpStatus.NOT_FOUND,
                        "Пользователь не найден"
                )))
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