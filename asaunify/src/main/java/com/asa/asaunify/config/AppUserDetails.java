package com.asa.asaunify.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.time.Instant;
import java.util.Collection;

/**
 * Spring Security's UserDetails plus the one extra fact JwtFilter needs:
 * when this account's password last changed.
 *
 * Carrying it on the principal means the filter can reject tokens older than
 * the last password change without a second database round-trip per request.
 */
public class AppUserDetails extends User {

    private final transient Instant passwordChangedAt;

    public AppUserDetails(
            String username,
            String password,
            boolean enabled,
            boolean accountNonLocked,
            Collection<? extends GrantedAuthority> authorities,
            Instant passwordChangedAt) {

        super(username, password, enabled, true, true,
                accountNonLocked, authorities);
        this.passwordChangedAt = passwordChangedAt;
    }

    /** Null for accounts whose password has never been changed. */
    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }
}
