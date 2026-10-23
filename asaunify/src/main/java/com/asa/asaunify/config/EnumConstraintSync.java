package com.asa.asaunify.config;

import com.asa.asaunify.enums.ActionType;
import com.asa.asaunify.enums.RequestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Keeps the database CHECK constraints on enum columns in step with the Java
 * enums.
 *
 * Hibernate generates a CHECK constraint for every @Enumerated(STRING) column,
 * but ddl-auto=update never alters an existing one. Adding a value to an enum
 * (CANCELLED, PASSWORD_RESET_REQUESTED, ...) therefore made inserts fail on any
 * database created before the change - for audit logs that failure is only
 * logged, so the rows silently disappear.
 *
 * For each listed column whose constraint exists but lacks a current enum
 * value, the constraint is dropped and recreated from the enum. Constraints
 * that do not exist (fresh database) are left to Hibernate. A no-op once the
 * schema is current.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class EnumConstraintSync implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    private record Target(String table, String column, Class<? extends Enum<?>> type) {
        String constraint() {
            return table + "_" + column + "_check";
        }
    }

    private static final List<Target> TARGETS = List.of(
            new Target("requests", "status", RequestStatus.class),
            new Target("audit_logs", "action_type", ActionType.class),
            new Target("notifications", "action_type", ActionType.class)
    );

    @Override
    public void run(ApplicationArguments args) {
        for (Target t : TARGETS) {
            try {
                sync(t);
            } catch (Exception e) {
                // Never block startup over this; the cause is logged.
                log.error("Could not sync constraint {}: {}",
                        t.constraint(), e.getMessage());
            }
        }
    }

    private void sync(Target t) {
        List<String> defs = jdbc.queryForList(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                        + "WHERE conname = ? AND conrelid = ?::regclass",
                String.class, t.constraint(), t.table());

        if (defs.isEmpty()) {
            return;   // fresh schema: Hibernate created (or will create) it
        }

        String def = defs.get(0);
        List<String> values = Arrays.stream(t.type().getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toList());

        boolean current = values.stream().allMatch(v -> def.contains("'" + v + "'"));
        if (current) {
            return;
        }

        // Names come from our own enum constants, never from user input.
        String list = values.stream()
                .map(v -> "'" + v + "'")
                .collect(Collectors.joining(", "));

        jdbc.execute("ALTER TABLE " + t.table()
                + " DROP CONSTRAINT " + t.constraint());
        jdbc.execute("ALTER TABLE " + t.table()
                + " ADD CONSTRAINT " + t.constraint()
                + " CHECK (" + t.column() + " IN (" + list + "))");

        log.info("Updated constraint {} to the current {} values",
                t.constraint(), t.type().getSimpleName());
    }
}
