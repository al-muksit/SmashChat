package com.smashchat;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.smashchat.databinding.ActivityOtherUserProfileBinding;
import com.squareup.picasso.Picasso;

/**
 * OtherUserProfileActivity displays the profile details of another user.
 */
public class OtherUserProfileActivity extends AppCompatActivity {

    private ActivityOtherUserProfileBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivityOtherUserProfileBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Get data from intent
        String name = getIntent().getStringExtra("userName");
        String email = getIntent().getStringExtra("email");
        String phone = getIntent().getStringExtra("phone");
        String address = getIntent().getStringExtra("address");
        String profilePic = getIntent().getStringExtra("profilePic");

        // Display data
        binding.tvUserName.setText(name);
        binding.tvEmail.setText(email);
        binding.tvPhone.setText(phone != null && !phone.isEmpty() ? phone : "No phone number");
        binding.tvAddress.setText(address != null && !address.isEmpty() ? address : "No address provided");

        if (profilePic != null && !profilePic.isEmpty()) {
            Picasso.get().load(profilePic)
                    .placeholder(R.drawable.profile)
                    .into(binding.profileImage);
        }

        binding.btnMessage.setOnClickListener(v -> {
            Toast.makeText(this, "Messaging feature coming soon!", Toast.LENGTH_SHORT).show();
        });
    }
}
