package com.smashchat;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
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
import com.smashchat.Utils.PreferenceManager;
import com.smashchat.databinding.ActivityMainBinding;

import java.util.ArrayList;

public class MainActivity extends BaseActivity {

    private FirebaseDatabase firebaseDatabase;
    private ActivityMainBinding binding;
    private ArrayList<Users> userList = new ArrayList<>(); // Active chats
    private ArrayList<Users> displayedList = new ArrayList<>(); // Currently shown list
    private ArrayList<String> blockedUsersList = new ArrayList<>(); // People I blocked
    private ArrayList<String> blockedByList = new ArrayList<>(); // People who blocked me
    private UsersAdapter usersAdapter;
    private Handler searchHandler = new Handler();
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply theme before setContentView
        int currentTheme = preferenceManager.getDarkModeTheme();
        switch (currentTheme) {
            case PreferenceManager.THEME_OFF:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case PreferenceManager.THEME_ON:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case PreferenceManager.THEME_SYSTEM:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }

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
            getSupportActionBar().setTitle("SmashChat");
        }
        binding.toolbar.setTitleTextColor(Color.WHITE);

        // Setup RecyclerView
        usersAdapter = new UsersAdapter(displayedList, this);
        binding.userRecyclerView.setAdapter(usersAdapter);
        binding.userRecyclerView.setLayoutManager(new LinearLayoutManager(this));

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
                        userList.clear();
                        if (!snapshot.exists()) {
                            if (binding.searchView.getQuery().toString().isEmpty()) {
                                displayedList.clear();
                                usersAdapter.notifyDataSetChanged();
                                updateEmptyState();
                            }
                            return;
                        }

                        for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                            String otherUserId = chatSnapshot.getKey();
                            
                            // Listen for real-time profile/status updates for each active chat
                            firebaseDatabase.getReference().child("UserProfiles").child(otherUserId)
                                    .addValueEventListener(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                                            Users user = userSnapshot.getValue(Users.class);
                                            if (user != null) {
                                                user.setUserId(userSnapshot.getKey());
                                                
                                                // Filter out blocked users even from active chats
                                                if (blockedUsersList.contains(user.getUserId()) || blockedByList.contains(user.getUserId())) {
                                                    return;
                                                }
                                                
                                                // Get the last interaction timestamp and read status from UserChats
                                                Object val = chatSnapshot.getValue();
                                                if (val instanceof Long) {
                                                    user.setLastMessageTime((Long) val);
                                                    user.setRead(true);
                                                } else {
                                                    Long timestamp = chatSnapshot.child("timestamp").getValue(Long.class);
                                                    Boolean isRead = chatSnapshot.child("read").getValue(Boolean.class);
                                                    
                                                    if (timestamp != null) {
                                                        user.setLastMessageTime(timestamp);
                                                    }
                                                    if (isRead != null) {
                                                        user.setRead(isRead);
                                                    } else {
                                                        user.setRead(true); // Default to read if not specified
                                                    }
                                                }
                                                
                                                // Update or add user in userList
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

                                                // Sort the list by last interaction time (descending)
                                                java.util.Collections.sort(userList, (u1, u2) -> 
                                                        Long.compare(u2.getLastMessageTime(), u1.getLastMessageTime()));

                                                // If not searching, update displayed list
                                                runOnUiThread(() -> {
                                                    if (binding.searchView.getQuery().toString().isEmpty()) {
                                                        displayedList.clear();
                                                        displayedList.addAll(userList);
                                                        usersAdapter.notifyDataSetChanged();
                                                        updateEmptyState();
                                                    }
                                                });
                                            }
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {}
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(MainActivity.this, "Failed to load chats: " + error.getMessage(), Toast.LENGTH_SHORT).show();
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
