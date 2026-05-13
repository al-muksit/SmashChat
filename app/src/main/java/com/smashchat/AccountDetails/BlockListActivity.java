package com.smashchat.AccountDetails;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.smashchat.Adapter.BlockedUsersAdapter;
import com.smashchat.BaseActivity;
import com.smashchat.Models.Users;
import com.smashchat.databinding.ActivityBlockListBinding;

import java.util.ArrayList;

public class BlockListActivity extends BaseActivity {

    private ActivityBlockListBinding binding;
    private FirebaseDatabase database;
    private String currentUid;
    private ArrayList<Users> blockedUsers = new ArrayList<>();
    private ArrayList<Users> displayedList = new ArrayList<>();
    private BlockedUsersAdapter adapter;
    private Handler searchHandler = new Handler();
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBlockListBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        database = FirebaseDatabase.getInstance();
        currentUid = FirebaseAuth.getInstance().getUid();

        binding.backArrow.setOnClickListener(v -> finish());

        adapter = new BlockedUsersAdapter(displayedList, this, user -> unblockUser(user));
        binding.blockListRecyclerView.setAdapter(adapter);
        binding.blockListRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        binding.blockListSearch.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchHandler.removeCallbacks(searchRunnable);
                filterUsers(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchHandler.removeCallbacks(searchRunnable);
                searchRunnable = () -> filterUsers(newText);
                searchHandler.postDelayed(searchRunnable, 300);
                return true;
            }
        });

        fetchBlockedUsers();
    }

    private void fetchBlockedUsers() {
        if (currentUid == null) return;

        database.getReference().child("BlockedUsers").child(currentUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        blockedUsers.clear();
                        if (!snapshot.exists()) {
                            updateUI();
                            return;
                        }

                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String blockedId = ds.getKey();
                            database.getReference().child("UserProfiles").child(blockedId)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                                            Users user = userSnapshot.getValue(Users.class);
                                            if (user != null) {
                                                user.setUserId(userSnapshot.getKey());
                                                
                                                // Check for existing to avoid duplicates during rapid updates
                                                int existingIndex = -1;
                                                for (int i = 0; i < blockedUsers.size(); i++) {
                                                    if (blockedUsers.get(i).getUserId().equals(user.getUserId())) {
                                                        existingIndex = i;
                                                        break;
                                                    }
                                                }
                                                if (existingIndex != -1) {
                                                    blockedUsers.set(existingIndex, user);
                                                } else {
                                                    blockedUsers.add(user);
                                                }
                                                updateUI();
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

    private void filterUsers(String query) {
        String lowerCaseQuery = query.toLowerCase().trim();
        displayedList.clear();

        if (lowerCaseQuery.isEmpty()) {
            displayedList.addAll(blockedUsers);
        } else {
            for (Users user : blockedUsers) {
                String name = user.getUserName() != null ? user.getUserName().toLowerCase() : "";
                String customId = user.getCustomId() != null ? user.getCustomId().toLowerCase() : "";
                if (name.contains(lowerCaseQuery) || customId.contains(lowerCaseQuery)) {
                    displayedList.add(user);
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateUI() {
        filterUsers(binding.blockListSearch.getQuery().toString());
    }

    private void updateEmptyState() {
        if (displayedList.isEmpty()) {
            binding.emptyStateText.setVisibility(View.VISIBLE);
            binding.blockListRecyclerView.setVisibility(View.GONE);
            if (!binding.blockListSearch.getQuery().toString().isEmpty()) {
                binding.emptyStateText.setText("No blocked users found matching search");
            } else {
                binding.emptyStateText.setText("No blocked users");
            }
        } else {
            binding.emptyStateText.setVisibility(View.GONE);
            binding.blockListRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void unblockUser(Users user) {
        if (currentUid == null || user.getUserId() == null) return;

        // 1. Remove from my BlockedUsers list
        database.getReference().child("BlockedUsers").child(currentUid).child(user.getUserId()).removeValue()
                .addOnSuccessListener(unused -> {
                    // 2. Remove reciprocal from their BlockedBy list
                    database.getReference().child("BlockedBy").child(user.getUserId()).child(currentUid).removeValue()
                            .addOnSuccessListener(unused1 -> {
                                Toast.makeText(BlockListActivity.this, "User unblocked", Toast.LENGTH_SHORT).show();
                            });
                });
    }
}
