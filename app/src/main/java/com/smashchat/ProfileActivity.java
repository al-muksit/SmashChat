package com.smashchat;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.smashchat.AccountDetails.SigninActivity;
import com.smashchat.Models.Users;
import com.smashchat.databinding.ActivityProfileBinding;
import com.squareup.picasso.Picasso;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ProfileActivity allows users to view and edit their profile information,
 * including profile picture, name, phone, and address.
 */
public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private FirebaseAuth firebaseAuth;
    private FirebaseDatabase firebaseDatabase;
    private FirebaseStorage firebaseStorage;
    private ProgressDialog progressDialog;
    private Uri selectedImage;
    private ActivityResultLauncher<String> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseDatabase = FirebaseDatabase.getInstance();
        firebaseStorage = FirebaseStorage.getInstance();

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Profile Update");
        progressDialog.setMessage("Updating your information...");

        // Load existing user data
        loadUserData();

        // Image picker
        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
                result -> {
                    if (result != null) {
                        binding.profileImage.setImageURI(result);
                        selectedImage = result;
                    }
                });

        binding.profileImage.setOnClickListener(v -> galleryLauncher.launch("image/*"));

        // Save button
        binding.btnSave.setOnClickListener(v -> updateProfile());

        // Logout button
        binding.btnLogout.setOnClickListener(v -> {
            firebaseAuth.signOut();
            Intent intent = new Intent(ProfileActivity.this, SigninActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    private void loadUserData() {
        String uid = firebaseAuth.getUid();
        if (uid == null) return;

        firebaseDatabase.getReference().child("Users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Users user = snapshot.getValue(Users.class);
                        if (user != null) {
                            binding.etUserName.setText(user.getUserName());
                            binding.etEmail.setText(user.getEmail());
                            binding.etPhone.setText(user.getPhone());
                            binding.etAddress.setText(user.getAddress());
                            
                            if (user.getProfilePic() != null && !user.getProfilePic().isEmpty()) {
                                Picasso.get().load(user.getProfilePic())
                                        .placeholder(R.drawable.profile)
                                        .into(binding.profileImage);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void updateProfile() {
        String name = binding.etUserName.getText().toString().trim();
        String phone = binding.etPhone.getText().toString().trim();
        String address = binding.etAddress.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog.show();
        String uid = firebaseAuth.getUid();

        if (selectedImage != null) {
            // Upload new image first
            StorageReference reference = firebaseStorage.getReference().child("Profiles").child(uid);
            reference.putFile(selectedImage).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    reference.getDownloadUrl().addOnSuccessListener(uri -> {
                        saveToDatabase(uid, name, phone, address, uri.toString());
                    });
                } else {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            saveToDatabase(uid, name, phone, address, null);
        }
    }

    private void saveToDatabase(String uid, String name, String phone, String address, String imageUrl) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("userName", name);
        updates.put("phone", phone);
        updates.put("address", address);
        if (imageUrl != null) {
            updates.put("profilePic", imageUrl);
        }

        firebaseDatabase.getReference().child("Users").child(uid).updateChildren(updates)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        Toast.makeText(ProfileActivity.this, "Profile Updated", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ProfileActivity.this, "Update failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
