package com.asa.asaunify.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "login_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "login_at", nullable = false)
    private LocalDateTime loginAt;

    @Column(name = "logout_at")
    private LocalDateTime logoutAt;         // null until user logs out

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "browser")
    private String browser;

    @Column(name = "device")
    private String device;

    @Column(name = "login_status", nullable = false)
    private String loginStatus;             // "SUCCESS" or "FAILED"

    // Computed on logout — difference between loginAt and logoutAt
    @Column(name = "session_duration_minutes")
    private Long sessionDurationMinutes;
}