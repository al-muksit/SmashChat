package com.smashchat.Services;

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

public class BaseActivity extends AppCompatActivity {

    private static int activeActivities = 0;
    public static boolean isAppInForeground = false;
    public static String currentChatUserId = null;

    protected PreferenceManager preferenceManager;
    private static final Handler uiHandler = new Handler(Looper.getMainLooper());
    private static Runnable goOfflineTask;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        preferenceManager = new PreferenceManager(this);

        // set the theme based on user settings before showing anything
        int theme = preferenceManager.getDarkModeTheme();
        switch (theme) {
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

    private void updateOnlineStatus(String status) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            DatabaseReference statusRef = FirebaseDatabase.getInstance().getReference()
                    .child("UserProfiles")
                    .child(uid)
                    .child("status");

            if ("Active".equals(status)) {
                // only show as active if they have it enabled in settings
                if (preferenceManager.isActiveStatusEnabled()) {
                    statusRef.setValue("Active");
                    statusRef.onDisconnect().setValue("Offline");
                } else {
                    statusRef.setValue("Offline");
                }
            } else {
                statusRef.setValue("Offline");
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        activeActivities++;
        isAppInForeground = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        activeActivities--;
        if (activeActivities == 0) {
            isAppInForeground = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // cancel the offline timer since we are back
        if (goOfflineTask != null) {
            uiHandler.removeCallbacks(goOfflineTask);
            goOfflineTask = null;
        }
        updateOnlineStatus("Active");
    }

    @Override
    protected void onPause() {
        super.onPause();
        // wait 2 seconds before going offline to handle activity transitions smoothly
        goOfflineTask = () -> updateOnlineStatus("Offline");
        uiHandler.postDelayed(goOfflineTask, 2000);
    }
}
