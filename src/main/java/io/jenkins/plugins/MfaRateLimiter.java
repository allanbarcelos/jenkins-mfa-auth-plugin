package io.jenkins.plugins;

import java.util.concurrent.ConcurrentHashMap;

public final class MfaRateLimiter {
    static final int MAX_ATTEMPTS = 5;
    static final long LOCKOUT_SECONDS = 900; // 15 minutes

    private static final ConcurrentHashMap<String, AttemptRecord> ATTEMPTS = new ConcurrentHashMap<>();

    public static boolean isLocked(String userId) {
        AttemptRecord record = ATTEMPTS.get(userId);
        if (record == null) return false;
        if (record.count >= MAX_ATTEMPTS) {
            long now = System.currentTimeMillis() / 1000;
            if (now - record.lastAttemptAt < LOCKOUT_SECONDS) {
                return true;
            }
            ATTEMPTS.remove(userId);
        }
        return false;
    }

    public static void recordFailure(String userId) {
        long now = System.currentTimeMillis() / 1000;
        ATTEMPTS.compute(userId, (k, v) -> {
            AttemptRecord r = (v != null) ? v : new AttemptRecord();
            r.count++;
            r.lastAttemptAt = now;
            return r;
        });
    }

    public static void reset(String userId) {
        ATTEMPTS.remove(userId);
    }

    static int getAttemptCount(String userId) {
        AttemptRecord r = ATTEMPTS.get(userId);
        return r == null ? 0 : r.count;
    }

    // Exposed for testing: simulate time passage without sleeping
    static void setLastAttemptAt(String userId, long epochSeconds) {
        AttemptRecord r = ATTEMPTS.get(userId);
        if (r != null) r.lastAttemptAt = epochSeconds;
    }

    private static class AttemptRecord {
        int count;
        long lastAttemptAt;
    }
}
