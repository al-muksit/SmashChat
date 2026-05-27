package com.smashchat.Others;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.smashchat.R;
import com.smashchat.Services.BaseActivity;
import com.smashchat.Utils.PreferenceManager;
import com.smashchat.databinding.ActivityDarkModeBinding;

public class DarkModeActivity extends BaseActivity {

    private ActivityDarkModeBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityDarkModeBinding.inflate(getLayoutInflater());
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

        updateUI();

        binding.btnOn.setOnClickListener(v -> applyTheme(PreferenceManager.THEME_ON));
        binding.btnOff.setOnClickListener(v -> applyTheme(PreferenceManager.THEME_OFF));
        binding.btnSystem.setOnClickListener(v -> applyTheme(PreferenceManager.THEME_SYSTEM));
    }

    private void updateUI() {
        int currentTheme = preferenceManager.getDarkModeTheme();
        
        binding.checkOn.setVisibility(currentTheme == PreferenceManager.THEME_ON ? View.VISIBLE : View.GONE);
        binding.checkOff.setVisibility(currentTheme == PreferenceManager.THEME_OFF ? View.VISIBLE : View.GONE);
        binding.checkSystem.setVisibility(currentTheme == PreferenceManager.THEME_SYSTEM ? View.VISIBLE : View.GONE);
    }

    private void applyTheme(int mode) {
        preferenceManager.setDarkModeTheme(mode);
        updateUI();

        switch (mode) {
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
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}