package ru.oparin.troyka.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.EmailVerificationToken;

import java.time.LocalDateTime;

@Repository
public interface EmailVerificationTokenRepository extends ReactiveCrudRepository<EmailVerificationToken, Long> {
    
    @Query("SELECT * FROM email_verification_token WHERE token = :token")
    Mono<EmailVerificationToken> findByToken(String token);
    
    @Query("DELETE FROM email_verification_token WHERE expires_at < :now")
    Mono<Long> deleteByExpiresAtBefore(LocalDateTime now);
}
