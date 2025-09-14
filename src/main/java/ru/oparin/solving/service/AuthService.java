package ru.oparin.solving.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.oparin.solving.model.dto.AuthResponse;
import ru.oparin.solving.model.dto.LoginRequest;
import ru.oparin.solving.model.dto.RegisterRequest;
import ru.oparin.solving.model.entity.User;
import ru.oparin.solving.model.enums.Role;
import ru.oparin.solving.repository.UserRepository;

import java.time.LocalDateTime;

@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setRole(Role.USER);

        User savedUser = userRepository.save(user);
        String token = jwtService.generateToken(savedUser);

        return new AuthResponse(
                token,
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getRole().name(),
                LocalDateTime.now().plusHours(24)
        );
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        String token = jwtService.generateToken(user);

        return new AuthResponse(
                token,
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                LocalDateTime.now().plusHours(24)
        );
    }
}