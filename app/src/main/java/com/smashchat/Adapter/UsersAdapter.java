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
import com.smashchat.Models.Users;
import com.smashchat.OtherUserProfileActivity;
import com.smashchat.R;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

/**
 * UsersAdapter is responsible for displaying the list of users in the RecyclerView.
 */
public class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.ViewHolder> {
    
    private final ArrayList<Users> list;
    private final Context context;

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
        try {
            Users users = list.get(position);
            
            // Load profile picture with fallback for empty/null strings
            if (users.getProfilePic() != null && !users.getProfilePic().isEmpty()) {
                Picasso.get()
                        .load(users.getProfilePic())
                        .placeholder(R.drawable.profile)
                        .error(R.drawable.profile)
                        .into(holder.imageView);
            } else {
                holder.imageView.setImageResource(R.drawable.profile);
            }
            
            // Set username and email
            holder.userName.setText(users.getUserName() != null ? users.getUserName() : "Unknown User");
            holder.userEmail.setText(users.getEmail() != null ? users.getEmail() : "");
            
            // Show Custom ID and Last Message
            String customId = users.getCustomId() != null ? users.getCustomId() : "";
            String lastMsg = users.getLastMessage() != null ? users.getLastMessage() : "Tap to chat";
            holder.lastMessage.setText(customId + " | " + lastMsg);

            // Set click listener
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, ChatActivity.class);
                intent.putExtra("userId", users.getUserId());
                intent.putExtra("userName", users.getUserName());
                intent.putExtra("profilePic", users.getProfilePic());
                
                // Add other details just in case
                intent.putExtra("phone", users.getPhone());
                intent.putExtra("address", users.getAddress());

                context.startActivity(intent);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        TextView userName, userEmail, lastMessage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.profile_image);
            userName = itemView.findViewById(R.id.userName);
            userEmail = itemView.findViewById(R.id.userEmail);
            lastMessage = itemView.findViewById(R.id.lastMessage);
        }
    }
}
