package com.smashchat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
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
import com.google.firebase.messaging.FirebaseMessaging;
import com.smashchat.AccountDetails.ProfileActivity;
import com.smashchat.Adapter.UsersAdapter;
import com.smashchat.Models.Users;
import com.smashchat.Others.SettingsActivity;
import com.smashchat.Services.BaseActivity;
import com.smashchat.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

public class MainActivity extends BaseActivity {

    private FirebaseDatabase firebaseDatabase;
    private ActivityMainBinding binding;
    private final ArrayList<Users> userList = new ArrayList<>(); // active chat list
    private final ArrayList<Users> displayedList = new ArrayList<>(); // the ones actually on screen
    private final ArrayList<String> blockedUsersList = new ArrayList<>(); // people I blocked
    private final ArrayList<String> blockedByList = new ArrayList<>(); // people who blocked me
    private final HashMap<String, ValueEventListener> profileListeners = new HashMap<>();
    private UsersAdapter usersAdapter;
    private final Handler searchHandler = new Handler();
    private Runnable searchRunnable;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private final Handler connectivityHandler = new Handler(Looper.getMainLooper());
    private boolean isFirstConnectivityChange = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // setup the view binding and edge-to-edge
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        // handle the status/nav bar spacing
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        firebaseDatabase = FirebaseDatabase.getInstance();

        // simple toolbar setup
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // recycler view for the chat list
        usersAdapter = new UsersAdapter(displayedList, this);
        binding.userRecyclerView.setAdapter(usersAdapter);
        binding.userRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // start with cached history for speed
        userList.addAll(preferenceManager.getChatHistory());
        displayedList.clear();
        displayedList.addAll(userList);
        usersAdapter.notifyDataSetChanged();
        updateEmptyState();

