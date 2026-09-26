package com.pacific.marketplace;

import com.pacific.marketplace.service.PasswordPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Passwords that are easy to guess are turned down, whatever the capitals or digits on the end. */
class PasswordPolicyTest {

    private static void refused(String password, String message) {
        assertThatThrownBy(() -> PasswordPolicy.check(password, "casey.jones@example.com", "Casey Jones"))
                .hasMessageContaining(message);
    }

    @Test
    void commonPasswordsAndPatternsAreRefused() {
        for (String p : new String[] {"password", "Password123!", "PASSWORD1", "qwerty123", "iloveyou2", "letmein!!",
                "12345678", "87654321", "abcdefgh", "aaaaaaaa", "abababab", "Pacific2026"}) {
            refused(p, "too easy to guess");
        }
    }

    @Test
    void passwordsMadeFromTheEmailOrNameAreRefused() {
        refused("caseyjones", "email address");
        refused("CaseyJones99", "email address");
        assertThatThrownBy(() -> PasswordPolicy.check("jones1985", "cj@example.com", "Casey Jones"))
                .hasMessageContaining("your name");
    }

    @Test
    void ordinaryPasswordsAreFine() {
        for (String p : new String[] {"correct-horse-battery", "river-lamp-orbit", "Tr0ub4dor&3", "my dog likes toast"}) {
            assertThatCode(() -> PasswordPolicy.check(p, "casey.jones@example.com", "Casey Jones")).doesNotThrowAnyException();
        }
    }
}
