package com.smashchat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.smashchat.Utils.PreferenceManager;

/**
 * BaseActivity provides common functionality for all activities,
 * specifically handling the real-time active status tracking.
 */
public class BaseActivity extends AppCompatActivity {

    public static boolean isAppInForeground = false;
    public static String currentChatUserId = null;

    protected PreferenceManager preferenceManager;
    private static final Handler statusHandler = new Handler(Looper.getMainLooper());
    private static Runnable offlineRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        preferenceManager = new PreferenceManager(this);

        // Apply theme globally before super.onCreate and setContentView
        int currentTheme = preferenceManager.getDarkModeTheme();
        switch (currentTheme) {
            case PreferenceManager.THEME_OFF:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case PreferenceManager.THEME_ON:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case PreferenceManager.THEME_SYSTEM:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
        super.onCreate(savedInstanceState);
    }

    private void updateStatus(String status) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            DatabaseReference statusRef = FirebaseDatabase.getInstance().getReference()
                    .child("UserProfiles")
                    .child(uid)
                    .child("status");

            if ("Active".equals(status)) {
                if (preferenceManager.isActiveStatusEnabled()) {
                    statusRef.setValue("Active");
                    statusRef.onDisconnect().setValue("Offline");
                } else {
                    // If status is disabled, force it to show as Offline
                    statusRef.setValue("Offline");
                }
            } else {
                // Always allow updating to "Offline" to ensure accuracy when leaving.
                statusRef.setValue("Offline");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isAppInForeground = true;
        // Cancel any pending offline update since the user is still in the app
        if (offlineRunnable != null) {
            statusHandler.removeCallbacks(offlineRunnable);
            offlineRunnable = null;
        }
        updateStatus("Active");
    }

    @Override
    protected void onPause() {
        super.onPause();
        isAppInForeground = false;
        // Schedule an offline update with a slight delay (2 seconds)
        // This prevents the user from appearing "Offline" during activity transitions
        offlineRunnable = () -> updateStatus("Offline");
        statusHandler.postDelayed(offlineRunnable, 2000);
    }
}
