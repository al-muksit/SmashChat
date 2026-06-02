package com.smashchat.AccountDetails;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.smashchat.Services.BaseActivity;
import com.smashchat.MainActivity;
import com.smashchat.Utils.HashAlgorithm;
import com.smashchat.databinding.ActivitySigninBinding;

import java.util.Objects;

public class SigninActivity extends BaseActivity {

    private FirebaseAuth auth;
    private ActivitySigninBinding binding;
    private ProgressDialog loadingBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySigninBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        // make it look good on screens with cutouts/notches
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, keyboard.bottom));
            return insets;
        });

        auth = FirebaseAuth.getInstance();

        loadingBar = new ProgressDialog(this);
        loadingBar.setTitle("Login");
        loadingBar.setMessage("Signing you in, please wait...");

        binding.signin.setOnClickListener(v -> {
            String email = binding.email.getText().toString().trim();
            String pass = binding.password.getText().toString().trim();

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Type in your email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            loadingBar.show();
            
            // hash the password before sending to firebase
            String hashed = HashAlgorithm.hashPassword(pass, email);
            
            auth.signInWithEmailAndPassword(email, hashed)
                    .addOnCompleteListener(task -> {
                        loadingBar.dismiss();
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(this, MainActivity.class));
                            finish();
                        } else {
                            Toast.makeText(this, Objects.requireNonNull(task.getException()).getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        binding.registerLink.setOnClickListener(v -> {
            startActivity(new Intent(this, SignupActivity.class));
        });

        binding.forgotPassword.setOnClickListener(v -> {
            startActivity(new Intent(this, ForgotPasswordActivity.class));
        });

        // go straight to home if already logged in
        if (auth.getCurrentUser() != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }

    // old school way for buttons in xml
    public void register(View view) {
        startActivity(new Intent(this, SignupActivity.class));
    }
}
