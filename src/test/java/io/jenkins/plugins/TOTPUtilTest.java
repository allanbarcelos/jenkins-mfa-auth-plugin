package io.jenkins.plugins;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.Test;

class TOTPUtilTest {

    @Test
    void generateSecret_producesValidBase32() {
        String secret = TOTPUtil.generateSecret();
        assertNotNull(secret);
        assertFalse(secret.isEmpty());
        // Base32 charset: A-Z and 2-7 with optional padding
        assertTrue(secret.matches("[A-Z2-7]+=*"), "Secret should be valid Base32: " + secret);
    }

    @Test
    void generateSecret_isDecodable() {
        String secret = TOTPUtil.generateSecret();
        byte[] decoded = new Base32().decode(secret);
        assertEquals(20, decoded.length, "Decoded secret should be 160 bits (20 bytes)");
    }

    @Test
    void verifyCode_withCurrentWindow() throws Exception {
        String secret = TOTPUtil.generateSecret();
        long time = System.currentTimeMillis() / 1000 / TOTPUtil.TIME_STEP;
        String code = TOTPUtil.generateTOTP(secret, time);
        assertTrue(TOTPUtil.verifyCode(secret, code));
    }

    @Test
    void verifyCode_withPreviousWindow_succeeds() throws Exception {
        String secret = TOTPUtil.generateSecret();
        long time = System.currentTimeMillis() / 1000 / TOTPUtil.TIME_STEP;
        String previousCode = TOTPUtil.generateTOTP(secret, time - 1);
        assertTrue(TOTPUtil.verifyCode(secret, previousCode),
                "Should accept code from previous 30-second window (clock skew tolerance)");
    }

    @Test
    void verifyCode_withNextWindow_succeeds() throws Exception {
        String secret = TOTPUtil.generateSecret();
        long time = System.currentTimeMillis() / 1000 / TOTPUtil.TIME_STEP;
        String nextCode = TOTPUtil.generateTOTP(secret, time + 1);
        assertTrue(TOTPUtil.verifyCode(secret, nextCode),
                "Should accept code from next 30-second window (clock skew tolerance)");
    }

    @Test
    void verifyCode_withOldWindow_fails() throws Exception {
        String secret = TOTPUtil.generateSecret();
        long time = System.currentTimeMillis() / 1000 / TOTPUtil.TIME_STEP;
        String staleCode = TOTPUtil.generateTOTP(secret, time - 2);
        assertFalse(TOTPUtil.verifyCode(secret, staleCode),
                "Should reject code older than 1 window");
    }

    @Test
    void verifyCode_withWrongCode_fails() {
        String secret = TOTPUtil.generateSecret();
        assertFalse(TOTPUtil.verifyCode(secret, "000000"));
    }

    @Test
    void verifyCode_withNullSecret_returnsFalse() {
        assertFalse(TOTPUtil.verifyCode((String) null, "123456"));
    }

    @Test
    void verifyCode_withNullCode_returnsFalse() {
        assertFalse(TOTPUtil.verifyCode(TOTPUtil.generateSecret(), null));
    }

    @Test
    void verifyCode_withWrongLength_returnsFalse() {
        String secret = TOTPUtil.generateSecret();
        assertFalse(TOTPUtil.verifyCode(secret, "12345"));   // too short
        assertFalse(TOTPUtil.verifyCode(secret, "1234567")); // too long
    }

    @Test
    void getQRBarcodeURL_containsRequiredParts() {
        String url = TOTPUtil.getQRBarcodeURL("alice", "Jenkins", "SECRETKEY");
        assertTrue(url.startsWith("otpauth://totp/"));
        assertTrue(url.contains("Jenkins:alice"));
        assertTrue(url.contains("secret=SECRETKEY"));
        assertTrue(url.contains("issuer=Jenkins"));
        assertTrue(url.contains("algorithm=SHA1"));
        assertTrue(url.contains("digits=6"));
        assertTrue(url.contains("period=30"));
    }
}
