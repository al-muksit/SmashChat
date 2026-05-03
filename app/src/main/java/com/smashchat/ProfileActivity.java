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
import com.smashchat.Utils.DatabaseHelper;
import com.smashchat.Utils.PreferenceManager;
import com.smashchat.databinding.ActivityProfileBinding;
import com.squareup.picasso.Picasso;

import java.util.HashMap;
import java.util.Map;

/**
 * ProfileActivity allows users to view and edit their profile information independently.
 * It manages profile picture updates and ensures a unique User ID.
 */
public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private FirebaseAuth firebaseAuth;
    private FirebaseDatabase firebaseDatabase;
    private FirebaseStorage firebaseStorage;
    private PreferenceManager preferenceManager;
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

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseDatabase = FirebaseDatabase.getInstance();
        firebaseStorage = FirebaseStorage.getInstance();
        preferenceManager = new PreferenceManager(this);
        databaseHelper = new DatabaseHelper(this);

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Profile Update");
        progressDialog.setMessage("Updating your information...");
        
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

        binding.btnLogout.setOnClickListener(v -> {
            firebaseAuth.signOut();
            preferenceManager.clear();
            databaseHelper.clear();
            Intent intent = new Intent(ProfileActivity.this, SigninActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    private void loadUserData() {
        String uid = firebaseAuth.getUid();
        if (uid == null) return;

        // Try to load from SQLite first
        android.graphics.Bitmap localBitmap = databaseHelper.getImage(uid);
        if (localBitmap != null) {
            binding.profileImage.setImageBitmap(localBitmap);
        }

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
                            binding.etCustomId.setText(user.getCustomId());
                            currentProfilePicUrl = user.getProfilePic();

                            // Load from Firebase if not in SQLite
                            if (localBitmap == null && user.getProfilePic() != null && !user.getProfilePic().isEmpty()) {
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

    private void validateAndUpdate() {
        String customId = binding.etCustomId.getText().toString().trim();
        if (!customId.startsWith("@")) {
            customId = "@" + customId;
        }
        
        final String finalCustomId = customId;
        String uid = firebaseAuth.getUid();

        progressDialog.show();

        // Check uniqueness if User ID was changed
        firebaseDatabase.getReference().child("Users").orderByChild("customId").equalTo(finalCustomId)
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
            // Save to SQLite
            try {
                android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
                databaseHelper.saveImage(uid, bitmap);
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }

            StorageReference reference = firebaseStorage.getReference().child("Profiles").child(uid);
            reference.putFile(selectedImage).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    reference.getDownloadUrl().addOnSuccessListener(uri -> {
                        saveToDatabase(uid, customId, uri.toString());
                    });
                } else {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            saveToDatabase(uid, customId, currentProfilePicUrl);
        }
    }

    private void saveToDatabase(String uid, String customId, String imageUrl) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("userName", binding.etUserName.getText().toString().trim());
        updates.put("phone", binding.etPhone.getText().toString().trim());
        updates.put("address", binding.etAddress.getText().toString().trim());
        updates.put("customId", customId);
        updates.put("profilePic", imageUrl);

        firebaseDatabase.getReference().child("Users").child(uid).updateChildren(updates)
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
