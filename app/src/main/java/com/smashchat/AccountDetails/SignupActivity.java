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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.smashchat.Models.Users;
import com.smashchat.databinding.ActivitySignupBinding;

import java.util.Objects;

/**
 * SignupActivity handles the user registration process.
 * It ensures a unique User ID starting with '@' and stores user details in Firebase.
 */
public class SignupActivity extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;
    private FirebaseDatabase firebaseDatabase;
    private ActivitySignupBinding binding;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivitySignupBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseDatabase = FirebaseDatabase.getInstance();

        progressDialog = new ProgressDialog(SignupActivity.this);
        progressDialog.setTitle("Creating Account");
        progressDialog.setMessage("We are creating your account. Please wait...");

        binding.signup.setOnClickListener(v -> {
            String nameStr = binding.username.getText().toString().trim();
            String customIdStr = binding.userId.getText().toString().trim();
            String emailStr = binding.email.getText().toString().trim();
            String passStr = binding.password.getText().toString().trim();

            if (nameStr.isEmpty() || customIdStr.isEmpty() || emailStr.isEmpty() || passStr.isEmpty()) {
                Toast.makeText(SignupActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Ensure customId starts with @
            final String finalCustomId = customIdStr.startsWith("@") ? customIdStr : "@" + customIdStr;

            progressDialog.show();

            // Check if User ID is unique
            firebaseDatabase.getReference().child("Users").orderByChild("customId").equalTo(finalCustomId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                progressDialog.dismiss();
                                Toast.makeText(SignupActivity.this, "This User ID is already taken. Try another.", Toast.LENGTH_SHORT).show();
                            } else {
                                // Create account
                                firebaseAuth.createUserWithEmailAndPassword(emailStr, passStr)
                                        .addOnCompleteListener(task -> {
                                            if (task.isSuccessful()) {
                                                String id = Objects.requireNonNull(task.getResult().getUser()).getUid();
                                                saveUserToDatabase(id, nameStr, emailStr, passStr, finalCustomId);
                                            } else {
                                                progressDialog.dismiss();
                                                Toast.makeText(SignupActivity.this, 
                                                        Objects.requireNonNull(task.getException()).getMessage(), 
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            progressDialog.dismiss();
                        }
                    });
        });

        binding.loginLink.setOnClickListener(v -> {
            Intent intent = new Intent(SignupActivity.this, SigninActivity.class);
            startActivity(intent);
        });
    }

    private void saveUserToDatabase(String id, String username, String email, String password, String customId) {
        Users users = new Users(username, email, password);
        users.setUserId(id);
        users.setCustomId(customId);
        users.setProfilePic(""); // Default empty
        
        firebaseDatabase.getReference().child("Users").child(id).setValue(users)
                .addOnCompleteListener(dbTask -> {
                    progressDialog.dismiss();
                    if (dbTask.isSuccessful()) {
                        Toast.makeText(SignupActivity.this, "Account Registered Successfully", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(SignupActivity.this, SigninActivity.class));
                        finish();
                    } else {
                        Toast.makeText(SignupActivity.this, "Database error", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void login(View view) {
        startActivity(new Intent(getApplicationContext(), SigninActivity.class));
    }
}
