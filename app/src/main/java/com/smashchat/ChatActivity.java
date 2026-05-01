package com.smashchat;

import android.os.Bundle;
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
import com.smashchat.databinding.ActivityChatBinding;

import java.util.ArrayList;
import java.util.Date;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        database = FirebaseDatabase.getInstance();
        auth = FirebaseAuth.getInstance();

        senderId = auth.getUid();
        receiverId = getIntent().getStringExtra("userId");
        String userName = getIntent().getStringExtra("userName");

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(userName);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        senderRoom = senderId + receiverId;
        receiverIdRoom = receiverId + senderId;

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList, this, receiverId);
        binding.chatRecyclerView.setAdapter(chatAdapter);
        binding.chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Mark this chat as active for both users
        database.getReference().child("Chats").child(senderId).child(receiverId).setValue(true);
        database.getReference().child("Chats").child(receiverId).child(senderId).setValue(true);

        // Fetch messages
        database.getReference().child("chats").child(senderRoom)
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

            database.getReference().child("chats").child(senderRoom).push().setValue(model)
                    .addOnSuccessListener(unused -> {
                        database.getReference().child("chats").child(receiverIdRoom).push().setValue(model)
                                .addOnSuccessListener(unused1 -> {});
                    });
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
