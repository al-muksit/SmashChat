package com.smashchat;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.smashchat.databinding.ActivitySettingsBinding;

import androidx.annotation.NonNull;

public class SettingsActivity extends BaseActivity {

    private ActivitySettingsBinding binding;
    private FirebaseDatabase database;
    private String currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        database = FirebaseDatabase.getInstance();
        currentUid = FirebaseAuth.getInstance().getUid();

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Toolbar Setup
        setSupportActionBar(binding.settingsBar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        binding.backArrow.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        binding.btnActiveStatus.setOnClickListener(v -> 
            startActivity(new Intent(SettingsActivity.this, ActiveStatusActivity.class))
        );

        binding.btnDarkMode.setOnClickListener(v -> 
            startActivity(new Intent(SettingsActivity.this, DarkModeActivity.class))
        );

        binding.btnBlockList.setOnClickListener(v -> 
            startActivity(new Intent(SettingsActivity.this, BlockListActivity.class))
        );

        fetchBlockedUsersCount();
    }

    private void fetchBlockedUsersCount() {
        if (currentUid == null) return;

        database.getReference().child("BlockedUsers").child(currentUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            long count = snapshot.getChildrenCount();
                            if (count == 1) {
                                binding.tvBlockListCount.setText("1 user in block list");
                            } else {
                                binding.tvBlockListCount.setText(count + " users in block list");
                            }
                        } else {
                            binding.tvBlockListCount.setText("No one in block list");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}