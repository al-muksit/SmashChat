package com.smashchat;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.smashchat.Adapter.ChatAdapter;
import com.smashchat.Models.Messages;
import com.smashchat.Models.Users;
import com.smashchat.databinding.ActivityChatBinding;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;

/**
 * ChatActivity handles the one-to-one messaging logic between two users.
 */
public class ChatActivity extends AppCompatActivity {

    private ActivityChatBinding binding;
    private FirebaseDatabase database;
    private FirebaseAuth auth;
    private String senderId;
    private String receiverId;
    private String senderRoom;
    private String receiverIdRoom;
    private ArrayList<Messages> messageList;
    private ChatAdapter chatAdapter;
    private Users receiverUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            
            // 1. Maintain top/side padding for the system bars (status bar, etc.)
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            
            // 2. Calculate Base Height (80sp) in pixels
            float baseHeightSp = 80f;
            int baseHeightPx = (int) android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_SP, baseHeightSp, getResources().getDisplayMetrics());
            
            // 3. Calculate Keyboard Height (accounting for the navigation bar)
            int keyboardHeight = ime.bottom - systemBars.bottom;
            if (keyboardHeight < 0) keyboardHeight = 0;
            
            // 4. Add extra offset (15sp) when keyboard is visible
            int extraOffsetPx = 0;
            if (keyboardHeight > 0) {
                float extraOffsetSp = 15f;
                extraOffsetPx = (int) android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_SP, extraOffsetSp, getResources().getDisplayMetrics());
            }
            
            // 5. Apply formula: New Height = 80sp + Keyboard Height + Extra Offset
            android.view.ViewGroup.LayoutParams params = binding.linearLayout.getLayoutParams();
            params.height = baseHeightPx + keyboardHeight + extraOffsetPx;
            binding.linearLayout.setLayoutParams(params);

            return insets;
        });

        database = FirebaseDatabase.getInstance();
        auth = FirebaseAuth.getInstance();

        senderId = auth.getUid();
        receiverId = getIntent().getStringExtra("userId");
        String userName = getIntent().getStringExtra("userName");
        String profilePic = getIntent().getStringExtra("profilePic");

        if (receiverId == null) {
            Toast.makeText(this, "User error", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Custom Toolbar Setup
        View toolbarView = binding.toolbar.findViewById(R.id.chat_toolbar_include);
        if (toolbarView != null) {
            TextView tvUserName = toolbarView.findViewById(R.id.userName);
            ImageView ivProfile = toolbarView.findViewById(R.id.profile_image);
            ImageView ivBack = toolbarView.findViewById(R.id.backArrow);
            View vStatus = toolbarView.findViewById(R.id.statusIndicator);

            if (tvUserName != null) tvUserName.setText(userName != null ? userName : "Chat");
            
            if (ivProfile != null) {
                if (profilePic != null && !profilePic.isEmpty()) {
                    Picasso.get().load(profilePic).placeholder(R.drawable.profile).into(ivProfile);
                } else {
                    ivProfile.setImageResource(R.drawable.profile);
                }
            }

            if (ivBack != null) ivBack.setOnClickListener(v -> onBackPressed());

            // Fetch full receiver details and listen for status
            database.getReference().child("UserProfiles").child(receiverId)
                    .addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            receiverUser = snapshot.getValue(Users.class);
                            if (receiverUser != null) {
                                receiverUser.setUserId(snapshot.getKey());
                                
                                // Update Status Indicator
                                if (vStatus != null) {
                                    if ("Active".equals(receiverUser.getStatus())) {
                                        vStatus.setVisibility(View.VISIBLE);
                                    } else {
                                        vStatus.setVisibility(View.GONE);
                                    }
                                }
                                
                                // Update Name/Pic if changed
                                if (tvUserName != null) tvUserName.setText(receiverUser.getUserName());
                                if (ivProfile != null && (profilePic == null || profilePic.isEmpty())) {
                                    if (receiverUser.getProfilePic() != null && !receiverUser.getProfilePic().isEmpty()) {
                                        Picasso.get().load(receiverUser.getProfilePic()).placeholder(R.drawable.profile).into(ivProfile);
                                    }
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });

            toolbarView.setOnClickListener(v -> {
                if (receiverUser != null) {
                    Intent intent = new Intent(ChatActivity.this, OtherUserProfileActivity.class);
                    intent.putExtra("userId", receiverUser.getUserId());
                    intent.putExtra("userName", receiverUser.getUserName());
                    intent.putExtra("email", receiverUser.getEmail());
                    intent.putExtra("phone", receiverUser.getPhone());
                    intent.putExtra("address", receiverUser.getAddress());
                    intent.putExtra("profilePic", receiverUser.getProfilePic());
                    startActivity(intent);
                }
            });
        }

        senderRoom = senderId + receiverId;
        receiverIdRoom = receiverId + senderId;

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList, this, receiverId);
        binding.chatRecyclerView.setAdapter(chatAdapter);
        
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // Ensures messages start from the bottom
        binding.chatRecyclerView.setLayoutManager(layoutManager);

        // Scroll to bottom when keyboard appears
        binding.chatRecyclerView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom < oldBottom) {
                binding.chatRecyclerView.postDelayed(() -> {
                    if (messageList.size() > 0) {
                        binding.chatRecyclerView.smoothScrollToPosition(messageList.size() - 1);
                    }
                }, 100);
            }
        });

        // Mark this chat as active for both users
        database.getReference().child("UserChats").child(senderId).child(receiverId).setValue(true);
        database.getReference().child("UserChats").child(receiverId).child(senderId).setValue(true);

        // Fetch messages
        database.getReference().child("Messages").child(senderRoom)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        messageList.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            Messages model = ds.getValue(Messages.class);
                            messageList.add(model);
                        }
                        chatAdapter.notifyDataSetChanged();
                        if (messageList.size() > 0) {
                            binding.chatRecyclerView.smoothScrollToPosition(messageList.size() - 1);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });

        binding.btnSend.setOnClickListener(v -> {
            String message = binding.etMessage.getText().toString().trim();
            if (message.isEmpty()) return;

            final Messages model = new Messages(senderId, message);
            model.setTimestamp(new Date().getTime());
            binding.etMessage.setText("");

            database.getReference().child("Messages").child(senderRoom).push().setValue(model)
                    .addOnSuccessListener(unused -> {
                        database.getReference().child("Messages").child(receiverIdRoom).push().setValue(model)
                                .addOnSuccessListener(unused1 -> {});
                    });
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
