package com.smashchat.Services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.graphics.drawable.IconCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.smashchat.ChatActivity;
import com.smashchat.Models.Users;
import com.smashchat.R;
import com.smashchat.Utils.AESalgorithm;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFCMService";
    private static final String CHANNEL_ID = "SmashChatMessages_v5";
    private static final String CHANNEL_NAME = "Messages";
    public static final String KEY_TEXT_REPLY = "key_text_reply";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        
        // look for the data we sent from the server
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();
            String senderId = data.get("senderId");
            String encryptedMessage = data.get("message");

            if (senderId != null && encryptedMessage != null) {
                handlePushMessage(senderId, encryptedMessage);
            }
        }
    }

    private void handlePushMessage(String senderId, String encryptedMessage) {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) return;

        // decrypt the message so we can show it in the notification
        String sharedSecretKey = (myUid.compareTo(senderId) < 0) ? (myUid + senderId) : (senderId + myUid);
        String decrypted = AESalgorithm.decrypt(encryptedMessage, sharedSecretKey);
        
        // replace special tags with friendly text
        if ("[smile]".equals(decrypted)) decrypted = "sent a smile emoji";

        final String finalMessage = decrypted;

        // fetch sender's profile to get their name and picture
        FirebaseDatabase.getInstance().getReference().child("UserProfiles").child(senderId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Users user = snapshot.getValue(Users.class);
                        if (user != null) {
                            user.setUserId(snapshot.getKey());
                            
                            // load the profile pic if they have one
                            if (user.getProfilePic() != null && !user.getProfilePic().isEmpty()) {
                                Picasso.get().load(user.getProfilePic()).into(new Target() {
                                    @Override
                                    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                                        showChatNotification(user, finalMessage, bitmap);
                                    }

                                    @Override
                                    public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                                        showChatNotification(user, finalMessage, null);
                                    }

                                    @Override
                                    public void onPrepareLoad(Drawable placeHolderDrawable) {}
                                });
                            } else {
                                showChatNotification(user, finalMessage, null);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void showChatNotification(Users user, String message, Bitmap avatar) {
        String senderId = user.getUserId();
        String name = user.getUserName();
        Context context = getApplicationContext();

        setupChannel();

        // tap notification -> open chat
        Intent chatIntent = new Intent(context, ChatActivity.class);
        chatIntent.putExtra("userId", senderId);
        chatIntent.putExtra("userName", name);
        chatIntent.putExtra("profilePic", user.getProfilePic());
        chatIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, senderId.hashCode(), chatIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // "Mark as Read" action
        Intent markReadIntent = new Intent(context, NotificationReceiver.class);
        markReadIntent.setAction(NotificationReceiver.ACTION_MARK_AS_READ);
        markReadIntent.putExtra(NotificationReceiver.EXTRA_SENDER_ID, senderId);
        PendingIntent markReadPending = PendingIntent.getBroadcast(
                context, senderId.hashCode() + 1, markReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Quick Reply action
        RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel("Reply...")
                .build();

        Intent replyIntent = new Intent(context, NotificationReceiver.class);
        replyIntent.setAction(NotificationReceiver.ACTION_REPLY);
        replyIntent.putExtra(NotificationReceiver.EXTRA_SENDER_ID, senderId);
        PendingIntent replyPending = PendingIntent.getBroadcast(
                context, senderId.hashCode() + 2, replyIntent,
                PendingIntent.FLAG_MUTABLE
        );

        NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(
                R.drawable.send_btn, "Reply", replyPending)
                .addRemoteInput(remoteInput)
                .build();

        Uri sound = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getPackageName() + "/" + R.raw.messages_sound);

        // build the modern chat style notification
        Person sender = new Person.Builder()
                .setName(name)
                .setIcon(avatar != null ? IconCompat.createWithBitmap(avatar) : null)
                .build();

        NotificationCompat.MessagingStyle style = new NotificationCompat.MessagingStyle(sender)
                .addMessage(message, System.currentTimeMillis(), sender);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(avatar)
                .setStyle(style)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setSound(sound)
                .setAutoCancel(true)
                .setVibrate(new long[]{1000, 1000, 1000})
                .setLights(0xFF00FF00, 3000, 3000)
                .setContentIntent(contentIntent)
                .addAction(R.drawable.profile, "Mark as Read", markReadPending)
                .addAction(replyAction);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(senderId.hashCode(), builder.build());
        }
    }

    private void setupChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri sound = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getPackageName() + "/" + R.raw.messages_sound);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            NotificationChannel chan = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            chan.setSound(sound, attrs);
            chan.enableLights(true);
            chan.enableVibration(true);

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(chan);
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        saveTokenToDatabase(token);
    }

    private void saveTokenToDatabase(String token) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseDatabase.getInstance().getReference()
                    .child("UserProfiles").child(uid).child("fcmToken").setValue(token);
        }
    }
}
