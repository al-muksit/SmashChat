package com.smashchat.Utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class HashAlgorithmTest {

    @Test
    public void testHashPasswordConsistency() {
        String password = "password123";
        String salt = "user_uid_123";
        
        String hash1 = HashAlgorithm.hashPassword(password, salt);
        String hash2 = HashAlgorithm.hashPassword(password, salt);
        
        assertEquals("Hashes should be consistent for the same password and salt", hash1, hash2);
    }

    @Test
    public void testHashPasswordDifferentSalt() {
        String password = "password123";
        String salt1 = "user_uid_1";
        String salt2 = "user_uid_2";
        
        String hash1 = HashAlgorithm.hashPassword(password, salt1);
        String hash2 = HashAlgorithm.hashPassword(password, salt2);
        
        assertNotEquals("Hashes should be different for different salts", hash1, hash2);
    }

    @Test
    public void testHashPasswordDifferentPassword() {
        String password1 = "password123";
        String password2 = "password456";
        String salt = "user_uid_123";
        
        String hash1 = HashAlgorithm.hashPassword(password1, salt);
        String hash2 = HashAlgorithm.hashPassword(password2, salt);
        
        assertNotEquals("Hashes should be different for different passwords", hash1, hash2);
    }
}
