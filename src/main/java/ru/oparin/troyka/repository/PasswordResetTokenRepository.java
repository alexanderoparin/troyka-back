package ru.oparin.troyka.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.PasswordResetToken;

import java.time.LocalDateTime;

@Repository
public interface PasswordResetTokenRepository extends ReactiveCrudRepository<PasswordResetToken, Long> {
    
    @Query("SELECT * FROM troyka.password_reset_tokens WHERE token = :token AND used = false AND expires_at > :now")
    Mono<PasswordResetToken> findByTokenAndNotUsedAndNotExpired(String token, LocalDateTime now);
    
    @Query("SELECT * FROM troyka.password_reset_tokens WHERE user_id = :userId AND used = false AND expires_at > :now ORDER BY created_at DESC LIMIT 1")
    Mono<PasswordResetToken> findActiveTokenByUserId(Long userId, LocalDateTime now);
    
    @Query("UPDATE troyka.password_reset_tokens SET used = true WHERE token = :token")
    Mono<Void> markTokenAsUsed(String token);
    
    @Query("DELETE FROM troyka.password_reset_tokens WHERE expires_at < :now")
    Mono<Void> deleteExpiredTokens(LocalDateTime now);
}

