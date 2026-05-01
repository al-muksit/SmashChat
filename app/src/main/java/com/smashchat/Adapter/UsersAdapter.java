package com.smashchat.Adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.smashchat.ChatActivity;
import com.smashchat.MainActivity;
import com.smashchat.Models.Users;
import com.smashchat.OtherUserProfileActivity;
import com.smashchat.R;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

/**
 * UsersAdapter is responsible for displaying the list of users in the RecyclerView.
 */
public class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.ViewHolder> {
    
    private ArrayList<Users> list;
    private Context context;

    public UsersAdapter(ArrayList<Users> list, Context context) {
        this.list = list;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the custom layout for each user item
        View view = LayoutInflater.from(context).inflate(R.layout.sample_show_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Users users = list.get(position);
        
        // Load profile picture using Picasso with a placeholder
        Picasso.get()
                .load(users.getProfilePic())
                .placeholder(R.drawable.profile)
                .into(holder.imageView);
        
        // Set username and last message (if available)
        holder.userName.setText(users.getUserName());
        // For now, using a placeholder for last message
        holder.lastMessage.setText(users.getLastMessage() != null ? users.getLastMessage() : "Tap to chat");

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            Intent intent;
            if (context instanceof MainActivity) {
                // If in MainActivity, go straight to chat
                intent = new Intent(context, ChatActivity.class);
            } else {
                // If in SearchActivity, go to profile first
                intent = new Intent(context, OtherUserProfileActivity.class);
            }
            intent.putExtra("userId", users.getUserId());
            intent.putExtra("userName", users.getUserName());
            intent.putExtra("phone", users.getPhone());
            intent.putExtra("address", users.getAddress());
            intent.putExtra("profilePic", users.getProfilePic());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    /**
     * ViewHolder class to hold references to the UI components for each list item.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView userName, lastMessage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.profile_image);
            userName = itemView.findViewById(R.id.userName);
            lastMessage = itemView.findViewById(R.id.lastMessage);
        }
    }
}
