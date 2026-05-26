package com.smashchat.Utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class HashAlgorithmTest {

    @Test
    public void testHashPasswordConsistency() {
        String password = "password123";
        String email = "test@example.com";
        
        String hash1 = HashAlgorithm.hashPassword(password, email);
        String hash2 = HashAlgorithm.hashPassword(password, email);
        
        assertEquals("Hashes should be consistent for the same password and email", hash1, hash2);
    }

    @Test
    public void testHashPasswordDifferentEmail() {
        String password = "password123";
        String email1 = "user1@example.com";
        String email2 = "user2@example.com";
        
        String hash1 = HashAlgorithm.hashPassword(password, email1);
        String hash2 = HashAlgorithm.hashPassword(password, email2);
        
        assertNotEquals("Hashes should be different for different email salts", hash1, hash2);
    }

    @Test
    public void testHashPasswordDifferentPassword() {
        String password1 = "password123";
        String password2 = "password456";
        String email = "test@example.com";
        
        String hash1 = HashAlgorithm.hashPassword(password1, email);
        String hash2 = HashAlgorithm.hashPassword(password2, email);
        
        assertNotEquals("Hashes should be different for different passwords", hash1, hash2);
    }
}
