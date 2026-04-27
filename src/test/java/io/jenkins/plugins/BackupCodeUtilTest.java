package io.jenkins.plugins;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackupCodeUtilTest {

    @Test
    void generateCodes_correctCount() {
        String[] codes = BackupCodeUtil.generateCodes();
        assertEquals(BackupCodeUtil.CODE_COUNT, codes.length);
    }

    @Test
    void generateCodes_allUnique() {
        String[] codes = BackupCodeUtil.generateCodes();
        assertEquals(codes.length, new HashSet<>(Arrays.asList(codes)).size(),
                "All backup codes should be unique");
    }

    @Test
    void generateCodes_correctLength() {
        String[] codes = BackupCodeUtil.generateCodes();
        for (String code : codes) {
            assertEquals(8, code.length(), "Each backup code should be 8 characters");
        }
    }

    @Test
    void hash_isConsistent() {
        String hash1 = BackupCodeUtil.hash("ABCD1234");
        String hash2 = BackupCodeUtil.hash("ABCD1234");
        assertEquals(hash1, hash2);
    }

    @Test
    void hash_isCaseInsensitive() {
        assertEquals(BackupCodeUtil.hash("abcd1234"), BackupCodeUtil.hash("ABCD1234"));
    }

    @Test
    void hash_differentiatesInputs() {
        assertNotEquals(BackupCodeUtil.hash("ABCD1234"), BackupCodeUtil.hash("ABCD1235"));
    }

    @Test
    void consume_validCode_returnsTrue() {
        String[] codes = BackupCodeUtil.generateCodes();
        String[] hashes = BackupCodeUtil.hashCodes(codes);
        List<String> hashList = Arrays.asList(hashes);
        assertTrue(BackupCodeUtil.consume(codes[0], hashList));
    }

    @Test
    void consume_invalidCode_returnsFalse() {
        String[] hashes = BackupCodeUtil.hashCodes(BackupCodeUtil.generateCodes());
        List<String> hashList = Arrays.asList(hashes);
        assertFalse(BackupCodeUtil.consume("ZZZZZZZZ", hashList));
    }

    @Test
    void consume_isSingleUse() {
        String[] codes = BackupCodeUtil.generateCodes();
        String[] hashes = BackupCodeUtil.hashCodes(codes);
        List<String> hashList = new java.util.ArrayList<>(Arrays.asList(hashes));
        assertTrue(BackupCodeUtil.consume(codes[0], hashList));
        assertFalse(BackupCodeUtil.consume(codes[0], hashList), "Code should only work once");
    }

    @Test
    void consume_removesFromList() {
        String[] codes = BackupCodeUtil.generateCodes();
        String[] hashes = BackupCodeUtil.hashCodes(codes);
        List<String> hashList = new java.util.ArrayList<>(Arrays.asList(hashes));
        int before = hashList.size();
        BackupCodeUtil.consume(codes[0], hashList);
        assertEquals(before - 1, hashList.size());
    }

    @Test
    void isBackupCode_withValidBackupCode() {
        assertTrue(BackupCodeUtil.isBackupCode("ABCD1234"));
        assertTrue(BackupCodeUtil.isBackupCode("  ABCD1234  ")); // trim handled
    }

    @Test
    void isBackupCode_withTotpCode_returnsFalse() {
        assertFalse(BackupCodeUtil.isBackupCode("123456")); // TOTP codes are all digits
    }

    @Test
    void isBackupCode_withNull_returnsFalse() {
        assertFalse(BackupCodeUtil.isBackupCode(null));
    }

    @Test
    void storageRoundTrip() {
        String[] codes = BackupCodeUtil.generateCodes();
        String[] hashes = BackupCodeUtil.hashCodes(codes);
        String stored = BackupCodeUtil.toStorageString(hashes);
        List<String> loaded = BackupCodeUtil.fromStorageString(stored);
        assertEquals(Arrays.asList(hashes), loaded);
    }

    @Test
    void fromStorageString_emptyString_returnsEmptyList() {
        assertTrue(BackupCodeUtil.fromStorageString("").isEmpty());
        assertTrue(BackupCodeUtil.fromStorageString(null).isEmpty());
    }
}
