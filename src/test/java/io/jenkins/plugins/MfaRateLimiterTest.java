package io.jenkins.plugins;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MfaRateLimiterTest {

    @BeforeEach
    void resetState() {
        MfaRateLimiter.reset("alice");
        MfaRateLimiter.reset("bob");
    }

    @Test
    void notLockedInitially() {
        assertFalse(MfaRateLimiter.isLocked("alice"));
    }

    @Test
    void notLockedBeforeMaxAttempts() {
        for (int i = 0; i < MfaRateLimiter.MAX_ATTEMPTS - 1; i++) {
            MfaRateLimiter.recordFailure("alice");
        }
        assertFalse(MfaRateLimiter.isLocked("alice"));
    }

    @Test
    void lockedAfterMaxAttempts() {
        for (int i = 0; i < MfaRateLimiter.MAX_ATTEMPTS; i++) {
            MfaRateLimiter.recordFailure("alice");
        }
        assertTrue(MfaRateLimiter.isLocked("alice"));
    }

    @Test
    void lockoutExpiresAfterTimeout() {
        for (int i = 0; i < MfaRateLimiter.MAX_ATTEMPTS; i++) {
            MfaRateLimiter.recordFailure("alice");
        }
        assertTrue(MfaRateLimiter.isLocked("alice"));

        // Simulate time passage past the lockout window
        long expiredTime = (System.currentTimeMillis() / 1000) - MfaRateLimiter.LOCKOUT_SECONDS - 1;
        MfaRateLimiter.setLastAttemptAt("alice", expiredTime);

        assertFalse(MfaRateLimiter.isLocked("alice"),
                "Lock should be released after lockout period expires");
    }

    @Test
    void resetClearsAttempts() {
        for (int i = 0; i < MfaRateLimiter.MAX_ATTEMPTS; i++) {
            MfaRateLimiter.recordFailure("alice");
        }
        assertTrue(MfaRateLimiter.isLocked("alice"));
        MfaRateLimiter.reset("alice");
        assertFalse(MfaRateLimiter.isLocked("alice"));
        assertEquals(0, MfaRateLimiter.getAttemptCount("alice"));
    }

    @Test
    void differentUsersAreIndependent() {
        for (int i = 0; i < MfaRateLimiter.MAX_ATTEMPTS; i++) {
            MfaRateLimiter.recordFailure("alice");
        }
        assertTrue(MfaRateLimiter.isLocked("alice"));
        assertFalse(MfaRateLimiter.isLocked("bob"));
    }

    @Test
    void attemptCountIncrements() {
        assertEquals(0, MfaRateLimiter.getAttemptCount("alice"));
        MfaRateLimiter.recordFailure("alice");
        assertEquals(1, MfaRateLimiter.getAttemptCount("alice"));
        MfaRateLimiter.recordFailure("alice");
        assertEquals(2, MfaRateLimiter.getAttemptCount("alice"));
    }
}
