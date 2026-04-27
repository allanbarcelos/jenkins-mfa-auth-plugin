package io.jenkins.plugins;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class BackupCodeUtil {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    // Unambiguous characters: no 0/O, 1/I/L confusion
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    static final int CODE_COUNT = 10;

    public static String[] generateCodes() {
        String[] codes = new String[CODE_COUNT];
        for (int i = 0; i < CODE_COUNT; i++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int j = 0; j < CODE_LENGTH; j++) {
                sb.append(CHARS.charAt(SECURE_RANDOM.nextInt(CHARS.length())));
            }
            codes[i] = sb.toString();
        }
        return codes;
    }

    public static String hash(String code) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(code.toUpperCase().trim().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String[] hashCodes(String[] codes) {
        String[] hashes = new String[codes.length];
        for (int i = 0; i < codes.length; i++) {
            hashes[i] = hash(codes[i]);
        }
        return hashes;
    }

    public static boolean isBackupCode(String code) {
        if (code == null) return false;
        String trimmed = code.trim();
        // Backup codes are 8 uppercase alphanumeric characters; TOTP codes are 6 digits
        return trimmed.length() == CODE_LENGTH && !trimmed.matches("\\d+");
    }

    /**
     * Checks if the code matches any stored hash and removes it (single-use).
     * Returns true if the code was valid and consumed.
     */
    public static boolean consume(String code, List<String> storedHashes) {
        String h = hash(code);
        return storedHashes.remove(h);
    }

    public static List<String> fromStorageString(String stored) {
        List<String> list = new ArrayList<>();
        if (stored != null && !stored.isEmpty()) {
            for (String h : stored.split(",")) {
                String trimmed = h.trim();
                if (!trimmed.isEmpty()) list.add(trimmed);
            }
        }
        return list;
    }

    public static String toStorageString(List<String> hashes) {
        return String.join(",", hashes);
    }

    public static String toStorageString(String[] hashes) {
        return String.join(",", hashes);
    }
}
