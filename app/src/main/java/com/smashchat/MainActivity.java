package com.smashchat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.smashchat.AccountDetails.ProfileActivity;
import com.smashchat.Adapter.UsersAdapter;
import com.smashchat.Models.Users;
import com.smashchat.Others.SettingsActivity;
import com.smashchat.Services.BaseActivity;
import com.smashchat.Services.MessageNotificationService;
import com.smashchat.databinding.ActivityMainBinding;

import java.util.ArrayList;

public class MainActivity extends BaseActivity {

    private FirebaseDatabase firebaseDatabase;
    private ActivityMainBinding binding;
    private ArrayList<Users> userList = new ArrayList<>(); // Active chats
    private ArrayList<Users> displayedList = new ArrayList<>(); // Currently shown list
    private ArrayList<String> blockedUsersList = new ArrayList<>(); // People I blocked
    private ArrayList<String> blockedByList = new ArrayList<>(); // People who blocked me
    private final java.util.HashMap<String, ValueEventListener> profileListeners = new java.util.HashMap<>();
    private UsersAdapter usersAdapter;
    private Handler searchHandler = new Handler();
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initializing View Binding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        // Adjusting layout for system bars (status bar, navigation bar)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Firebase
        firebaseDatabase = FirebaseDatabase.getInstance();

        // Setup Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Setup RecyclerView
        usersAdapter = new UsersAdapter(displayedList, this);
        binding.userRecyclerView.setAdapter(usersAdapter);
        binding.userRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Load cached history
        userList = preferenceManager.getChatHistory();
        displayedList.clear();
        displayedList.addAll(userList);
        usersAdapter.notifyDataSetChanged();
        updateEmptyState();

