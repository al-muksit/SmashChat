package com.smashchat;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.smashchat.Adapter.UsersAdapter;
import com.smashchat.Models.Users;
import com.smashchat.databinding.ActivitySearchBinding;

import java.util.ArrayList;

/**
 * SearchActivity allows users to find others by their unique Custom ID.
 */
public class SearchActivity extends AppCompatActivity {

    private ActivitySearchBinding binding;
    private ArrayList<Users> list = new ArrayList<>();
    private UsersAdapter adapter;
    private FirebaseDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        database = FirebaseDatabase.getInstance();
        adapter = new UsersAdapter(list, this);
        binding.searchRecyclerView.setAdapter(adapter);
        binding.searchRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchUser(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchUser(newText);
                return true;
            }
        });
    }

    private void searchUser(String query) {
        if (query.isEmpty()) {
            list.clear();
            adapter.notifyDataSetChanged();
            return;
        }

        String lowerCaseQuery = query.toLowerCase().trim();
        database.getReference().child("Users")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        list.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            Users user = ds.getValue(Users.class);
                            if (user != null) {
                                user.setUserId(ds.getKey());
                                
                                String name = user.getUserName() != null ? user.getUserName().toLowerCase() : "";
                                String customId = user.getCustomId() != null ? user.getCustomId().toLowerCase() : "";
                                String email = user.getEmail() != null ? user.getEmail().toLowerCase() : "";
                                
                                // Show user if name or ID contains the query string
                                if (name.contains(lowerCaseQuery) || customId.contains(lowerCaseQuery) || email.contains(lowerCaseQuery)) {
                                    // Don't show current logged-in user
                                    if (!ds.getKey().equals(com.google.firebase.auth.FirebaseAuth.getInstance().getUid())) {
                                        list.add(user);
                                    }
                                }
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }
}
