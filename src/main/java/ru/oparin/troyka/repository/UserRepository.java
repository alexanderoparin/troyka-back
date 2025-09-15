package ru.oparin.troyka.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.troyka.model.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Boolean existsByUsername(String username);

    Boolean existsByEmail(String email);
}
