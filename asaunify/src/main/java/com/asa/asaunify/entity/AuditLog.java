package com.asa.asaunify.entity;

import com.asa.asaunify.enums.ActionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // When the action happened
    @Column(nullable = false)
    private LocalDateTime timestamp;

    // Who performed the action
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // What type of action was performed
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;

    // What kind of object was affected
    // e.g. "REQUEST", "MEMO", "USER"
    @Column(name = "object_type")
    private String objectType;

    // The ID of the affected object
    @Column(name = "object_id")
    private String objectId;

    // State before the action — stored as JSONB
    // e.g. { "status": "PENDING" }
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private Map<String, Object> oldValue;

    // State after the action — stored as JSONB
    // e.g. { "status": "APPROVED" }
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private Map<String, Object> newValue;

    // IP address of the user's session
    @Column(name = "ip_address")
    private String ipAddress;

    // Which module the action occurred in
    // e.g. "REQUESTS", "MEMOS", "USERS", "AUTH"
    @Column(nullable = false)
    private String module;

    @PrePersist
    protected void onCreate() {
        this.timestamp = LocalDateTime.now();
    }
}