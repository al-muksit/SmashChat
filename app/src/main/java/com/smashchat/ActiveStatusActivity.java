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

public class ActiveStatusActivity extends AppCompatActivity {

    private ActivityActiveStatusBinding binding;
    private PreferenceManager preferenceManager;

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

        preferenceManager = new PreferenceManager(this);

        // Toolbar Setup
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Active Status");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.statusSwitch.setChecked(preferenceManager.isActiveStatusEnabled());

        binding.statusSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setActiveStatusEnabled(isChecked);
            updateStatusInFirebase(isChecked);
        });
    }

    private void updateStatusInFirebase(boolean isEnabled) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            String status = isEnabled ? "Active" : "Offline";
            FirebaseDatabase.getInstance().getReference().child("UserProfiles").child(uid).child("status").setValue(status);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}