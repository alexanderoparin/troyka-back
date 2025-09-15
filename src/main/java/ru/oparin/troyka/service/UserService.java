package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.oparin.troyka.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public long allUserCount() {
        return userRepository.count();
    }
}
