package com.smashchat.Services;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.RemoteInput;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.smashchat.Utils.AESalgorithm;

import java.util.Date;
import java.util.HashMap;

/**
 * NotificationReceiver handles background actions from notifications
 * like "Mark as Read" and inline "Reply".
 */
public class NotificationReceiver extends BroadcastReceiver {

    private static final String TAG = "NotificationReceiver";
    public static final String ACTION_MARK_AS_READ = "com.smashchat.ACTION_MARK_AS_READ";
    public static final String ACTION_REPLY = "com.smashchat.ACTION_REPLY";
    public static final String EXTRA_SENDER_ID = "sender_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String senderId = intent.getStringExtra(EXTRA_SENDER_ID);
        String myUid = FirebaseAuth.getInstance().getUid();

        if (myUid == null || senderId == null) return;

        if (ACTION_MARK_AS_READ.equals(action)) {
            markAsRead(senderId, myUid);
            cancelNotification(context, senderId);
        } else if (ACTION_REPLY.equals(action)) {
            handleReply(context, intent, senderId, myUid);
        }
    }

    private void markAsRead(String senderId, String myUid) {
        FirebaseDatabase.getInstance().getReference()
                .child("UserChats").child(myUid).child(senderId)
                .child("read").setValue(true)
                .addOnSuccessListener(unused -> Log.d(TAG, "Marked as read: " + senderId));
    }

    private void handleReply(Context context, Intent intent, String senderId, String myUid) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            CharSequence replyText = remoteInput.getCharSequence(MessageNotificationService.KEY_TEXT_REPLY);
            if (replyText != null) {
                sendMessage(senderId, myUid, replyText.toString());
                // After replying, the notification should ideally be updated or cleared.
                // For a professional feel, we'll mark the existing thread as read and cancel.
                markAsRead(senderId, myUid);
                cancelNotification(context, senderId);
            }
        }
    }

    private void sendMessage(String receiverId, String senderId, String message) {
        long currentTimestamp = new Date().getTime();
        String sharedSecretKey = (senderId.compareTo(receiverId) < 0) ? (senderId + receiverId) : (receiverId + senderId);
        
        String encryptedMessage = AESalgorithm.encrypt(message, sharedSecretKey);
        
        HashMap<String, Object> messageMap = new HashMap<>();
        messageMap.put("uId", senderId);
        messageMap.put("message", encryptedMessage);
        messageMap.put("timestamp", currentTimestamp);
        messageMap.put("type", 0);

        String senderRoom = senderId + receiverId;
        String receiverRoom = receiverId + senderId;

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        database.getReference().child("Messages").child(senderRoom).push().setValue(messageMap)
                .addOnSuccessListener(unused -> {
                    database.getReference().child("Messages").child(receiverRoom).push().setValue(messageMap)
                            .addOnSuccessListener(unused1 -> {
                                updateLastChatInfo(senderId, receiverId, currentTimestamp, encryptedMessage, true);
                                updateLastChatInfo(receiverId, senderId, currentTimestamp, encryptedMessage, false);
                            });
                });
    }

    private void updateLastChatInfo(String uid, String otherId, long timestamp, String encryptedLastMsg, boolean read) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("timestamp", timestamp);
        map.put("lastMessage", encryptedLastMsg);
        map.put("read", read);
        FirebaseDatabase.getInstance().getReference().child("UserChats").child(uid).child(otherId).updateChildren(map);
    }

    private void cancelNotification(Context context, String senderId) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(senderId.hashCode());
        }
    }
}
