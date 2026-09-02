package com.asa.asaunify.config;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets logout invalidate an already-issued JWT even though the tokens are
 * stateless. On logout the presented token's unique id (jti) is added to a
 * blocklist and rejected by {@link JwtFilter} until it would have expired.
 * A fresh login gets a new jti and is unaffected.
 *
 * Note: state is in-memory (per instance, cleared on restart). For multi-node
 * or restart-durable revocation, back this with a shared/persistent store.
 */
@Service
public class TokenRevocationService {

    // jti -> the token's expiry, so entries can be purged once irrelevant.
    private final ConcurrentHashMap<String, Instant> revoked =
            new ConcurrentHashMap<>();

    public void revoke(String jti, Instant expiresAt) {
        if (jti == null) return;
        purgeExpired();
        revoked.put(jti, expiresAt != null ? expiresAt : Instant.now());
    }

    public boolean isRevoked(String jti) {
        if (jti == null) return false;
        Instant exp = revoked.get(jti);
        if (exp == null) return false;
        if (Instant.now().isAfter(exp)) {
            revoked.remove(jti);   // expired anyway
            return false;
        }
        return true;
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        revoked.entrySet().removeIf(e -> now.isAfter(e.getValue()));
    }
}
