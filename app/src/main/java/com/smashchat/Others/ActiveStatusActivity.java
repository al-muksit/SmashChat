package com.smashchat.Others;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.smashchat.R;
import com.smashchat.Services.BaseActivity;
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

        // Custom Toolbar Setup
        android.widget.ImageView ivBack = binding.toolbar.findViewById(R.id.backArrow);
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }

        binding.statusSwitch.setChecked(preferenceManager.isActiveStatusEnabled());
        
        // Apply initial colors
        applySwitchColorTheme(binding.statusSwitch.isChecked());

        binding.statusSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setActiveStatusEnabled(isChecked);
            updateStatusInFirebase(isChecked);
            // Force color update on every click
            applySwitchColorTheme(isChecked);
        });
    }

    private void applySwitchColorTheme(boolean isChecked) {
        boolean isDarkMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        // --- DEFINE MEANINGFUL COLOR VARIABLES ---
        
        // ON State Colors (Teal)
        int colorTealThumbOn = android.graphics.Color.parseColor("#009688");
        int colorTealTrackOn = android.graphics.Color.parseColor("#80009688");

        // OFF State Colors (Grey)
        int colorLightGrey = android.graphics.Color.parseColor("#BDBDBD");
        int colorDarkGrey = android.graphics.Color.parseColor("#757575");
        
        int colorLightGreyTrack = android.graphics.Color.parseColor("#80BDBDBD");
        int colorDarkGreyTrack = android.graphics.Color.parseColor("#80757575");

        // --- SELECT COLORS BASED ON STATE ---
        
        int finalThumbColor;
        int finalTrackColor;

        if (isChecked) {
            finalThumbColor = colorTealThumbOn;
            finalTrackColor = colorTealTrackOn;
        } else {
            // Select grey shade based on Dark Mode
            finalThumbColor = isDarkMode ? colorLightGrey : colorDarkGrey;
            finalTrackColor = isDarkMode ? colorLightGreyTrack : colorDarkGreyTrack;
        }

        // --- APPLY COLORS ---
        
        // We set the PorterDuff mode to SRC_IN to ensure the color overrides any drawable defaults
        binding.statusSwitch.setThumbTintMode(android.graphics.PorterDuff.Mode.SRC_IN);
        binding.statusSwitch.setTrackTintMode(android.graphics.PorterDuff.Mode.SRC_IN);

        binding.statusSwitch.setThumbTintList(android.content.res.ColorStateList.valueOf(finalThumbColor));
        binding.statusSwitch.setTrackTintList(android.content.res.ColorStateList.valueOf(finalTrackColor));
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