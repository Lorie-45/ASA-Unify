package com.asa.asaunify.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single password-reset request.
 *
 * The code itself is never stored — only a BCrypt hash of it — so a database
 * dump does not hand out working reset codes. The attempt counter lives on
 * this row rather than in memory so it survives a restart and cannot be
 * cleared by an attacker retrying against a different instance.
 */
@Entity
@Table(
        name = "password_reset_tokens",
        indexes = {
                @Index(name = "idx_prt_user", columnList = "user_id"),
                @Index(name = "idx_prt_expires_at", columnList = "expires_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // BCrypt hash of the 6-digit code. A fast hash (SHA-256) would be useless
    // here: 6 digits is only ~20 bits, so a leaked table would be reversed
    // instantly. BCrypt's work factor makes that impractical within the
    // token's short lifetime.
    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Incremented on every failed verification; the token dies once this
    // reaches the configured maximum.
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    // Set when the token is used for a successful reset, or when it is
    // superseded by a newer request. Non-null means permanently unusable.
    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }
}
