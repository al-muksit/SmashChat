package com.smashchat.Utils;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Utility class for secure password hashing using PBKDF2 with HMAC-SHA256.
 */
public class HashAlgorithm {

    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256; // bits
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /**
     * Hashes a password using PBKDF2 with a salt.
     *
     * @param password The plaintext password to hash.
     * @param salt     A unique salt (e.g., user ID) to prevent rainbow table attacks.
     * @return The hashed password as a Hex-encoded string, or the original password if hashing fails.
     */
    public static String hashPassword(String password, String salt) {
        if (password == null || salt == null) {
            return password;
        }

        char[] passwordChars = password.toCharArray();
        byte[] saltBytes = salt.getBytes();

        PBEKeySpec spec = new PBEKeySpec(passwordChars, saltBytes, ITERATIONS, KEY_LENGTH);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hashedBytes = factory.generateSecret(spec).getEncoded();
            return bytesToHex(hashedBytes);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
            return password;
        } finally {
            spec.clearPassword();
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Legacy SHA-256 implementation (retained for backward compatibility if needed).
     * @deprecated Use {@link #hashPassword(String, String)} for better security.
     */
    @Deprecated
    public static String sha256(String base) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            return bytesToHex(hash);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
