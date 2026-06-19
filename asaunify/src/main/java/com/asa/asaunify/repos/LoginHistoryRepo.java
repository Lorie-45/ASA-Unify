package com.asa.asaunify.repos;


import com.asa.asaunify.entity.LoginHistory;
import com.asa.asaunify.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoginHistoryRepo extends JpaRepository<LoginHistory, UUID> {

    List<LoginHistory> findByUserOrderByLoginAtDesc(User user);

    List<LoginHistory> findByLoginAtBetweenOrderByLoginAtDesc(
            LocalDateTime from,
            LocalDateTime to
    );

    List<LoginHistory> findByUserAndLoginAtBetweenOrderByLoginAtDesc(
            User user,
            LocalDateTime from,
            LocalDateTime to
    );

    Optional<LoginHistory> findTopByUserAndLogoutAtIsNullOrderByLoginAtDesc(User user);

    @Query("SELECT COUNT(l) FROM LoginHistory l WHERE l.user = :user " +
            "AND l.loginStatus = 'FAILED' AND l.loginAt >= :since")
    long countFailedAttempts(User user, LocalDateTime since);
}