package com.smashchat;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.smashchat.Utils.PreferenceManager;

/**
 * BaseActivity provides common functionality for all activities,
 * specifically handling the real-time active status tracking.
 */
public class BaseActivity extends AppCompatActivity {

    protected PreferenceManager preferenceManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferenceManager = new PreferenceManager(this);
    }

    private void updateStatus(String status) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            // Only update to "Active" if the user has enabled Active Status in settings.
            // Always allow updating to "Offline" to ensure accuracy when leaving.
            if (preferenceManager.isActiveStatusEnabled() || status.equals("Offline")) {
                FirebaseDatabase.getInstance().getReference()
                        .child("UserProfiles")
                        .child(uid)
                        .child("status")
                        .setValue(status);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus("Active");
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateStatus("Offline");
    }
}
