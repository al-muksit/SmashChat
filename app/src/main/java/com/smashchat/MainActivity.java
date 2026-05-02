package com.smashchat;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
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

    private FirebaseAuth firebaseAuth;
    private FirebaseDatabase firebaseDatabase;
    private ActivityMainBinding binding;
    private ArrayList<Users> userList = new ArrayList<>();
    private UsersAdapter usersAdapter;
    private PreferenceManager preferenceManager;

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
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseDatabase = FirebaseDatabase.getInstance();

        // Setup Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("SmashChat");
        }
        binding.toolbar.setTitleTextColor(Color.WHITE);

        // Setup RecyclerView
        usersAdapter = new UsersAdapter(userList, this);
        binding.userRecyclerView.setAdapter(usersAdapter);
        binding.userRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Fetch users from Firebase Realtime Database
        fetchUsers();
    }

    private void fetchUsers() {
        String currentUid = FirebaseAuth.getInstance().getUid();
        if (currentUid == null) return;

        // Listen for active chats for the current user
        firebaseDatabase.getReference().child("Chats").child(currentUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        userList.clear();
                        if (!snapshot.exists()) {
                            usersAdapter.notifyDataSetChanged();
                            return;
                        }

                        for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                            String otherUserId = chatSnapshot.getKey();
                            
                            // Fetch user details for each active chat
                            firebaseDatabase.getReference().child("Users").child(otherUserId)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                                            Users user = userSnapshot.getValue(Users.class);
                                            if (user != null) {
                                                user.setUserId(userSnapshot.getKey());
                                                userList.add(user);
                                                usersAdapter.notifyDataSetChanged();
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
                    }
                });
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
        } else if (id == R.id.logout) {
            firebaseAuth.signOut();
            Intent intent = new Intent(MainActivity.this, SigninActivity.class);
            startActivity(intent);
            finish();
            return true;
        } else if (id == R.id.search) {
            startActivity(new Intent(MainActivity.this, SearchActivity.class));
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
}
