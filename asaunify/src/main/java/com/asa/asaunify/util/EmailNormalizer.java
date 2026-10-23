package com.asa.asaunify.util;

import java.util.Locale;

/**
 * Single place where an email address is turned into its canonical form.
 *
 * Addresses are trimmed and lower-cased. The local part of an email is
 * technically case-sensitive per RFC 5321, but no mail provider in practice
 * treats it that way, and storing mixed case meant "John@x.com" and
 * "john@x.com" became two distinct accounts while login silently failed for
 * the wrong casing.
 *
 * Every write and every lookup must route through here so the stored form and
 * the lookup form can never drift apart. Deliberately does NOT attempt to
 * verify that the mailbox exists.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    /** Returns the canonical form, or null if the input is null. */
    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        // Locale.ROOT — a Turkish-locale default would lower-case 'I' to a
        // dotless 'ı' and break lookups for those addresses.
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
