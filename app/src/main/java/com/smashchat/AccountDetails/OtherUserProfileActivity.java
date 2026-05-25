package com.smashchat.AccountDetails;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.smashchat.BaseActivity;
import com.smashchat.ChatActivity;
import com.smashchat.databinding.ActivityOtherUserProfileBinding;
import com.squareup.picasso.Picasso;
import com.smashchat.R;

/**
 * OtherUserProfileActivity displays the profile details of another user.
 */
public class OtherUserProfileActivity extends BaseActivity {

    private ActivityOtherUserProfileBinding binding;
    private com.smashchat.Utils.DatabaseHelper databaseHelper;

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

        databaseHelper = new com.smashchat.Utils.DatabaseHelper(this);

        // Get data from intent
        String userId = getIntent().getStringExtra("userId");
        String name = getIntent().getStringExtra("userName");
        String email = getIntent().getStringExtra("email");
        String phone = getIntent().getStringExtra("phone");
        String address = getIntent().getStringExtra("address");
        String profilePic = getIntent().getStringExtra("profilePic");
        String customId = getIntent().getStringExtra("customId");

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Set dynamic user name in the toolbar TextView
        binding.toolbarUserName.setText(name != null ? name : "User Profile");
        
        // Handle back button click
        binding.backArrow.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // Display body data
        binding.tvUserName.setText(name);
        binding.tvEmail.setText(email);
        binding.tvPhone.setText(phone != null && !phone.isEmpty() ? phone : "No phone number");
        binding.tvAddress.setText(address != null && !address.isEmpty() ? address : "No address provided");
        binding.tvCustomId.setText(customId != null && !customId.isEmpty() ? customId : "No ID provided");

        // Try loading from local database first
        android.graphics.Bitmap localBitmap = databaseHelper.getImage(userId);
        if (localBitmap != null) {
            binding.profileImage.setImageBitmap(localBitmap);
        } else if (profilePic != null && !profilePic.isEmpty()) {
            Picasso.get().load(profilePic)
                    .placeholder(R.drawable.profile)
                    .error(R.drawable.profile)
                    .into(new com.squareup.picasso.Target() {
                        @Override
                        public void onBitmapLoaded(android.graphics.Bitmap bitmap, Picasso.LoadedFrom from) {
                            binding.profileImage.setImageBitmap(bitmap);
                            databaseHelper.saveImage(userId, bitmap);
                        }

                        @Override
                        public void onBitmapFailed(Exception e, android.graphics.drawable.Drawable errorDrawable) {
                            binding.profileImage.setImageDrawable(errorDrawable);
                        }

                        @Override
                        public void onPrepareLoad(android.graphics.drawable.Drawable placeHolderDrawable) {
                            binding.profileImage.setImageDrawable(placeHolderDrawable);
                        }
                    });
        } else {
            binding.profileImage.setImageResource(R.drawable.profile);
        }

        binding.btnMessage.setOnClickListener(v -> {
            Intent chatIntent = new Intent(OtherUserProfileActivity.this, ChatActivity.class);
            chatIntent.putExtra("userId", getIntent().getStringExtra("userId"));
            chatIntent.putExtra("userName", name);
            chatIntent.putExtra("profilePic", profilePic);
            startActivity(chatIntent);
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
