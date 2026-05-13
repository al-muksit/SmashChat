package com.smashchat;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.smashchat.Utils.PreferenceManager;
import com.smashchat.databinding.ActivityActiveStatusBinding;

public class ActiveStatusActivity extends BaseActivity {

    private ActivityActiveStatusBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityActiveStatusBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Toolbar Setup
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Active Status");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.statusSwitch.setChecked(preferenceManager.isActiveStatusEnabled());
        setupSwitchColors();

        binding.statusSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setActiveStatusEnabled(isChecked);
            updateStatusInFirebase(isChecked);
        });
    }

    private void setupSwitchColors() {
        boolean isDarkMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        // --- DEFINING COLORS ---
        
        // Active Colors (When Switch is ON)
        int activeTealThumb = android.graphics.Color.parseColor("#009688");
        int activeTealTrack = android.graphics.Color.parseColor("#80009688"); // Semi-transparent teal

        // Inactive Colors (When Switch is OFF)
        int lightGrey = android.graphics.Color.parseColor("#BDBDBD");
        int darkGrey = android.graphics.Color.parseColor("#757575");
        
        int lightGreyTrack = android.graphics.Color.parseColor("#80BDBDBD");
        int darkGreyTrack = android.graphics.Color.parseColor("#80757575");

        // Select the appropriate grey based on whether Dark Mode is active
        int inactiveGreyThumb = isDarkMode ? lightGrey : darkGrey;
        int inactiveGreyTrack = isDarkMode ? lightGreyTrack : darkGreyTrack;

        // --- CREATING STATE LISTS ---

        // These lists tell the Android system: 
        // "Use color A if checked, otherwise use color B"
        
        int[][] states = new int[][] {
                new int[] { android.R.attr.state_checked }, // State 1: Checked (ON)
                new int[] { }                               // State 2: Default (OFF)
        };

        android.content.res.ColorStateList thumbColorStateList = new android.content.res.ColorStateList(
                states,
                new int[] { activeTealThumb, inactiveGreyThumb }
        );

        android.content.res.ColorStateList trackColorStateList = new android.content.res.ColorStateList(
                states,
                new int[] { activeTealTrack, inactiveGreyTrack }
        );

        // --- APPLYING TINTS ---
        
        // Thumb: The circular part that moves
        binding.statusSwitch.setThumbTintList(thumbColorStateList);
        
        // Track: The background bar the thumb slides on
        binding.statusSwitch.setTrackTintList(trackColorStateList);
    }

    private void updateStatusInFirebase(boolean isEnabled) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            com.google.firebase.database.DatabaseReference statusRef = 
                    FirebaseDatabase.getInstance().getReference().child("UserProfiles").child(uid).child("status");
            
            if (isEnabled) {
                statusRef.setValue("Active");
                statusRef.onDisconnect().setValue("Offline");
            } else {
                statusRef.setValue("Offline");
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}