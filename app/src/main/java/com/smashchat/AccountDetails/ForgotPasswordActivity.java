package com.smashchat.AccountDetails;

import android.app.ProgressDialog;
import android.os.Bundle;
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
import com.smashchat.Utils.EmailValidator;
import com.smashchat.databinding.ActivityForgotPasswordBinding;

public class ForgotPasswordActivity extends BaseActivity {

    private ActivityForgotPasswordBinding binding;
    private FirebaseAuth auth;
    private FirebaseDatabase database;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance();

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Please wait...");
        progressDialog.setCancelable(false);

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.sendLinkBtn.setOnClickListener(v -> {
            String email = binding.emailInput.getText().toString().trim().toLowerCase();
            EmailValidator.validateEmail(email, (isValid, message) -> {
                if (isValid) {
                    checkAccountAndSendResetLink(email);
                } else {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void checkAccountAndSendResetLink(String email) {
        progressDialog.setMessage("Verifying account...");
        progressDialog.show();

        // Check if the user exists in our database before sending the official link
        database.getReference().child("UserProfiles")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean found = false;
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String dbEmail = ds.child("email").getValue(String.class);
                            if (dbEmail != null && dbEmail.trim().equalsIgnoreCase(email)) {
                                found = true;
                                break;
                            }
                        }

                        if (found) {
                            sendOfficialResetEmail(email);
                        } else {
                            progressDialog.dismiss();
                            Toast.makeText(ForgotPasswordActivity.this, "This email is not registered.", Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        Toast.makeText(ForgotPasswordActivity.this, "Database Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void sendOfficialResetEmail(String email) {
        progressDialog.setMessage("Sending reset link...");
        
        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        Toast.makeText(ForgotPasswordActivity.this, "A reset link has been sent to your email.", Toast.LENGTH_LONG).show();
                        finish(); // Return to Login Activity
                    } else {
                        Toast.makeText(ForgotPasswordActivity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}