        // handle searching with a small delay so it doesn't lag
        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
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
                searchHandler.postDelayed(searchRunnable, 300);
                return true;
            }
        });

        // load all the real-time data
        fetchBlockedLists();
        fetchUsers();

        // ask for notification permission on new android versions
        requestNotificationPermission();

        // pull to refresh logic
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            fetchBlockedLists();
            fetchUsers();
            new Handler().postDelayed(() -> binding.swipeRefreshLayout.setRefreshing(false), 1000);
        });

        // get the FCM token so we can send push notifications later
        initFcmToken();

        setupConnectivityMonitor();
    }

    private void setupConnectivityMonitor() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        
        // Check initial state
        Network activeNetwork = connectivityManager.getActiveNetwork();
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        boolean hasInternet = capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        
        if (!hasInternet) {
            updateConnectivityStatus(false);
            isFirstConnectivityChange = false;
        }

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                runOnUiThread(() -> {
                    if (!isFirstConnectivityChange) {
                        updateConnectivityStatus(true);
                    }
                    isFirstConnectivityChange = false;
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                runOnUiThread(() -> {
                    updateConnectivityStatus(false);
                    isFirstConnectivityChange = false;
                });
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                super.onCapabilitiesChanged(network, capabilities);
                boolean hasInternetCapability = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                runOnUiThread(() -> {
                    if (!hasInternetCapability) {
                        updateConnectivityStatus(false);
                    }
                });
            }
        };

        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
    }

    private void updateConnectivityStatus(boolean isConnected) {
        boolean isDarkMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) 
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        if (isConnected) {
            binding.connectivityStatusLayout.setVisibility(View.VISIBLE);
            int greenColor = ContextCompat.getColor(this, isDarkMode ? R.color.green_night : R.color.green);
            binding.connectivityStatusLayout.setBackgroundColor(greenColor);
            binding.connectivityIcon.setImageResource(R.drawable.outline_cloud_done_24);
            binding.connectivityText.setText("Internet restored");

            connectivityHandler.removeCallbacksAndMessages(null);
            connectivityHandler.postDelayed(() -> binding.connectivityStatusLayout.setVisibility(View.GONE), 3000);
        } else {
            connectivityHandler.removeCallbacksAndMessages(null);
            binding.connectivityStatusLayout.setVisibility(View.VISIBLE);
            int redColor = ContextCompat.getColor(this, isDarkMode ? R.color.red_night : R.color.red);
            binding.connectivityStatusLayout.setBackgroundColor(redColor);
            binding.connectivityIcon.setImageResource(R.drawable.outline_cloud_off_24);
            binding.connectivityText.setText("No internet connection");
        }
    }

    private void initFcmToken() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w("MainActivity", "Couldn't get FCM token", task.getException());
                        return;
                    }

                    String token = task.getResult();
                    if (token != null) {
                        firebaseDatabase.getReference()
                                .child("UserProfiles").child(uid).child("fcmToken").setValue(token);
                    }
                });
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

        // check who I've blocked
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

        // check who has blocked me
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
                                    
                                    if (userId != null && userId.equals(currentUid)) continue;
                                    if (blockedUsersList.contains(userId) || blockedByList.contains(userId)) continue;

                                    String name = user.getUserName() != null ? user.getUserName().toLowerCase() : "";
                                    String customId = user.getCustomId() != null ? user.getCustomId().toLowerCase() : "";
                                    
                                    if (name.contains(lowerCaseQuery) || customId.contains(lowerCaseQuery)) {
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
                            } catch (Exception ignored) {}
                        }
                        
                        runOnUiThread(() -> {
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

        // get all the chats this user is part of
        firebaseDatabase.getReference().child("UserChats").child(currentUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        HashSet<String> currentChatIds = new HashSet<>();
                        
                        if (!snapshot.exists()) {
                            clearStaleListeners(currentChatIds);
                            userList.clear();
                            updateDisplayList();
                            return;
                        }

                        for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                            String otherUserId = chatSnapshot.getKey();
                            if (otherUserId == null) continue;
                            
                            currentChatIds.add(otherUserId);
                            
                            // only add a listener if we don't have one for this person yet
                            if (!profileListeners.containsKey(otherUserId)) {
                                ValueEventListener listener = new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                                        Users user = userSnapshot.getValue(Users.class);
                                        if (user != null) {
                                            user.setUserId(userSnapshot.getKey());
                                            
                                            if (blockedUsersList.contains(user.getUserId()) || blockedByList.contains(user.getUserId())) {
                                                removeFromUserList(user.getUserId());
                                                updateDisplayList();
                                                return;
                                            }
                                            
                                            // get the latest message and read status
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

                                                            // get their mute settings
                                                            firebaseDatabase.getReference().child("UserProfiles").child(currentUid)
                                                                    .child("MutedUsers").child(otherUserId)
                                                                    .addValueEventListener(new ValueEventListener() {
                                                                        @Override
                                                                        public void onDataChange(@NonNull DataSnapshot muteSnapshot) {
                                                                            boolean isMuted = muteSnapshot.exists() && Boolean.TRUE.equals(muteSnapshot.getValue(Boolean.class));
                                                                            user.setMuted(isMuted);
                                                                            runOnUiThread(() -> updateUserInList(user));
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
                        
                        clearStaleListeners(currentChatIds);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(MainActivity.this, "Failed to load chats", Toast.LENGTH_SHORT).show();
                        updateEmptyState();
                    }
                });
    }

    private void clearStaleListeners(HashSet<String> currentChatIds) {
        Iterator<Map.Entry<String, ValueEventListener>> it = profileListeners.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ValueEventListener> entry = it.next();
            if (!currentChatIds.contains(entry.getKey())) {
                firebaseDatabase.getReference().child("UserProfiles").child(entry.getKey()).removeEventListener(entry.getValue());
                it.remove();
                removeFromUserList(entry.getKey());
            }
        }
        updateDisplayList();
    }

    private void removeFromUserList(String userId) {
        if (userId == null) return;
        Iterator<Users> it = userList.iterator();
        while (it.hasNext()) {
            Users u = it.next();
            if (userId.equals(u.getUserId())) it.remove();
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
        
        if (index != -1) userList.set(index, user);
        else userList.add(user);

        // keep the most recent chats at the top
        Collections.sort(userList, (u1, u2) -> Long.compare(u2.getLastMessageTime(), u1.getLastMessageTime()));
        
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
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
        // clean up listeners so we don't leak memory
        for (Map.Entry<String, ValueEventListener> entry : profileListeners.entrySet()) {
            firebaseDatabase.getReference().child("UserProfiles").child(entry.getKey()).removeEventListener(entry.getValue());
        }
        profileListeners.clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
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
                            shareIntent.putExtra(Intent.EXTRA_TEXT, "I'm loving SmashChat! Download it here: " + appLink);
                            startActivity(Intent.createChooser(shareIntent, "Share via"));
                        } else {
                            Toast.makeText(this, "Can't find app link", Toast.LENGTH_SHORT).show();
                        }
                    }).addOnFailureListener(e -> Toast.makeText(this, "Failed to get link", Toast.LENGTH_SHORT).show());
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
}
