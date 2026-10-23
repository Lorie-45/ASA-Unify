package com.asa.asaunify.config;

import com.asa.asaunify.entity.User;
import com.asa.asaunify.repos.UserRepo;
import com.asa.asaunify.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-off backfill: brings addresses stored before normalization into
 * canonical form.
 *
 * Lookups now normalize the address first, so a row still stored as
 * "John@x.com" would never be found again — the account would silently stop
 * being able to log in or reset its password. This repairs those rows.
 *
 * Runs before {@link Seeder} (which has no explicit order, so it sorts last),
 * because the seeder decides whether to create the admin account by looking up
 * a normalized address — against an un-normalized row that check would miss
 * and it would insert a duplicate admin.
 *
 * Cheap no-op once the data is clean: the query matches nothing.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class EmailNormalizationRunner implements ApplicationRunner {

    private final UserRepo userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> affected = userRepository.findWithNonNormalizedEmail();
        if (affected.isEmpty()) {
            return;
        }

        int updated = 0;
        int skipped = 0;

        for (User user : affected) {
            String normalized = EmailNormalizer.normalize(user.getEmail());

            // Two historic rows may differ only by case. Collapsing them would
            // violate the unique constraint and, worse, guess which account is
            // the real one — so leave both alone and let an admin decide.
            if (userRepository.existsByEmail(normalized)) {
                // Ids only: no address in the log line.
                log.warn("Skipping email normalization for user {} — the "
                        + "canonical form is already taken by another account. "
                        + "Resolve the duplicate manually.", user.getId());
                skipped++;
                continue;
            }

            user.setEmail(normalized);
            userRepository.save(user);
            updated++;
        }

        log.info("Email normalization backfill: {} updated, {} skipped",
                updated, skipped);
    }
}
