package com.smashchat.Utils;

import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility class for secure message encryption using AES-256-CBC.
 * Each conversation (Room) has a unique derived key.
 */
public class AESalgorithm {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String APP_SALT = "SmashChat_Secure_Msg_Salt_2024";

    /**
     * Encrypts a message using a key derived from the Room ID.
     */
    public static String encrypt(String message, String roomId) {
        if (message == null || roomId == null) return message;
        try {
            byte[] key = deriveKey(roomId);
            SecretKeySpec secretKey = new SecretKeySpec(key, ALGORITHM);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encryptedBytes = cipher.doFinal(message.getBytes("UTF-8"));
            
            // Combine IV and Encrypted Message: [IV (16 bytes)][EncryptedData]
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);
            
            return bytesToHex(combined);
        } catch (Exception e) {
            e.printStackTrace();
            return message;
        }
    }

    /**
     * Decrypts a message using a key derived from the Room ID.
     */
    public static String decrypt(String encryptedHex, String roomId) {
        if (encryptedHex == null || roomId == null) return encryptedHex;
        try {
            byte[] combined = hexToBytes(encryptedHex);
            if (combined.length < 16) return encryptedHex; // Not a valid encrypted message
            
            byte[] iv = new byte[16];
            byte[] encryptedBytes = new byte[combined.length - 16];
            System.arraycopy(combined, 0, iv, 0, 16);
            System.arraycopy(combined, 16, encryptedBytes, 0, encryptedBytes.length);
            
            byte[] key = deriveKey(roomId);
            SecretKeySpec secretKey = new SecretKeySpec(key, ALGORITHM);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            
            return new String(decryptedBytes, "UTF-8");
        } catch (Exception e) {
            // Fallback to original text (might be plaintext message)
            return encryptedHex;
        }
    }

    private static byte[] deriveKey(String roomId) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String input = roomId + APP_SALT;
        return digest.digest(input.getBytes("UTF-8"));
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

    private static byte[] hexToBytes(String hexString) {
        try {
            int len = hexString.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                                     + Character.digit(hexString.charAt(i + 1), 16));
            }
            return data;
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
