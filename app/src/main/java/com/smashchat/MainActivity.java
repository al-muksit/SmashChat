package com.smashchat;

import android.annotation.SuppressLint;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.smashchat.AccountDetails.SigninActivity;
import com.smashchat.Adapter.UsersAdapter;
import com.smashchat.Models.Users;
import com.smashchat.Utils.PreferenceManager;
import com.smashchat.databinding.ActivityMainBinding;

import java.util.ArrayList;

/**
 * MainActivity is the primary screen that displays the list of users fetched from Firebase.
 */
public class MainActivity extends AppCompatActivity {

    private FirebaseDatabase firebaseDatabase;
    private ActivityMainBinding binding;
    private ArrayList<Users> userList = new ArrayList<>(); // Active chats
    private ArrayList<Users> displayedList = new ArrayList<>(); // Currently shown list
    private UsersAdapter usersAdapter;
    private PreferenceManager preferenceManager;
    private Handler searchHandler = new Handler();
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        preferenceManager = new PreferenceManager(this);
        // Apply theme before setContentView
        if (preferenceManager.isDarkMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
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
        fetchUsers();
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

                                    String name = user.getUserName() != null ? user.getUserName().toLowerCase() : "";
                                    String customId = user.getCustomId() != null ? user.getCustomId().toLowerCase() : "";
                                    
                                    // Search by name or custom ID as requested
                                    if (name.contains(lowerCaseQuery) || customId.contains(lowerCaseQuery)) {
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

                        long totalChats = snapshot.getChildrenCount();
                        final int[] fetchedCount = {0};

                        for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                            String otherUserId = chatSnapshot.getKey();
                            
                            // Fetch user details for each active chat
                            firebaseDatabase.getReference().child("UserProfiles").child(otherUserId)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                                            Users user = userSnapshot.getValue(Users.class);
                                            if (user != null) {
                                                user.setUserId(userSnapshot.getKey());
                                                userList.add(user);
                                            }
                                            fetchedCount[0]++;
                                            if (fetchedCount[0] == totalChats) {
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
                                        public void onCancelled(@NonNull DatabaseError error) {
                                            fetchedCount[0]++;
                                            if (fetchedCount[0] == totalChats) {
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

        MenuItem themeItem = menu.findItem(R.id.dark_mode_toggle);
        SwitchCompat themeSwitch = (SwitchCompat) themeItem.getActionView().findViewById(R.id.theme_switch);
        
        themeSwitch.setChecked(preferenceManager.isDarkMode());
        
        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setDarkMode(isChecked);
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.profile) {
            startActivity(new Intent(MainActivity.this, ProfileActivity.class));
            return true;
        } else if (id == R.id.settings) {
            Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
}
