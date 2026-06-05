package com.smashchat.AccountDetails;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.smashchat.Services.BaseActivity;
import com.smashchat.Models.Users;
import com.smashchat.R;
import com.smashchat.Services.ImgBBService;
import com.smashchat.Utils.DatabaseHelper;
import com.smashchat.Utils.HashAlgorithm;
import com.smashchat.databinding.ActivityProfileBinding;
import com.squareup.picasso.Picasso;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ProfileActivity allows users to view and edit their profile information independently.
 * It manages profile picture updates and ensures a unique User ID.
 */
public class ProfileActivity extends BaseActivity {

    private ActivityProfileBinding binding;
    private FirebaseAuth firebaseAuth;
    private FirebaseDatabase firebaseDatabase;
    private DatabaseHelper databaseHelper;
    private ProgressDialog progressDialog;
    private Uri selectedImage;
    private ActivityResultLauncher<String> galleryLauncher;
    private String currentProfilePicUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseDatabase = FirebaseDatabase.getInstance();
        databaseHelper = new DatabaseHelper(this);

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Profile Update");
        progressDialog.setMessage("Updating your information...");

        // Custom Toolbar Setup
        android.widget.ImageView ivBack = binding.toolbar.findViewById(R.id.backArrow);
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }
        
        loadUserData();

        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
                result -> {
                    if (result != null) {
                        binding.profileImage.setImageURI(result);
                        selectedImage = result;
                    }
                });

        binding.profileImage.setOnClickListener(v -> galleryLauncher.launch("image/*"));

        binding.btnSave.setOnClickListener(v -> validateAndUpdate());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.profile_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_logout) {
            showLogoutConfirmationDialog();
            return true;
        } else if (id == R.id.menu_change_email) {
            // Future implementation
            return true;
        } else if (id == R.id.menu_reset_password) {
            showChangePasswordDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        builder.setView(dialogView);

        EditText etCurrentPassword = dialogView.findViewById(R.id.etCurrentPassword);
        EditText etNewPassword = dialogView.findViewById(R.id.etNewPassword);
        MaterialButton btnChange = dialogView.findViewById(R.id.btnChangePassword);

        AlertDialog dialog = builder.create();

        btnChange.setOnClickListener(v -> {
            String currentPassword = etCurrentPassword.getText().toString().trim();
            String newPassword = etNewPassword.getText().toString().trim();

            if (currentPassword.isEmpty() || newPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newPassword.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null && user.getEmail() != null) {
                progressDialog.setMessage("Changing password...");
                progressDialog.show();

                String email = user.getEmail();
                // Standardize: We don't hash before saving to DB, 
                // because Signup/Signin doesn't hash the DB value.
                
                // Re-authenticate user (using the raw password because Firebase Auth 
                // has the raw password, while our DB has the raw one too)
                user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword))
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                user.updatePassword(newPassword).addOnCompleteListener(updateTask -> {
                                    if (updateTask.isSuccessful()) {
                                        // Update the RAW password in our custom Database node too
                                        String uid = firebaseAuth.getUid();
                                        if (uid != null) {
                                            firebaseDatabase.getReference().child("UserProfiles")
                                                    .child(uid).child("password").setValue(newPassword)
                                                    .addOnCompleteListener(dbTask -> {
                                                        progressDialog.dismiss();
                                                        if (dbTask.isSuccessful()) {
                                                            dialog.dismiss();
                                                            Toast.makeText(this, "Password Changed Successfully", Toast.LENGTH_SHORT).show();
                                                            showPasswordChangedSuccessDialog();
                                                        } else {
                                                            Toast.makeText(this, "Changed in Auth but failed in DB", Toast.LENGTH_SHORT).show();
                                                        }
                                                    });
                                        }
                                    } else {
                                        progressDialog.dismiss();
                                        Toast.makeText(this, "Error: " + Objects.requireNonNull(updateTask.getException()).getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } else {
                                progressDialog.dismiss();
                                Toast.makeText(this, "Authentication failed: " + Objects.requireNonNull(task.getException()).getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });

        dialog.show();
    }

    private void showPasswordChangedSuccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Password Changed")
                .setMessage("Your password has been changed successfully.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure want to logout this account?")
                .setPositiveButton("Confirm", (dialog, which) -> {
                    firebaseAuth.signOut();
                    preferenceManager.clear();
                    databaseHelper.clear();
                    Intent intent = new Intent(ProfileActivity.this, SigninActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadUserData() {
        String uid = firebaseAuth.getUid();
        if (uid == null) return;

        // Try to load from SQLite first
        android.graphics.Bitmap localBitmap = databaseHelper.getImage(uid);
        if (localBitmap != null) {
            binding.profileImage.setImageBitmap(localBitmap);
        }

        firebaseDatabase.getReference().child("UserProfiles").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Users user = snapshot.getValue(Users.class);
                        if (user != null) {
                            binding.etUserName.setText(user.getUserName());
                            binding.etEmail.setText(user.getEmail());
                            binding.etPhone.setText(user.getPhone());
                            binding.etAddress.setText(user.getAddress());
                            binding.etCustomId.setText(user.getCustomId());
                            currentProfilePicUrl = user.getProfilePic();

                            // Load and cache if not present or refresh
                            if (currentProfilePicUrl != null && !currentProfilePicUrl.isEmpty()) {
                                Picasso.get().load(currentProfilePicUrl)
                                        .placeholder(R.drawable.profile)
                                        .error(R.drawable.profile)
                                        .into(new com.squareup.picasso.Target() {
                                            @Override
                                            public void onBitmapLoaded(android.graphics.Bitmap bitmap, Picasso.LoadedFrom from) {
                                                binding.profileImage.setImageBitmap(bitmap);
                                                databaseHelper.saveImage(uid, bitmap);
                                            }

                                            @Override
                                            public void onBitmapFailed(Exception e, android.graphics.drawable.Drawable errorDrawable) {
                                                if (localBitmap == null) {
                                                    binding.profileImage.setImageDrawable(errorDrawable);
                                                }
                                            }

                                            @Override
                                            public void onPrepareLoad(android.graphics.drawable.Drawable placeHolderDrawable) {
                                                if (localBitmap == null) {
                                                    binding.profileImage.setImageDrawable(placeHolderDrawable);
                                                }
                                            }
                                        });
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void validateAndUpdate() {
        String customId = binding.etCustomId.getText().toString().trim();
        if (!customId.startsWith("@")) {
            customId = "@" + customId;
        }
        
        final String finalCustomId = customId;
        String uid = firebaseAuth.getUid();

        progressDialog.show();

        // Check uniqueness if User ID was changed
        firebaseDatabase.getReference().child("UserProfiles").orderByChild("customId").equalTo(finalCustomId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean isTaken = false;
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            if (!ds.getKey().equals(uid)) {
                                isTaken = true;
                                break;
                            }
                        }

                        if (isTaken) {
                            progressDialog.dismiss();
                            Toast.makeText(ProfileActivity.this, "This User ID is already taken. Try another.", Toast.LENGTH_SHORT).show();
                        } else {
                            uploadImageAndSave(uid, finalCustomId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                    }
                });
    }

    private void uploadImageAndSave(String uid, String customId) {
        if (selectedImage != null) {
            try {
                android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
                
                // Save locally first
                databaseHelper.saveImage(uid, bitmap);
                
                // Upload to ImgBB for sharing
                ImgBBService.uploadImage(bitmap, new ImgBBService.UploadCallback() {
                    @Override
                    public void onSuccess(String imageUrl) {
                        runOnUiThread(() -> saveToDatabase(uid, customId, imageUrl));
                    }

                    @Override
                    public void onFailure(String error) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(ProfileActivity.this, "Upload failed: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            } catch (java.io.IOException e) {
                progressDialog.dismiss();
                e.printStackTrace();
            }
        } else {
            saveToDatabase(uid, customId, currentProfilePicUrl);
        }
    }

    private void saveToDatabase(String uid, String customId, String imageUrl) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("userName", binding.etUserName.getText().toString().trim());
        updates.put("email", binding.etEmail.getText().toString().trim());
        updates.put("phone", binding.etPhone.getText().toString().trim());
        updates.put("address", binding.etAddress.getText().toString().trim());
        updates.put("customId", customId);
        updates.put("profilePic", imageUrl != null ? imageUrl : "");

        firebaseDatabase.getReference().child("UserProfiles").child(uid).updateChildren(updates)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        preferenceManager.saveUserData(
                                binding.etUserName.getText().toString().trim(),
                                binding.etEmail.getText().toString().trim(),
                                imageUrl
                        );
                        Toast.makeText(ProfileActivity.this, "Profile Updated Successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ProfileActivity.this, "Update failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
