package com.smashchat.AccountDetails;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.smashchat.Services.BaseActivity;
import com.smashchat.Models.Users;
import com.smashchat.Services.EmailService;
import com.smashchat.Utils.EmailValidator;
import com.smashchat.Utils.HashAlgorithm;
import com.smashchat.databinding.ActivitySignupBinding;

import java.util.Objects;
import java.util.Random;

/**
 * SignupActivity handles the user registration process.
 * It ensures a unique User ID starting with '@' and stores user details in Firebase.
 */
public class SignupActivity extends BaseActivity {

    private FirebaseAuth firebaseAuth;
    private FirebaseDatabase firebaseDatabase;
    private ActivitySignupBinding binding;
    private ProgressDialog progressDialog;
    
    private String nameStr, customIdStr, emailStr, passStr;
    private String generatedCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivitySignupBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            
            // Apply top and side padding from system bars
            // We use the maximum of systemBars.bottom and ime.bottom to ensure 
            // the layout moves up for both the navigation bar and the keyboard.
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, ime.bottom));

            return insets;
        });

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseDatabase = FirebaseDatabase.getInstance();

        progressDialog = new ProgressDialog(SignupActivity.this);
        progressDialog.setTitle("Creating Account");
        progressDialog.setMessage("We are creating your account. Please wait...");

        binding.signup.setOnClickListener(v -> {
            nameStr = binding.username.getText().toString().trim();
            customIdStr = binding.userId.getText().toString().trim();
            emailStr = binding.email.getText().toString().trim().toLowerCase();
            passStr = binding.password.getText().toString().trim();

            if (nameStr.isEmpty() || customIdStr.isEmpty() || emailStr.isEmpty() || passStr.isEmpty()) {
                Toast.makeText(SignupActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Real-world email validation
            EmailValidator.validateEmail(emailStr, (isValid, message) -> {
                if (isValid) {
                    checkUserAvailability();
                } else {
                    Toast.makeText(SignupActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        binding.otpContainer.verifySignupBtn.setOnClickListener(v -> {
            String enteredCode = binding.otpContainer.otpViewSignup.getText().toString().trim();
            if (enteredCode.length() < 6) {
                Toast.makeText(this, "Please enter the full 6-digit code", Toast.LENGTH_SHORT).show();
                return;
            }
            verifySignupCode(enteredCode);
        });

        binding.otpContainer.otpViewSignup.setOtpCompletionListener(this::verifySignupCode);

        binding.loginLink.setOnClickListener(v -> {
            Intent intent = new Intent(SignupActivity.this, SigninActivity.class);
            startActivity(intent);
        });
    }

    private void checkUserAvailability() {
        final String finalCustomId = customIdStr.startsWith("@") ? customIdStr : "@" + customIdStr;
        progressDialog.show();

        firebaseDatabase.getReference().child("UserProfiles").orderByChild("customId").equalTo(finalCustomId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            progressDialog.dismiss();
                            Toast.makeText(SignupActivity.this, "This User ID is already taken", Toast.LENGTH_SHORT).show();
                        } else {
                            // Also check if email exists already
                            firebaseDatabase.getReference().child("UserProfiles").orderByChild("email").equalTo(emailStr)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot emailSnapshot) {
                                            progressDialog.dismiss();
                                            if (emailSnapshot.exists()) {
                                                Toast.makeText(SignupActivity.this, "Email already registered", Toast.LENGTH_SHORT).show();
                                            } else {
                                                sendVerificationCode();
                                            }
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                            progressDialog.dismiss();
                                            Toast.makeText(SignupActivity.this, "Database Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        Toast.makeText(SignupActivity.this, "Database Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void sendVerificationCode() {
        Random random = new Random();
        generatedCode = String.format("%06d", random.nextInt(1000000));
        
        progressDialog.setMessage("Sending verification code...");
        progressDialog.show();

        EmailService.sendVerificationEmail(emailStr, generatedCode, new EmailService.EmailCallback() {
            @Override
            public void onSuccess() {
                progressDialog.dismiss();
                // Save to DB temporarily
                long expiry = System.currentTimeMillis() + (5 * 60 * 1000);
                firebaseDatabase.getReference().child("SignupCodes").child(emailStr.replace(".", "_"))
                        .child("code").setValue(generatedCode);
                firebaseDatabase.getReference().child("SignupCodes").child(emailStr.replace(".", "_"))
                        .child("expiry").setValue(expiry);

                Toast.makeText(SignupActivity.this, "Verification code sent to " + emailStr, Toast.LENGTH_LONG).show();
                
                binding.signupForm.setVisibility(View.GONE);
                binding.otpContainer.otpLayout.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure(String error) {
                progressDialog.dismiss();
                Toast.makeText(SignupActivity.this, "Failed to send code: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void verifySignupCode(String code) {
        firebaseDatabase.getReference().child("SignupCodes").child(emailStr.replace(".", "_"))
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String savedCode = snapshot.child("code").getValue(String.class);
                            Long expiry = snapshot.child("expiry").getValue(Long.class);

                            if (savedCode != null && savedCode.equals(code)) {
                                if (expiry != null && System.currentTimeMillis() < expiry) {
                                    createNewUser();
                                } else {
                                    Toast.makeText(SignupActivity.this, "Code expired", Toast.LENGTH_SHORT).show();
                                    binding.otpContainer.otpLayout.setVisibility(View.GONE);
                                    binding.signupForm.setVisibility(View.VISIBLE);
                                }
                            } else {
                                Toast.makeText(SignupActivity.this, "Invalid code", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(SignupActivity.this, "Database Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void createNewUser() {
        progressDialog.show();
        String hashedPassword = HashAlgorithm.hashPassword(passStr, emailStr);
        final String finalCustomId = customIdStr.startsWith("@") ? customIdStr : "@" + customIdStr;

        firebaseAuth.createUserWithEmailAndPassword(emailStr, hashedPassword)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String id = Objects.requireNonNull(task.getResult().getUser()).getUid();
                        saveUserToDatabase(id, nameStr, emailStr, hashedPassword, finalCustomId);
                        firebaseDatabase.getReference().child("SignupCodes").child(emailStr.replace(".", "_")).removeValue();
                    } else {
                        progressDialog.dismiss();
                        Toast.makeText(SignupActivity.this, Objects.requireNonNull(task.getException()).getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveUserToDatabase(String id, String username, String email, String password, String customId) {
        Users users = new Users(username, email, password);
        users.setUserId(id);
        users.setCustomId(customId);
        users.setProfilePic(""); // Default empty
        
        firebaseDatabase.getReference().child("UserProfiles").child(id).setValue(users)
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
