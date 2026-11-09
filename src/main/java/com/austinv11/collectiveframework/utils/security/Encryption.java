package com.austinv11.collectiveframework.utils.security;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * This class simplifies and attempts to merge all java cipher/encryption apis
 */
public final class Encryption {

    /**
     * Encrypts a value
     *
     * @param string               The string to encrypt
     * @param algorithm            The algorithm, mode and padding to use
     * @param key                  The key
     * @param initializationVector The initialization vector
     * @return The value, use {@link Encryption#getValue(SecretValue)} to get the encrypted value and
     *         {@link Encryption#getMeta(SecretValue)} to get the length of the encryption
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws InvalidAlgorithmParameterException
     * @throws InvalidKeyException
     * @throws ShortBufferException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    public static SecretValue<String, byte[], Integer> encrypt(String string, String algorithm, byte[] key,
        byte[] initializationVector)
        throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
        InvalidKeyException, ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        byte[] data = string.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        SecretKeySpec keySpec;
        if (algorithm.contains("/")) keySpec = new SecretKeySpec(key, algorithm.split("/")[0]);
        else keySpec = new SecretKeySpec(key, algorithm);
        IvParameterSpec ivSpec = new IvParameterSpec(initializationVector);
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = new byte[cipher.getOutputSize(data.length)];
        int encryptionLength = cipher.update(data, 0, data.length, encrypted, 0);
        encryptionLength += cipher.doFinal(encrypted, encryptionLength);

        // The ciphertext used to be wrapped in `new String(encrypted)`, which decodes it with the platform
        // charset and replaces every malformed byte sequence with U+FFFD - so decrypt(encrypt(x)) could never
        // return x. It also covered the whole buffer, including the zero padding past encryptionLength. Base64
        // over exactly encryptionLength bytes is lossless (CODE_REVIEW §1.19).
        byte[] cipherText = java.util.Arrays.copyOf(encrypted, encryptionLength);
        return newSecretValue(
            java.util.Base64.getEncoder()
                .encodeToString(cipherText),
            key,
            encryptionLength);
    }

    /**
     * Decrypts a value
     *
     * @param string               The encrypted string
     * @param encryptionLength     The length of the encryption
     * @param algorithm            The algorithm, mode and padding to use
     * @param key                  The key
     * @param initializationVector The initialization vector
     * @return The value, use {@link Encryption#getValue(SecretValue)} to get the decrypted value and
     *         {@link Encryption#getMeta(SecretValue)} to get the length of the decryption
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws InvalidAlgorithmParameterException
     * @throws InvalidKeyException
     * @throws ShortBufferException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    public static SecretValue<String, byte[], Integer> decrypt(String string, int encryptionLength, String algorithm,
        byte[] key, byte[] initializationVector)
        throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
        InvalidKeyException, ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        byte[] data = java.util.Base64.getDecoder()
            .decode(string);
        SecretKeySpec keySpec;
        if (algorithm.contains("/")) keySpec = new SecretKeySpec(key, algorithm.split("/")[0]);
        else keySpec = new SecretKeySpec(key, algorithm);
        IvParameterSpec ivSpec = new IvParameterSpec(initializationVector);
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        // Base64 already carries the exact ciphertext length, so the recorded length is only a cross-check.
        int length = encryptionLength > 0 && encryptionLength <= data.length ? encryptionLength : data.length;
        byte[] decrypted = new byte[cipher.getOutputSize(length)];
        int decryptionLength = cipher.update(data, 0, length, decrypted, 0);
        decryptionLength += cipher.doFinal(decrypted, decryptionLength);
        // The whole buffer was decoded before, so the plaintext picked up the trailing zero padding.
        return newSecretValue(
            new String(decrypted, 0, decryptionLength, java.nio.charset.StandardCharsets.UTF_8),
            key,
            decryptionLength);
    }

    /**
     * Creates a new secret value
     *
     * @param value             The value
     * @param key               The key
     * @param meta              Any metadata
     * @param isCallerSensitive Set this to true to limit callers (if supported on the JDK)
     * @param approvedCaller    The only class allowed to retrieve info from it
     * @return The secret value
     */
    public static <V, K, M> SecretValue<V, K, M> newSecretValue(V value, K key, M meta, boolean isCallerSensitive,
        String approvedCaller) {
        return new SecretValue<V, K, M>(value, key, meta, isCallerSensitive, approvedCaller);
    }

    /**
     * Creates a new secret value with the approved caller as the caller of this method
     *
     * @param value             The value
     * @param key               The key
     * @param meta              Any metadata
     * @param isCallerSensitive Set this to true to limit callers (if supported on the JDK)
     * @return The secret value
     */
    public static <V, K, M> SecretValue<V, K, M> newSecretValue(V value, K key, M meta, boolean isCallerSensitive) {
        if (isCallerSensitive) return newSecretValue(value, key, meta, isCallerSensitive, getCallerClassName());
        else return newSecretValue(value, key, meta);
    }

    /**
     * Creates a new secret value
     *
     * @param value The value
     * @param key   The key
     * @param meta  Any metadata
     * @return The secret value
     */
    public static <V, K, M> SecretValue<V, K, M> newSecretValue(V value, K key, M meta) {
        return new SecretValue<V, K, M>(value, key, meta);
    }

