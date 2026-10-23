package com.asa.asaunify.repos;

import com.asa.asaunify.entity.PasswordResetToken;
import com.asa.asaunify.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepo
        extends JpaRepository<PasswordResetToken, UUID> {

    // The one live token for a user. Issuing a new request consumes any
    // previous ones, so at most one row can come back unconsumed.
    Optional<PasswordResetToken> findFirstByUserAndConsumedAtIsNullOrderByCreatedAtDesc(
            User user);

    // Supersede every outstanding request for this user. Called before a new
    // code is issued so an older code can never be used afterwards.
    // flushAutomatically so pending changes (the token just consumed, the new
    // password hash) hit the database before this bulk update runs. No
    // clearAutomatically: clearing would detach the User we still hand to the
    // audit log a few lines later.
    @Modifying(flushAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.consumedAt = :now "
            + "WHERE t.user = :user AND t.consumedAt IS NULL")
    int consumeAllActiveForUser(
            @Param("user") User user,
            @Param("now") LocalDateTime now);

    // Per-account request throttle: how many requests this account has made
    // inside the current window, whatever IP they came from.
    long countByUserAndCreatedAtAfter(User user, LocalDateTime since);

    // Housekeeping — expired rows carry no useful information.
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
