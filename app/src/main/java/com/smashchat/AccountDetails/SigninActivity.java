package com.smashchat.AccountDetails;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.smashchat.MainActivity;
import com.smashchat.databinding.ActivitySigninBinding;

import java.util.Objects;

/**
 * SigninActivity handles the user login process.
 * It checks if a user is already logged in and redirects to MainActivity.
 */
public class SigninActivity extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;
    private ActivitySigninBinding binding;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initializing View Binding
        binding = ActivitySigninBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        // Handling Window Insets for Edge-to-Edge display
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initializing Firebase
        firebaseAuth = FirebaseAuth.getInstance();

        // Initializing ProgressDialog (Legacy approach)
        progressDialog = new ProgressDialog(SigninActivity.this);
        progressDialog.setTitle("Login");
        progressDialog.setMessage("Logging into your account. Please wait...");

        // Set up Sign In button click listener
        binding.signin.setOnClickListener(v -> {
            String emailStr = binding.email.getText().toString().trim();
            String passStr = binding.password.getText().toString().trim();

            if (emailStr.isEmpty() || passStr.isEmpty()) {
                Toast.makeText(SigninActivity.this, "Please enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            progressDialog.show();
            
            // Sign in with Firebase Auth
            firebaseAuth.signInWithEmailAndPassword(emailStr, passStr)
                    .addOnCompleteListener(task -> {
                        progressDialog.dismiss();
                        if (task.isSuccessful()) {
                            Toast.makeText(SigninActivity.this, "Logged in Successfully", Toast.LENGTH_SHORT).show();
                            // Navigate to MainActivity
                            Intent intent = new Intent(SigninActivity.this, MainActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            // Display error message
                            Toast.makeText(SigninActivity.this, 
                                    Objects.requireNonNull(task.getException()).getMessage(), 
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // Navigate to Signup activity
        binding.registerLink.setOnClickListener(v -> {
            Intent intent = new Intent(SigninActivity.this, SignupActivity.class);
            startActivity(intent);
        });

        // Check if user is already logged in
        if (firebaseAuth.getCurrentUser() != null) {
            Intent intent = new Intent(SigninActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }

    /**
     * Legacy method for XML onClick. Prefer using ViewBinding listeners.
     */
    public void register(View view) {
        startActivity(new Intent(getApplicationContext(), SignupActivity.class));
    }
}
