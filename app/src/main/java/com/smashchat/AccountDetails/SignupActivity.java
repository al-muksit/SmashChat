package com.smashchat.AccountDetails;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.smashchat.Models.Users;
import com.smashchat.databinding.ActivitySignupBinding;

import java.util.Objects;

/**
 * SignupActivity handles the user registration process using Firebase Authentication
 * and stores user details in Firebase Realtime Database.
 */
public class SignupActivity extends AppCompatActivity {

    // Firebase instances
    private FirebaseAuth firebaseAuth;
    private FirebaseDatabase firebaseDatabase;

    // View Binding instance
    private ActivitySignupBinding binding;

    // Deprecated ProgressDialog - Consider replacing with a ProgressBar in layout for better UX
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initializing View Binding
        binding = ActivitySignupBinding.inflate(getLayoutInflater());
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
        firebaseDatabase = FirebaseDatabase.getInstance();

        // Initializing ProgressDialog (Legacy approach)
        progressDialog = new ProgressDialog(SignupActivity.this);
        progressDialog.setTitle("Creating Account");
        progressDialog.setMessage("We are creating your account. Please wait...");

        // Set up Sign Up button click listener
        binding.signup.setOnClickListener(v -> {
            String userStr = binding.username.getText().toString().trim();
            String emailStr = binding.email.getText().toString().trim();
            String passStr = binding.password.getText().toString().trim();

            if (userStr.isEmpty() || emailStr.isEmpty() || passStr.isEmpty()) {
                Toast.makeText(SignupActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            progressDialog.show();
            
            // Create user with Firebase Auth
            firebaseAuth.createUserWithEmailAndPassword(emailStr, passStr)
                    .addOnCompleteListener(task -> {
                        progressDialog.dismiss();
                        if (task.isSuccessful()) {
                            // User created successfully, now store additional info in Database
                            Users users = new Users(userStr, emailStr, passStr);
                            String id = Objects.requireNonNull(task.getResult().getUser()).getUid();
                            
                            firebaseDatabase.getReference().child("Users").child(id).setValue(users)
                                    .addOnCompleteListener(dbTask -> {
                                        if (dbTask.isSuccessful()) {
                                            Toast.makeText(SignupActivity.this, "Account Registered Successfully", Toast.LENGTH_SHORT).show();
                                            // Navigate to Sign-in screen
                                            Intent intent = new Intent(SignupActivity.this, SigninActivity.class);
                                            startActivity(intent);
                                            finish();
                                        }
                                    });
                        } else {
                            // Display error message from Firebase
                            Toast.makeText(SignupActivity.this, 
                                    Objects.requireNonNull(task.getException()).getMessage(), 
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // Navigate to Login activity if user already has an account
        binding.loginLink.setOnClickListener(v -> {
            Intent intent = new Intent(SignupActivity.this, SigninActivity.class);
            startActivity(intent);
        });
    }

    // This method is used by android:onClick in XML if not using ViewBinding click listeners
    public void login(View view) {
        startActivity(new Intent(getApplicationContext(), SigninActivity.class));
    }
}
