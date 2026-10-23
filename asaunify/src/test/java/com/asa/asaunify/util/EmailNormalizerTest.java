package com.asa.asaunify.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmailNormalizerTest {

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("user@asa.com", EmailNormalizer.normalize("  user@asa.com "));
    }

    @Test
    void lowercasesTheWholeAddress() {
        assertEquals("john.doe@asa.com",
                EmailNormalizer.normalize("John.Doe@ASA.com"));
    }

    @Test
    void isIdempotent() {
        String once = EmailNormalizer.normalize(" Mixed@Case.COM ");
        assertEquals(once, EmailNormalizer.normalize(once));
    }

    @Test
    void passesNullThrough() {
        assertNull(EmailNormalizer.normalize(null));
    }

    @Test
    void lowercasesDottedCapitalIWithoutLocaleDamage() {
        // Guards the Locale.ROOT choice: a Turkish default locale maps 'I' to
        // a dotless 'ı', which would make the address unfindable.
        assertEquals("ivan@asa.com", EmailNormalizer.normalize("IVAN@ASA.COM"));
    }
}
