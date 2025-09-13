package ru.oparin.solving.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solving.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
}