        // Setup Search with Debouncing
        binding.searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchHandler.removeCallbacks(searchRunnable);
                searchUsers(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchHandler.removeCallbacks(searchRunnable);
                searchRunnable = () -> searchUsers(newText);
                searchHandler.postDelayed(searchRunnable, 300); // 300ms delay
                return true;
            }
        });

        // Fetch users from Firebase Realtime Database
        fetchBlockedLists();
        fetchUsers();

        // Request Notification Permission for Android 13+
        requestNotificationPermission();

        // Setup SwipeRefreshLayout
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            fetchBlockedLists();
            fetchUsers();
            // Stop refreshing after a short delay to give visual feedback
            new Handler().postDelayed(() -> binding.swipeRefreshLayout.setRefreshing(false), 1000);
        });

        // Start Notification Service if not running
        Intent serviceIntent = new Intent(this, MessageNotificationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void fetchBlockedLists() {
        String currentUid = FirebaseAuth.getInstance().getUid();
        if (currentUid == null) return;

        // Listen for people I blocked
        firebaseDatabase.getReference().child("BlockedUsers").child(currentUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        blockedUsersList.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            blockedUsersList.add(ds.getKey());
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });

        // Listen for people who blocked me
        firebaseDatabase.getReference().child("BlockedBy").child(currentUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        blockedByList.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            blockedByList.add(ds.getKey());
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void searchUsers(String query) {
        String lowerCaseQuery = query.toLowerCase().trim();
        
        if (lowerCaseQuery.isEmpty()) {
            runOnUiThread(() -> {
                displayedList.clear();
                displayedList.addAll(userList);
                usersAdapter.notifyDataSetChanged();
                updateEmptyState();
            });
            return;
        }

        String currentUid = FirebaseAuth.getInstance().getUid();
        if (currentUid == null) return;

        firebaseDatabase.getReference().child("UserProfiles")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        // Check if query hasn't changed while we were fetching
                        if (!query.equals(binding.searchView.getQuery().toString())) {
                            return;
                        }

                        ArrayList<Users> results = new ArrayList<>();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            try {
                                Users user = ds.getValue(Users.class);
                                if (user != null) {
                                    String userId = ds.getKey();
                                    user.setUserId(userId);
                                    
                                    // Don't show current logged-in user
                                    if (userId != null && userId.equals(currentUid)) {
                                        continue;
                                    }

                                    // Filter out blocked users
                                    if (blockedUsersList.contains(userId) || blockedByList.contains(userId)) {
                                        continue;
                                    }

                                    String name = user.getUserName() != null ? user.getUserName().toLowerCase() : "";
                                    String customId = user.getCustomId() != null ? user.getCustomId().toLowerCase() : "";
                                    
                                    // Search by name or custom ID as requested
                                    if (name.contains(lowerCaseQuery) || customId.contains(lowerCaseQuery)) {
                                        // Check if this user is in our active chats to get read status
                                        for (Users activeUser : userList) {
                                            if (activeUser.getUserId().equals(userId)) {
                                                user.setRead(activeUser.isRead());
                                                user.setLastMessageTime(activeUser.getLastMessageTime());
                                                break;
                                            }
                                        }
                                        results.add(user);
                                    }
                                }
                            } catch (Exception e) {
                                // Skip malformed data
                            }
                        }
                        
                        // Update UI on main thread
                        runOnUiThread(() -> {
                            // Verify query again before updating list
                            if (query.equals(binding.searchView.getQuery().toString())) {
                                displayedList.clear();
                                displayedList.addAll(results);
                                usersAdapter.notifyDataSetChanged();
                                updateEmptyState();
                            }
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Search failed", Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void fetchUsers() {
        String currentUid = FirebaseAuth.getInstance().getUid();
        if (currentUid == null) return;

        // Listen for active chats for the current user
        firebaseDatabase.getReference().child("UserChats").child(currentUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        java.util.HashSet<String> currentChatIds = new java.util.HashSet<>();
                        
                        if (!snapshot.exists()) {
                            // No active chats, clear everything
                            clearStaleListeners(currentChatIds);
                            userList.clear();
                            updateDisplayList();
                            return;
                        }

                        for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                            String otherUserId = chatSnapshot.getKey();
                            if (otherUserId == null) continue;
                            
                            currentChatIds.add(otherUserId);
                            
                            // Only add a listener if we don't already have one for this user
                            if (!profileListeners.containsKey(otherUserId)) {
                                ValueEventListener listener = new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                                        Users user = userSnapshot.getValue(Users.class);
                                        if (user != null) {
                                            user.setUserId(userSnapshot.getKey());
                                            
                                            // Filter out blocked users
                                            if (blockedUsersList.contains(user.getUserId()) || blockedByList.contains(user.getUserId())) {
                                                removeFromUserList(user.getUserId());
                                                updateDisplayList();
                                                return;
                                            }
                                            
                                            // Get latest chat info
                                            firebaseDatabase.getReference().child("UserChats").child(currentUid).child(otherUserId)
                                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                                        @Override
                                                        public void onDataChange(@NonNull DataSnapshot chatInfo) {
                                                            if (!chatInfo.exists()) return;

                                                            Object val = chatInfo.getValue();
                                                            if (val instanceof Long) {
                                                                user.setLastMessageTime((Long) val);
                                                                user.setRead(true);
                                                            } else {
                                                                Long timestamp = chatInfo.child("timestamp").getValue(Long.class);
                                                                Boolean isRead = chatInfo.child("read").getValue(Boolean.class);
                                                                if (timestamp != null) user.setLastMessageTime(timestamp);
                                                                user.setRead(isRead != null ? isRead : true);
                                                            }

                                                            // Fetch Mute Status
                                                            firebaseDatabase.getReference().child("UserProfiles").child(currentUid)
                                                                    .child("MutedUsers").child(otherUserId)
                                                                    .addValueEventListener(new ValueEventListener() {
                                                                        @Override
                                                                        public void onDataChange(@NonNull DataSnapshot muteSnapshot) {
                                                                            boolean isMuted = muteSnapshot.exists() && Boolean.TRUE.equals(muteSnapshot.getValue(Boolean.class));
                                                                            user.setMuted(isMuted);
                                                                            
                                                                            // Force refresh the list item
                                                                            runOnUiThread(() -> {
                                                                                updateUserInList(user);
                                                                            });
                                                                        }

                                                                        @Override
                                                                        public void onCancelled(@NonNull DatabaseError error) {}
                                                                    });
                                                        }

                                                        @Override
                                                        public void onCancelled(@NonNull DatabaseError error) {}
                                                    });
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {}
                                };
                                
                                profileListeners.put(otherUserId, listener);
                                firebaseDatabase.getReference().child("UserProfiles").child(otherUserId).addValueEventListener(listener);
                            }
                        }
                        
                        // Cleanup listeners and users for chats that were removed
                        clearStaleListeners(currentChatIds);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(MainActivity.this, "Failed to load chats: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        updateEmptyState();
                    }
                });
    }

    private void clearStaleListeners(java.util.HashSet<String> currentChatIds) {
        java.util.Iterator<java.util.Map.Entry<String, ValueEventListener>> it = profileListeners.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String, ValueEventListener> entry = it.next();
            if (!currentChatIds.contains(entry.getKey())) {
                firebaseDatabase.getReference().child("UserProfiles").child(entry.getKey()).removeEventListener(entry.getValue());
                it.remove();
                
                // Also remove from userList
                removeFromUserList(entry.getKey());
            }
        }
        updateDisplayList();
    }

    private void removeFromUserList(String userId) {
        if (userId == null) return;
        java.util.Iterator<Users> it = userList.iterator();
        while (it.hasNext()) {
            Users u = it.next();
            if (userId.equals(u.getUserId())) {
                it.remove();
            }
        }
    }

    private void updateUserInList(Users user) {
        int index = -1;
        for (int i = 0; i < userList.size(); i++) {
            if (userList.get(i).getUserId().equals(user.getUserId())) {
                index = i;
                break;
            }
        }
        
        if (index != -1) {
            userList.set(index, user);
        } else {
            userList.add(user);
        }

        java.util.Collections.sort(userList, (u1, u2) -> 
                Long.compare(u2.getLastMessageTime(), u1.getLastMessageTime()));
        
        preferenceManager.saveChatHistory(userList);
        updateDisplayList();
    }

    private void updateDisplayList() {
        runOnUiThread(() -> {
            if (binding.searchView.getQuery().toString().isEmpty()) {
                displayedList.clear();
                displayedList.addAll(userList);
                usersAdapter.notifyDataSetChanged();
                updateEmptyState();
            }
        });
    }

    private void updateEmptyState() {
        if (displayedList.isEmpty()) {
            binding.emptyStateText.setVisibility(android.view.View.VISIBLE);
            binding.userRecyclerView.setVisibility(android.view.View.GONE);
            
            if (!binding.searchView.getQuery().toString().isEmpty()) {
                binding.emptyStateText.setText("No results found");
            } else {
                binding.emptyStateText.setText("Search people to connect");
            }
        } else {
            binding.emptyStateText.setVisibility(android.view.View.GONE);
            binding.userRecyclerView.setVisibility(android.view.View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove all profile listeners to prevent memory leaks
        for (java.util.Map.Entry<String, ValueEventListener> entry : profileListeners.entrySet()) {
            firebaseDatabase.getReference().child("UserProfiles").child(entry.getKey()).removeEventListener(entry.getValue());
        }
        profileListeners.clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.profile) {
            startActivity(new Intent(MainActivity.this, ProfileActivity.class));
            return true;
        } else if (id == R.id.settings) {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            return true;
        } else if (id == R.id.share) {
            firebaseDatabase.getReference("AppLink").child("SmashChat")
                    .get().addOnSuccessListener(dataSnapshot -> {
                        String appLink = dataSnapshot.getValue(String.class);
                        if (appLink != null) {
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("text/plain");
                            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Check out SmashChat!");
                            shareIntent.putExtra(Intent.EXTRA_TEXT, "I'm loving SmashChat app! Download it from here: " + appLink);
                            startActivity(Intent.createChooser(shareIntent, "Share via"));
                        } else {
                            Toast.makeText(this, "App link not found in database.", Toast.LENGTH_SHORT).show();
                        }
                    }).addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to retrieve app link: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
}
