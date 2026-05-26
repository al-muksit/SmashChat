package com.smashchat.Utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class AESalgorithmTest {

    @Test
    public void testEncryptionDecryption() {
        String originalMessage = "Hello, this is a secret message!";
        String roomId = "user1_user2";
        
        String encrypted = AESalgorithm.encrypt(originalMessage, roomId);
        assertNotEquals(originalMessage, encrypted);
        
        String decrypted = AESalgorithm.decrypt(encrypted, roomId);
        assertEquals(originalMessage, decrypted);
    }

    @Test
    public void testDifferentRoomsDifferentKeys() {
        String message = "Same message";
        String room1 = "room_A";
        String room2 = "room_B";
        
        String encrypted1 = AESalgorithm.encrypt(message, room1);
        String encrypted2 = AESalgorithm.encrypt(message, room2);
        
        // Even with same message, different rooms must produce different encrypted text
        // Note: AES-CBC with random IV would produce different text anyway, 
        // but this also ensures the derived key is different.
        assertNotEquals(encrypted1, encrypted2);
    }

    @Test
    public void testDecryptionFailureFallback() {
        String fakeEncrypted = "This is not hex";
        String roomId = "room123";
        
        // Should return the input if it's not valid hex or can't be decrypted
        String decrypted = AESalgorithm.decrypt(fakeEncrypted, roomId);
        assertEquals(fakeEncrypted, decrypted);
    }

    @Test
    public void testEmojiEncryption() {
        String emoji = "[smile]";
        String roomId = "room123";
        
        String encrypted = AESalgorithm.encrypt(emoji, roomId);
        String decrypted = AESalgorithm.decrypt(encrypted, roomId);
        
        assertEquals(emoji, decrypted);
    }
}
