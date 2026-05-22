package com.smashchat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.smashchat.AccountDetails.OtherUserProfileActivity;
import com.smashchat.Adapter.ChatAdapter;
import com.smashchat.Models.Messages;
import com.smashchat.Models.Users;
import com.smashchat.databinding.ActivityChatBinding;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Date;

/**
 * ChatActivity handles the one-to-one messaging logic between two users.
 */
public class ChatActivity extends BaseActivity {

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

            // Apply padding to root to respect status bar (top) and navigation bar/keyboard (bottom)
            // We use Math.max to ensure we cover either the navigation bar or the keyboard, whichever is taller
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, ime.bottom));

            return insets;
        });

        database = FirebaseDatabase.getInstance();
        auth = FirebaseAuth.getInstance();

        senderId = auth.getUid();
        receiverId = getIntent().getStringExtra("userId");
        currentChatUserId = receiverId;
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

        // Mark this chat as read for the current user (sender)
        database.getReference().child("UserChats").child(senderId).child(receiverId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Object val = snapshot.getValue();
                            if (val instanceof Long) {
                                // Convert old format to new format while preserving timestamp
                                java.util.HashMap<String, Object> map = new java.util.HashMap<>();
                                map.put("timestamp", val);
                                map.put("read", true);
                                database.getReference().child("UserChats").child(senderId).child(receiverId).setValue(map);
                            } else {
                                // Update read status in existing map format
                                database.getReference().child("UserChats").child(senderId).child(receiverId).child("read").setValue(true);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });

        // Fetch messages
        database.getReference().child("Messages").child(senderRoom)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            messageList.clear();
                            chatAdapter.notifyDataSetChanged();
                            return;
                        }
                        messageList.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            Messages model = ds.getValue(Messages.class);
                            messageList.add(model);
                        }
                        chatAdapter.notifyDataSetChanged();

                        if (!messageList.isEmpty()) {
                            binding.chatRecyclerView.smoothScrollToPosition(messageList.size() - 1);
                        }
                        
                        // Mark as read when new messages arrive while user is in chat
                        database.getReference().child("UserChats").child(senderId).child(receiverId).child("read").setValue(true);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });

        // Dynamic Send/Smile button toggle
        binding.btnSend.setImageResource(R.drawable.smile);
        binding.etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().trim().isEmpty()) {
                    binding.btnSend.setImageResource(R.drawable.smile);
                } else {
                    binding.btnSend.setImageResource(R.drawable.send_btn);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnSend.setOnClickListener(v -> {
            String message = binding.etMessage.getText().toString().trim();
            
            if (message.isEmpty()) {
                // Send Smile Emoji
                checkBlockStatusAndSend("[smile]", 1);
            } else {
                // Send Text Message
                checkBlockStatusAndSend(message, 0);
            }
        });
    }

    private void checkBlockStatusAndSend(String message, int type) {
        // Check if blocked before sending
        database.getReference().child("BlockedUsers").child(senderId).child(receiverId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Toast.makeText(ChatActivity.this, "You have blocked this user", Toast.LENGTH_SHORT).show();
                        } else {
                            // Check if the receiver blocked the sender
                            database.getReference().child("BlockedUsers").child(receiverId).child(senderId)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot snapshot2) {
                                            if (snapshot2.exists()) {
                                                Toast.makeText(ChatActivity.this, "You cannot send messages to this user", Toast.LENGTH_SHORT).show();
                                            } else {
                                                sendMessage(message, type);
                                            }
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {}
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void sendMessage(String message, int type) {
        long currentTimestamp = new Date().getTime();
        final Messages model = new Messages(senderId, message, currentTimestamp, type);
        binding.etMessage.setText("");

        database.getReference().child("Messages").child(senderRoom).push().setValue(model)
                .addOnSuccessListener(unused -> {
                    database.getReference().child("Messages").child(receiverIdRoom).push().setValue(model)
                            .addOnSuccessListener(unused1 -> {
                                // Update last interaction time for both users
                                database.getReference().child("UserChats").child(senderId).child(receiverId).child("timestamp").setValue(currentTimestamp);
                                database.getReference().child("UserChats").child(senderId).child(receiverId).child("read").setValue(true);
                                
                                database.getReference().child("UserChats").child(receiverId).child(senderId).child("timestamp").setValue(currentTimestamp);
                                database.getReference().child("UserChats").child(receiverId).child(senderId).child("read").setValue(false);
                            });
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.home_menu) {
            Intent intent = new Intent(ChatActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
            return true;
        } else if (id == R.id.mute_menu) {
            showMuteDialog();
            return true;
        } else if (id == R.id.delete_chat_menu) {
            showDeleteConfirmation();
            return true;
        } else if (id == R.id.block_menu) {
            showBlockConfirmation();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showMuteDialog() {
        String[] options = {"ON", "OFF"};

        // Fetch current mute status from Firebase
        // Path: UserProfiles / <myUid> / MutedUsers / <otherUid>
        database.getReference().child("UserProfiles").child(senderId).child("MutedUsers").child(receiverId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        // If value is true, it means the user is muted (Mute is ON)
                        boolean isCurrentlyMuted = snapshot.exists() && Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                        int checkedItem = isCurrentlyMuted ? 0 : 1; // 0 for ON, 1 for OFF

                        new AlertDialog.Builder(ChatActivity.this)
                                .setTitle("Mute Notifications")
                                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                                    boolean mute = (which == 0);
                                    database.getReference().child("UserProfiles").child(senderId)
                                            .child("MutedUsers").child(receiverId).setValue(mute)
                                            .addOnSuccessListener(unused -> {
                                                String status = mute ? "Muted" : "Unmuted";
                                                Toast.makeText(ChatActivity.this, "Notifications " + status, Toast.LENGTH_SHORT).show();
                                                dialog.dismiss();
                                            });
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ChatActivity.this, "Failed to load mute settings", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Chat")
                .setMessage("Are you sure you want to delete this chat permanently?")
                .setPositiveButton("Confirm", (dialog, which) -> {
                    // Remove message history for this user
                    database.getReference().child("Messages").child(senderId + receiverId).removeValue();
                    // Remove from active chats list for this user
                    database.getReference().child("UserChats").child(senderId).child(receiverId).removeValue();

                    Toast.makeText(ChatActivity.this, "Chat deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showBlockConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Block User")
                .setMessage("Are you sure you want to block this user? This will also delete your chat history.")
                .setPositiveButton("Confirm", (dialog, which) -> {
                    // 1. Add to BlockedUsers list (A blocked B) and BlockedBy list (B is blocked by A)
                    database.getReference().child("BlockedUsers").child(senderId).child(receiverId).setValue(true);
                    database.getReference().child("BlockedBy").child(receiverId).child(senderId).setValue(true);
                    
                    // 2. Delete chat history for both
                    database.getReference().child("Messages").child(senderRoom).removeValue();
                    database.getReference().child("Messages").child(receiverIdRoom).removeValue();
                    
                    // 3. Remove from active chats (UserChats)
                    database.getReference().child("UserChats").child(senderId).child(receiverId).removeValue();
                    database.getReference().child("UserChats").child(receiverId).child(senderId).removeValue();

                    Toast.makeText(ChatActivity.this, "User blocked and chat deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        currentChatUserId = null;
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
