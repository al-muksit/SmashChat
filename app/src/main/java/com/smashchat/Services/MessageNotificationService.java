package com.smashchat.Services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.smashchat.BaseActivity;
import com.smashchat.MainActivity;
import com.smashchat.Models.Users;
import com.smashchat.R;
import com.smashchat.Utils.AESalgorithm;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * MessageNotificationService listens for new messages in the background
 * and plays a sound if the user is not currently in the corresponding chat.
 */
public class MessageNotificationService extends Service {

    private static final String TAG = "MessageNotifyService";
    private static final String CHANNEL_ID = "SmashChatMessages_v4";
    private static final String CHANNEL_NAME = "New Messages";
    private static final String SERVICE_CHANNEL_ID = "SmashChatService";
    private static final String SERVICE_CHANNEL_NAME = "Background Service";
    private static final int NOTIFICATION_ID = 101;
    private static final int SERVICE_NOTIFICATION_ID = 102;

    private FirebaseDatabase database;
    private FirebaseAuth auth;
    private MediaPlayer mediaPlayer;
    private PowerManager.WakeLock wakeLock;
    
    // Map to keep track of the last processed message timestamp for each user
    private final Map<String, Long> lastProcessedTimestamps = new HashMap<>();
    private final Map<String, Boolean> mutedUsers = new HashMap<>();
    private boolean isInitialDataLoaded = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service Created");
        database = FirebaseDatabase.getInstance();
        auth = FirebaseAuth.getInstance();
        mediaPlayer = MediaPlayer.create(this, R.raw.messages_sound);
        
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmashChat:NotificationWakeLock");
        }

        createNotificationChannel();
        startForegroundService();
        fetchMuteSettings();
        startListening();
    }

    private void fetchMuteSettings() {
        String uid = auth.getUid();
        if (uid == null) return;

        database.getReference().child("UserProfiles").child(uid).child("MutedUsers")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        mutedUsers.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            Boolean isMuted = ds.getValue(Boolean.class);
                            if (isMuted != null) {
                                mutedUsers.put(ds.getKey(), isMuted);
                            }
                        }
                        Log.d(TAG, "Mute settings updated: " + mutedUsers.size() + " users muted");
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to fetch mute settings: " + error.getMessage());
                    }
                });
    }

    private void startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    SERVICE_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_MIN
            );
            serviceChannel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("SmashChat")
                .setContentText("Checking messages in background")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true);

        startForeground(SERVICE_NOTIFICATION_ID, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Creating Notification Channel");
            Uri soundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getPackageName() + "/" + R.raw.messages_sound);
            
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for new messages in SmashChat");
            channel.setSound(soundUri, audioAttributes);
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startListening() {
        String uid = auth.getUid();
        if (uid == null) return;

        // Using a ValueEventListener for the initial load to populate timestamps
        database.getReference().child("UserChats").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            Long timestamp = ds.child("timestamp").getValue(Long.class);
                            if (timestamp != null) {
                                lastProcessedTimestamps.put(ds.getKey(), timestamp);
                            }
                        }
                        isInitialDataLoaded = true;
                        Log.d(TAG, "Initial data loaded. Ready for notifications.");
                        
                        // Now attach the child event listener for real-time updates
                        attachChildListener(uid);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Initial load failed: " + error.getMessage());
                        attachChildListener(uid); // Try to listen anyway
                    }
                });
    }

    private void attachChildListener(String uid) {
        Log.d(TAG, "Attaching ChildEventListener to UserChats/" + uid);
        database.getReference().child("UserChats").child(uid)
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                        if (isInitialDataLoaded) {
                            processChatUpdate(snapshot);
                        }
                    }

                    @Override
                    public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                        processChatUpdate(snapshot);
                    }

                    @Override
                    public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                        lastProcessedTimestamps.remove(snapshot.getKey());
                    }

                    @Override
                    public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Child listener cancelled: " + error.getMessage());
                    }
                });
    }

    private void processChatUpdate(DataSnapshot snapshot) {
        if (wakeLock != null) wakeLock.acquire(5000); // Increased to 5s

        String senderId = snapshot.getKey();
        Boolean read = snapshot.child("read").getValue(Boolean.class);
        Long timestamp = snapshot.child("timestamp").getValue(Long.class);
        String lastMessageText = snapshot.child("lastMessage").getValue(String.class);

        Log.d(TAG, "Processing Update: sender=" + senderId + ", read=" + read + ", message=" + lastMessageText);

        if (senderId == null || timestamp == null) {
            Log.w(TAG, "Invalid data received");
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            return;
        }

        Long lastTimestamp = lastProcessedTimestamps.get(senderId);
        
        // Notify if it's unread AND the timestamp is newer than what we last saw
        if (read != null && !read && (lastTimestamp == null || timestamp > lastTimestamp)) {
            
            // Check if sender is muted
            Boolean isMuted = mutedUsers.get(senderId);
            if (isMuted != null && isMuted) {
                Log.d(TAG, "Sender is muted. Skipping.");
                lastProcessedTimestamps.put(senderId, timestamp);
            } else {
                Log.d(TAG, "NOTIFICATION TRIGGERED");
                lastProcessedTimestamps.put(senderId, timestamp);
                
                if (!BaseActivity.isAppInForeground) {
                    String myUid = auth.getUid();
                    if (myUid != null) {
                        String sharedSecretKey = (myUid.compareTo(senderId) < 0) ? (myUid + senderId) : (senderId + myUid);
                        String decryptedMessage = AESalgorithm.decrypt(lastMessageText, sharedSecretKey);
                        
                        // User friendly text for special messages
                        if ("[smile]".equals(decryptedMessage)) {
                            decryptedMessage = "sent a smile emoji";
                        }
                        
                        fetchUserAndShowNotification(senderId, decryptedMessage);
                    }
                } else {
                    if (BaseActivity.currentChatUserId == null || !Objects.equals(BaseActivity.currentChatUserId, senderId)) {
                        playNotificationSound();
                    }
                }
            }
        } else {
            // Update timestamp even if it's read, so we stay synced
            lastProcessedTimestamps.put(senderId, timestamp);
        }

        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    private void fetchUserAndShowNotification(String senderId, String message) {
        Log.d(TAG, "Fetching user info for notification: " + senderId);
        database.getReference().child("UserProfiles").child(senderId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Users user = snapshot.getValue(Users.class);
                        if (user != null) {
                            showNotification(user.getUserName(), message, senderId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to fetch user profile: " + error.getMessage());
                    }
                });
    }

    private void showNotification(String title, String message, String senderId) {
        Log.d(TAG, "Displaying system notification for " + title);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri soundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getPackageName() + "/" + R.raw.messages_sound);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setSound(soundUri)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            // Use sender's ID hash to allow multiple notifications from different people
            int id = (senderId != null) ? senderId.hashCode() : NOTIFICATION_ID;
            manager.notify(id, builder.build());
        }
    }

    private void playNotificationSound() {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(0);
            mediaPlayer.start();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "Task Removed. Restarting Service.");
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());
        startService(restartServiceIntent);
        super.onTaskRemoved(rootIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
