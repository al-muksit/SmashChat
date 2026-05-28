package com.smashchat.Services;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.app.RemoteInput;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.smashchat.Utils.AESalgorithm;

import java.util.Date;
import java.util.HashMap;

public class NotificationReceiver extends BroadcastReceiver {

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
            // just mark it as read in firebase and dismiss the notification
            markChatAsRead(senderId, myUid);
            dismissNotification(context, senderId);
        } else if (ACTION_REPLY.equals(action)) {
            // handle the quick reply from the notification tray
            handleQuickReply(context, intent, senderId, myUid);
        }
    }

    private void markChatAsRead(String senderId, String myUid) {
        FirebaseDatabase.getInstance().getReference()
                .child("UserChats").child(myUid).child(senderId)
                .child("read").setValue(true);
    }

    private void handleQuickReply(Context context, Intent intent, String senderId, String myUid) {
        Bundle results = RemoteInput.getResultsFromIntent(intent);
        if (results != null) {
            CharSequence reply = results.getCharSequence(MyFirebaseMessagingService.KEY_TEXT_REPLY);
            if (reply != null) {
                sendTheMessage(senderId, myUid, reply.toString());
                // clear the notification once we reply
                markChatAsRead(senderId, myUid);
                dismissNotification(context, senderId);
            }
        }
    }

    private void sendTheMessage(String receiverId, String senderId, String text) {
        long now = new Date().getTime();
        String key = (senderId.compareTo(receiverId) < 0) ? (senderId + receiverId) : (receiverId + senderId);
        
        String encrypted = AESalgorithm.encrypt(text, key);
        
        HashMap<String, Object> msg = new HashMap<>();
        msg.put("uId", senderId);
        msg.put("message", encrypted);
        msg.put("timestamp", now);
        msg.put("type", 0);

        String room1 = senderId + receiverId;
        String room2 = receiverId + senderId;

        FirebaseDatabase db = FirebaseDatabase.getInstance();
        db.getReference().child("Messages").child(room1).push().setValue(msg)
                .addOnSuccessListener(unused -> {
                    db.getReference().child("Messages").child(room2).push().setValue(msg)
                            .addOnSuccessListener(unused1 -> {
                                updateLastMsgInfo(senderId, receiverId, now, encrypted, true);
                                updateLastMsgInfo(receiverId, senderId, now, encrypted, false);
                            });
                });
    }

    private void updateLastMsgInfo(String uid, String otherId, long time, String lastMsg, boolean read) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("timestamp", time);
        map.put("lastMessage", lastMsg);
        map.put("read", read);
        FirebaseDatabase.getInstance().getReference().child("UserChats").child(uid).child(otherId).updateChildren(map);
    }

    private void dismissNotification(Context context, String senderId) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(senderId.hashCode());
    }
}
