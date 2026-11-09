package com.austinv11.collectiveframework.utils.security;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class EncryptionTest {

    @Test
    public void base64RoundTrip() throws Exception {
        String encoded = Encryption.base64Encode("hello");
        Assert.assertEquals("aGVsbG8=", encoded);
        Assert.assertEquals("hello", Encryption.base64Decode(encoded));
    }

    @Test
    public void base64RoundTripWithExplicitEncoding() throws Exception {
        String encoded = Encryption.base64Encode("h\u00e9llo", "UTF-8");
        Assert.assertEquals("h\u00e9llo", Encryption.base64Decode(encoded, "UTF-8"));
    }

    @Test
    public void base64EncodeUsesUtf8ByDefault() throws Exception {
        Assert.assertEquals(Encryption.base64Encode("h\u00e9llo", "UTF-8"), Encryption.base64Encode("h\u00e9llo"));
    }

    @Test
    public void encryptionTypeJoinsAlgorithmModeAndPadding() {
        Assert.assertEquals(
            "AES/CBC/PKCS5Padding",
            new Encryption.EncryptionType("AES", "CBC", "PKCS5Padding").toString());
        Assert.assertEquals("AES", new Encryption.EncryptionType("AES").toString());
    }

    @Test
    public void encryptionTypeConstantsAreStable() {
        Assert.assertEquals("AES", Encryption.EncryptionType.AES);
        Assert.assertEquals("CBC", Encryption.EncryptionType.CBC);
        Assert.assertEquals("PKCS5Padding", Encryption.EncryptionType.PKCS5_PADDING);
    }

    @Test
    public void secretValueExposesItsPartsWhenNotCallerSensitive() {
        SecretValue<String, String, Integer> secret = Encryption.newSecretValue("value", "key", 7);
        Assert.assertEquals("value", Encryption.getValue(secret));
        Assert.assertEquals("key", Encryption.getKey(secret));
        Assert.assertEquals(Integer.valueOf(7), Encryption.getMeta(secret));
    }

    @Test
    public void callerSensitiveSecretValueAllowsTheCreatingClass() {
        SecretValue<String, String, Integer> secret = Encryption.newSecretValue("value", "key", 7, true);
        Assert.assertEquals("value", Encryption.getValue(secret));
    }

    @Test(expected = SecurityException.class)
    public void callerSensitiveSecretValueRejectsOtherCallers() {
        SecretValue<String, String, Integer> secret = Encryption
            .newSecretValue("value", "key", 7, true, "com.example.SomebodyElse");
        Encryption.getValue(secret);
    }

    /**
     * CODE_REVIEW §1.19 - {@code encrypt} converts the ciphertext to a {@link String} with the platform
     * charset and {@code decrypt} converts it back with {@code String.getBytes()}. The conversion is lossy,
     * so the round trip cannot work for arbitrary binary ciphertext.
     */
    @Ignore("CODE_REVIEW §1.19: ciphertext is round-tripped through a lossy String conversion")
    @Test
    public void aesRoundTrip() throws Exception {
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        for (int i = 0; i < 16; i++) {
            key[i] = (byte) i;
            iv[i] = (byte) (16 - i);
        }
        SecretValue<String, byte[], Integer> encrypted = Encryption
            .encrypt("hello world", "AES/CBC/PKCS5Padding", key, iv);
        SecretValue<String, byte[], Integer> decrypted = Encryption
            .decrypt(Encryption.getValue(encrypted), Encryption.getMeta(encrypted), "AES/CBC/PKCS5Padding", key, iv);
        Assert.assertEquals("hello world", Encryption.getValue(decrypted));
    }

    /**
     * CODE_REVIEW §1.19 - {@code encrypt} also wraps the whole output buffer, including the zero padding
     * past {@code encryptionLength}, in the returned string.
     */
    @Ignore("CODE_REVIEW §1.19: new String(encrypted) covers bytes beyond encryptionLength")
    @Test
    public void encryptShouldNotIncludeTrailingPadding() throws Exception {
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        SecretValue<String, byte[], Integer> encrypted = Encryption.encrypt("hello", "AES/CBC/PKCS5Padding", key, iv);
        byte[] cipherText = Encryption.getValue(encrypted)
            .getBytes(StandardCharsets.ISO_8859_1);
        Assert.assertEquals(
            Encryption.getMeta(encrypted)
                .intValue(),
            cipherText.length);
    }
}
