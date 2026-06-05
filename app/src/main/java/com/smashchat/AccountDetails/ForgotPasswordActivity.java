package com.smashchat.AccountDetails;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.smashchat.Services.BaseActivity;
import com.smashchat.Services.EmailService;
import com.smashchat.Utils.EmailValidator;
import com.smashchat.Utils.HashAlgorithm;
import com.smashchat.databinding.ActivityForgotPasswordBinding;

import java.util.Random;

public class ForgotPasswordActivity extends BaseActivity {

    private ActivityForgotPasswordBinding binding;
    private FirebaseDatabase database;
    private String generatedCode;
    private String userEmail;
    private CountDownTimer countDownTimer;
    private boolean isTimerRunning = false;
    private android.app.ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Please wait...");
        progressDialog.setCancelable(false);

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        database = FirebaseDatabase.getInstance();

        binding.sendCodeBtn.setOnClickListener(v -> {
            userEmail = binding.emailInput.getText().toString().trim().toLowerCase();
            EmailValidator.validateEmail(userEmail, (isValid, message) -> {
                if (isValid) {
                    checkEmailAndSendCode(userEmail);
                } else {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        binding.verifyBtn.setOnClickListener(v -> {
            String enteredCode = binding.otpView.getText().toString().trim();
            if (enteredCode.length() < 6) {
                Toast.makeText(this, "Please enter the full 6-digit code", Toast.LENGTH_SHORT).show();
                return;
            }
            verifyCode(enteredCode);
        });

        binding.otpView.setOtpCompletionListener(this::verifyCode);

        binding.confirmChangeBtn.setOnClickListener(v -> {
            String newPassword = binding.newPasswordInput.getText().toString().trim();
            if (newPassword.isEmpty()) {
                Toast.makeText(this, "Please enter a new password", Toast.LENGTH_SHORT).show();
                return;
            }
            resetPassword(newPassword);
        });
    }

    private void checkEmailAndSendCode(String email) {
        progressDialog.setMessage("Checking email...");
        progressDialog.show();
        database.getReference().child("UserProfiles")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean found = false;
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String dbEmail = ds.child("email").getValue(String.class);
                            if (dbEmail != null && dbEmail.trim().equalsIgnoreCase(email.trim())) {
                                found = true;
                                break;
                            }
                        }

                        if (found) {
                            generateAndSendCode(email);
                        } else {
                            progressDialog.dismiss();
                            Toast.makeText(ForgotPasswordActivity.this, "This email is not registered yet. Please sign up first.", Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        Toast.makeText(ForgotPasswordActivity.this, "Database Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void generateAndSendCode(String email) {
        Random random = new Random();
        String code = String.format("%06d", random.nextInt(1000000));
        
        progressDialog.setMessage("Sending verification code...");

        EmailService.sendVerificationEmail(email, code, new EmailService.EmailCallback() {
            @Override
            public void onSuccess() {
                progressDialog.dismiss();
                // Save code to database with timestamp
                long expiryTime = System.currentTimeMillis() + (5 * 60 * 1000); // 5 minutes
                database.getReference().child("VerificationCodes").child(email.replace(".", "_"))
                        .child("code").setValue(code);
                database.getReference().child("VerificationCodes").child(email.replace(".", "_"))
                        .child("expiry").setValue(expiryTime);

                Toast.makeText(ForgotPasswordActivity.this, "Verification code sent to " + email, Toast.LENGTH_LONG).show();
                showStep2();
                startTimer();
            }

            @Override
            public void onFailure(String error) {
                progressDialog.dismiss();
                Toast.makeText(ForgotPasswordActivity.this, "Failed to send code: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startTimer() {
        if (isTimerRunning) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(300000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long minutes = (millisUntilFinished / 1000) / 60;
                long seconds = (millisUntilFinished / 1000) % 60;
                binding.timerText.setText(String.format("Time remaining: %02d:%02d", minutes, seconds));
                isTimerRunning = true;
            }

            @Override
            public void onFinish() {
                binding.timerText.setText("Code expired");
                binding.sendCodeBtn.setText("Send Code Again");
                binding.step2Layout.setVisibility(View.GONE);
                binding.step1Layout.setVisibility(View.VISIBLE);
                isTimerRunning = false;
            }
        }.start();
    }

    private void verifyCode(String enteredCode) {
        database.getReference().child("VerificationCodes").child(userEmail.replace(".", "_"))
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String savedCode = snapshot.child("code").getValue(String.class);
                            Long expiry = snapshot.child("expiry").getValue(Long.class);
                            
                            if (savedCode != null && savedCode.equals(enteredCode)) {
                                if (expiry != null && System.currentTimeMillis() < expiry) {
                                    showStep3();
                                    if (countDownTimer != null) countDownTimer.cancel();
                                } else {
                                    Toast.makeText(ForgotPasswordActivity.this, "Code expired. Try again.", Toast.LENGTH_SHORT).show();
                                    showStep1();
                                }
                            } else {
                                lockVerifyButton();
                                Toast.makeText(ForgotPasswordActivity.this, "Invalid code. Try again after 5 minutes.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ForgotPasswordActivity.this, "Database Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void lockVerifyButton() {
        binding.verifyBtn.setEnabled(false);
        new CountDownTimer(300000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long minutes = (millisUntilFinished / 1000) / 60;
                long seconds = (millisUntilFinished / 1000) % 60;
                binding.verifyBtn.setText(String.format("Retry in %02d:%02d", minutes, seconds));
            }

            @Override
            public void onFinish() {
                binding.verifyBtn.setEnabled(true);
                binding.verifyBtn.setText("Verify");
            }
        }.start();
    }

    private void resetPassword(String newPassword) {
        progressDialog.setMessage("Updating password...");
        progressDialog.show();
        // Hash the new password
        String hashedPassword = HashAlgorithm.hashPassword(newPassword, userEmail);

        database.getReference().child("UserProfiles")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean updated = false;
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String dbEmail = ds.child("email").getValue(String.class);
                            if (dbEmail != null && dbEmail.trim().equalsIgnoreCase(userEmail.trim())) {
                                updated = true;
                                ds.getRef().child("password").setValue(hashedPassword)
                                        .addOnCompleteListener(task -> {
                                            progressDialog.dismiss();
                                            if (task.isSuccessful()) {
                                                Toast.makeText(ForgotPasswordActivity.this, "Password updated successfully", Toast.LENGTH_SHORT).show();
                                                finish(); // Go back to login
                                            } else {
                                                Toast.makeText(ForgotPasswordActivity.this, "Failed to update password", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                break;
                            }
                        }

                        if (!updated) {
                            progressDialog.dismiss();
                            Toast.makeText(ForgotPasswordActivity.this, "Error: User record lost. Please try again.", Toast.LENGTH_SHORT).show();
                        }

                        // Remove the verification code
                        database.getReference().child("VerificationCodes").child(userEmail.replace(".", "_")).removeValue();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        Toast.makeText(ForgotPasswordActivity.this, "Database Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showStep1() {
        binding.step1Layout.setVisibility(View.VISIBLE);
        binding.step2Layout.setVisibility(View.GONE);
        binding.step3Layout.setVisibility(View.GONE);
    }

    private void showStep2() {
        binding.step1Layout.setVisibility(View.GONE);
        binding.step2Layout.setVisibility(View.VISIBLE);
        binding.step3Layout.setVisibility(View.GONE);
    }

    private void showStep3() {
        binding.step1Layout.setVisibility(View.GONE);
        binding.step2Layout.setVisibility(View.GONE);
        binding.step3Layout.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