    /**
     * Gets the value of the SecretValue
     *
     * @param value The SecretValue
     * @return The value
     */
    public static <V, K, M> V getValue(SecretValue<V, K, M> value) {
        if (value.isCallerSensitive) {
            if (!Objects.equals(getCallerClassName(), value.approvedCaller))
                throw new SecurityException("Unapproved caller!");
        }
        return value.value;
    }

    /**
     * Gets the key of the SecretValue
     *
     * @param value The SecretValue
     * @return The key
     */
    public static <V, K, M> K getKey(SecretValue<V, K, M> value) {
        if (value.isCallerSensitive) {
            if (!Objects.equals(getCallerClassName(), value.approvedCaller))
                throw new SecurityException("Unapproved caller!");
        }
        return value.key;
    }

    /**
     * Gets the metadata of the SecretValue
     *
     * @param value The SecretValue
     * @return The metadata
     */
    public static <V, K, M> M getMeta(SecretValue<V, K, M> value) {
        if (value.isCallerSensitive) {
            if (!Objects.equals(getCallerClassName(), value.approvedCaller))
                throw new SecurityException("Unapproved caller!");
        }
        return value.meta;
    }

    /**
     * Default encoding for base64 encryption
     */
    private static final String DEFAULT_ENCODING = "UTF-8";

    /**
     * Performs a base 64 encoding on a string
     *
     * @param string   The string to encode
     * @param encoding The encoding of the string
     * @return Encoded string
     * @throws UnsupportedEncodingException
     */
    public static String base64Encode(String string, String encoding)
        throws UnsupportedOperationException, UnsupportedEncodingException {
        return Base64.getEncoder()
            .encodeToString(string.getBytes(encoding));
    }

    /**
     * Performs a base 64 encoding on a string
     *
     * @param string The string to encode
     * @return Encoded string
     * @throws UnsupportedEncodingException
     */
    public static String base64Encode(String string)
        throws UnsupportedOperationException, UnsupportedEncodingException {
        return base64Encode(string, DEFAULT_ENCODING);
    }

    /**
     * Performs a base 64 decoding on a string
     *
     * @param string   The string to encode
     * @param encoding The encoding of the string
     * @return Decoded string
     * @throws IOException
     */
    public static String base64Decode(String string, String encoding)
        throws UnsupportedOperationException, IOException {
        return new String(
            Base64.getDecoder()
                .decode(string),
            encoding);
    }

    /**
     * Performs a base 64 decoding on a string
     *
     * @param string The string to encode
     * @return Decoded string
     * @throws IOException
     */
    public static String base64Decode(String string) throws UnsupportedOperationException, IOException {
        return base64Decode(string, DEFAULT_ENCODING);
    }

    /**
     * Returns the name of the first class outside of {@link Encryption} on the current call stack.
     * This replaces the internal {@code sun.reflect.Reflection.getCallerClass()} API, which was
     * removed in JDK 9, with a portable stack-trace inspection that also works on Java 8.
     *
     * @return The caller's class name, or {@code null} if no external caller is found
     */
    private static String getCallerClassName() {
        String encryptionClassName = Encryption.class.getName();
        for (StackTraceElement element : new Throwable().getStackTrace()) {
            if (!element.getClassName()
                .equals(encryptionClassName)) return element.getClassName();
        }
        return null;
    }

    /**
     * Eases the management of algorithms, modes, and padding
     * See http://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#Cipher for a description
     * of each.
     * Use this in the place of a string for encryption types.
     */
    public static final class EncryptionType {

        // Algorithms (minus PBE)
        public static final String AES = "AES";
        public static final String AES_WRAP = "AESWrap";
        public static final String ARCFOUR = "ARCFOUR";
        public static final String BLOWFISH = "Blowfish";
        public static final String CCM = "CCM";
        public static final String DES = "DES";
        public static final String DE_SEDE = "DESede";
        public static final String DE_SEDE_WRAP = "DESedeWrap";
        public static final String ECIES = "ECIES";
        public static final String GCM = "GCM";
        // Skipped PBE
        public static final String RC2 = "RC2";
        public static final String RC4 = "RC4";
        public static final String RC5 = "RC5";
        public static final String RSA = "RSA";

        // Modes (does not include number, i.e. CFBx where x is any number)
        public static final String NONE = "NONE";
        public static final String CBC = "CBC";
        public static final String CFB = "CFB";
        public static final String CTR = "CTR";
        public static final String CTS = "CTS";
        public static final String ECB = "ECB";
        public static final String OFB = "OFB";
        public static final String PCBC = "PCBC";

        // Paddings (minus OAEPWith<digest>And<mgf>Padding)
        public static final String NO_PADDING = "NoPadding";
        public static final String ISO10126_PADDING = "ISO10126Padding";
        public static final String OAEP_PADDING = "OAEPPadding";
        public static final String PKCS1_PADDING = "PKCS1Padding";
        public static final String PKCS5_PADDING = "PKCS5Padding";
        public static final String SSL3_PADDING = "SSL3Padding";

        private final String type;

        public EncryptionType(String algorithm) {
            type = algorithm;
        }

        public EncryptionType(String algorithm, String mode, String padding) {
            type = algorithm + "/" + mode + "/" + padding;
        }

        @Override
        public String toString() {
            return type;
        }
    }
}
