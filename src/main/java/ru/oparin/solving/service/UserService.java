package ru.oparin.solving.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.oparin.solving.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public long allUserCount() {
        return userRepository.count();
    }
}
