package com.smashchat.AccountDetails;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.smashchat.Models.Users;
import com.smashchat.databinding.ActivitySignupBinding;

import java.util.Objects;

/**
 * SignupActivity handles the user registration process using Firebase Authentication
 * and stores user details in Firebase Realtime Database and Storage.
 */
public class SignupActivity extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;
    private FirebaseDatabase firebaseDatabase;
    private FirebaseStorage firebaseStorage;
    private ActivitySignupBinding binding;
    private ProgressDialog progressDialog;
    private Uri selectedImage;
    private ActivityResultLauncher<String> galleryLauncher;

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
        firebaseStorage = FirebaseStorage.getInstance();

        progressDialog = new ProgressDialog(SignupActivity.this);
        progressDialog.setTitle("Creating Account");
        progressDialog.setMessage("We are creating your account. Please wait...");

        // Image picker launcher
        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
                result -> {
                    if (result != null) {
                        binding.profileImage.setImageURI(result);
                        selectedImage = result;
                    }
                });

        binding.profileImage.setOnClickListener(v -> galleryLauncher.launch("image/*"));

        binding.signup.setOnClickListener(v -> {
            String userStr = binding.username.getText().toString().trim();
            String emailStr = binding.email.getText().toString().trim();
            String passStr = binding.password.getText().toString().trim();

            if (userStr.isEmpty() || emailStr.isEmpty() || passStr.isEmpty()) {
                Toast.makeText(SignupActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            progressDialog.show();
            
            firebaseAuth.createUserWithEmailAndPassword(emailStr, passStr)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            String id = Objects.requireNonNull(task.getResult().getUser()).getUid();
                            
                            if (selectedImage != null) {
                                // Upload image to Firebase Storage
                                StorageReference reference = firebaseStorage.getReference().child("Profiles").child(id);
                                reference.putFile(selectedImage).addOnCompleteListener(storageTask -> {
                                    if (storageTask.isSuccessful()) {
                                        reference.getDownloadUrl().addOnSuccessListener(uri -> {
                                            String imageUrl = uri.toString();
                                            saveUserToDatabase(id, userStr, emailStr, passStr, imageUrl);
                                        });
                                    } else {
                                        progressDialog.dismiss();
                                        Toast.makeText(SignupActivity.this, "Image upload failed", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } else {
                                saveUserToDatabase(id, userStr, emailStr, passStr, "");
                            }
                        } else {
                            progressDialog.dismiss();
                            Toast.makeText(SignupActivity.this, 
                                    Objects.requireNonNull(task.getException()).getMessage(), 
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        binding.loginLink.setOnClickListener(v -> {
            Intent intent = new Intent(SignupActivity.this, SigninActivity.class);
            startActivity(intent);
        });
    }

    private void saveUserToDatabase(String id, String username, String email, String password, String imageUrl) {
        Users users = new Users(username, email, password);
        users.setUserId(id);
        users.setProfilePic(imageUrl);
        
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
