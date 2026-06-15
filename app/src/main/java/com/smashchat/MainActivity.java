package com.smashchat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.app.AlertDialog;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.progressindicator.LinearProgressIndicator;

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

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

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
        
        // Initial state check
        if (!isNetworkAvailable()) {
            updateConnectivityStatus(false);
            isFirstConnectivityChange = false;
        }

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    if (!isFirstConnectivityChange) {
                        updateConnectivityStatus(true);
                    }
                    isFirstConnectivityChange = false;
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> {
                    // Double check if we really lost internet (e.g. if switching from WiFi to Mobile)
                    if (!isNetworkAvailable()) {
                        updateConnectivityStatus(false);
                        isFirstConnectivityChange = false;
                    }
                });
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } else {
            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
        }
    }

    private boolean isNetworkAvailable() {
        if (connectivityManager == null) return false;
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) return false;
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
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
        
        // This hack is to show icons in the overflow menu
        if (menu.getClass().getSimpleName().equals("MenuBuilder")) {
            try {
                java.lang.reflect.Method m = menu.getClass().getDeclaredMethod("setOptionalIconsVisible", boolean.class);
                m.setAccessible(true);
                m.invoke(menu, true);
            } catch (Exception e) {
                Log.e("MainActivity", "onMenuOpened", e);
            }
        }

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
            firebaseDatabase.getReference("AppUpdate").child("apkUrl")
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
else if (id == R.id.update) {
            showUpdateDialog();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void showUpdateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_update, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        TextView title = dialogView.findViewById(R.id.update_title);
        TextView versionInfo = dialogView.findViewById(R.id.version_info);
        TextView releaseNotes = dialogView.findViewById(R.id.release_notes);
        LinearLayout progressContainer = dialogView.findViewById(R.id.progress_container);
        LinearProgressIndicator progressBar = dialogView.findViewById(R.id.update_progress);
        TextView progressPercent = dialogView.findViewById(R.id.progress_percent);
        Button btnUpdate = dialogView.findViewById(R.id.btn_update);
        Button btnClose = dialogView.findViewById(R.id.btn_close);

        // Fetch update info from Firebase
        firebaseDatabase.getReference("AppUpdate").get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                Long latestVersionCode = snapshot.child("latestVersionCode").getValue(Long.class);
                String latestVersionName = snapshot.child("latestVersionName").getValue(String.class);
                String apkUrl = snapshot.child("apkUrl").getValue(String.class);
                String notes = snapshot.child("releaseNotes").getValue(String.class);

                long currentVersionCode = BuildConfig.VERSION_CODE;
                String currentVersionName = BuildConfig.VERSION_NAME;

                versionInfo.setText("Current: " + currentVersionName + " | Latest: " + (latestVersionName != null ? latestVersionName : "N/A"));

                if (latestVersionCode != null && latestVersionCode > currentVersionCode) {
                    title.setText("New Version Available!");
                    releaseNotes.setText(notes != null ? notes : "A new version of SmashChat is available. Update now for the latest features!");
                    btnUpdate.setText("Update Now");
                    btnUpdate.setOnClickListener(v -> {
                        if (apkUrl != null) {
                            startDownload(apkUrl, progressContainer, progressBar, progressPercent, btnUpdate);
                        } else {
                            Toast.makeText(this, "Update link not found", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    title.setText("Already Latest Version");
                    releaseNotes.setText("You are using the latest version of SmashChat.");
                    btnUpdate.setText("OK");
                    btnUpdate.setOnClickListener(v -> dialog.dismiss());
                    btnClose.setVisibility(View.GONE);
                    // Center the OK button
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) btnUpdate.getLayoutParams();
                    params.width = LinearLayout.LayoutParams.WRAP_CONTENT;
                    params.gravity = Gravity.CENTER;
                    btnUpdate.setLayoutParams(params);
                }
            } else {
                Toast.makeText(this, "No updates available at the moment", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Update check failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void startDownload(String url, LinearLayout progressContainer, LinearProgressIndicator progressBar, TextView progressPercent, Button btnUpdate) {
        progressContainer.setVisibility(View.VISIBLE);
        btnUpdate.setEnabled(false);
        btnUpdate.setText("Downloading...");

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnUpdate.setEnabled(true);
                    btnUpdate.setText("Try Again");
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Download error: " + response.message(), Toast.LENGTH_SHORT).show();
                        btnUpdate.setEnabled(true);
                        btnUpdate.setText("Try Again");
                    });
                    return;
                }

                if (response.body() == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Empty download response", Toast.LENGTH_SHORT).show();
                        btnUpdate.setEnabled(true);
                        btnUpdate.setText("Try Again");
                    });
                    return;
                }

                File apkFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SmashChat_update.apk");
                if (apkFile.exists()) apkFile.delete();

                try (InputStream is = response.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(apkFile)) {

                    long totalBytes = response.body().contentLength();
                    byte[] buffer = new byte[8192];
                    int read;
                    long downloadedBytes = 0;

                    while ((read = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                        downloadedBytes += read;
                        
                        final long total = totalBytes;
                        final long current = downloadedBytes;
                        runOnUiThread(() -> {
                            if (total > 0) {
                                int progress = (int) ((current * 100) / total);
                                progressBar.setProgress(progress);
                                progressPercent.setText(progress + "%");
                            } else {
                                progressBar.setIndeterminate(true);
                                progressPercent.setText("Downloading...");
                            }
                        });
                    }
                    fos.flush();
                    runOnUiThread(() -> {
                        btnUpdate.setEnabled(true);
                        btnUpdate.setText("Install Now");
                        btnUpdate.setOnClickListener(v -> installApk(apkFile));
                        // Automatically trigger installation
                        installApk(apkFile);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Error saving APK: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        btnUpdate.setEnabled(true);
                        btnUpdate.setText("Try Again");
                    });
                }
            }
        });
    }

    private void installApk(File file) {
        Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
