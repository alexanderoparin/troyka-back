package ru.oparin.troyka.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.UserInfoDTO;
import ru.oparin.troyka.repository.UserRepository;
import ru.oparin.troyka.util.SecurityUtil;

@Service
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Mono<UserInfoDTO> getCurrentUser() {
        return SecurityUtil.getCurrentUsername()
                .flatMap(username -> {
                    log.info("Поиск пользователя по имени: {}", username);
                    return userRepository.findByUsername(username);
                })
                .map(user -> {
                    UserInfoDTO userInfoDTO = UserInfoDTO.fromUser(user);
                    log.info("Найден пользователь: {}", userInfoDTO);
                    return userInfoDTO;
                });
    }
}