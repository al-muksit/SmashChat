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
import com.smashchat.R;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

/**
 * UsersAdapter is responsible for displaying the list of users in the RecyclerView.
 */
public class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.ViewHolder> {
    
    private final ArrayList<Users> list;
    private final Context context;
    private final com.smashchat.Utils.DatabaseHelper databaseHelper;

    public UsersAdapter(ArrayList<Users> list, Context context) {
        this.list = list;
        this.context = context;
        this.databaseHelper = new com.smashchat.Utils.DatabaseHelper(context);
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
            
            // Try loading from local database first
            android.graphics.Bitmap localBitmap = databaseHelper.getImage(users.getUserId());
            if (localBitmap != null) {
                holder.imageView.setImageBitmap(localBitmap);
            } else if (users.getProfilePic() != null && !users.getProfilePic().isEmpty()) {
                Picasso.get()
                        .load(users.getProfilePic())
                        .placeholder(R.drawable.profile)
                        .error(R.drawable.profile)
                        .into(new com.squareup.picasso.Target() {
                            @Override
                            public void onBitmapLoaded(android.graphics.Bitmap bitmap, Picasso.LoadedFrom from) {
                                holder.imageView.setImageBitmap(bitmap);
                                databaseHelper.saveImage(users.getUserId(), bitmap);
                            }

                            @Override
                            public void onBitmapFailed(Exception e, android.graphics.drawable.Drawable errorDrawable) {
                                holder.imageView.setImageDrawable(errorDrawable);
                            }

                            @Override
                            public void onPrepareLoad(android.graphics.drawable.Drawable placeHolderDrawable) {
                                holder.imageView.setImageDrawable(placeHolderDrawable);
                            }
                        });
            } else {
                holder.imageView.setImageResource(R.drawable.profile);
            }
            
            // Set username and status text
            holder.userName.setText(users.getUserName() != null ? users.getUserName() : "Unknown User");
            String status = users.getStatus() != null ? users.getStatus() : "Offline";
            holder.userStatus.setText(status);
            holder.userStatus.setVisibility(View.VISIBLE);

            // Highlight unread messages
            if (!users.isRead()) {
                holder.userName.setTypeface(null, android.graphics.Typeface.BOLD);
                holder.userStatus.setTypeface(null, android.graphics.Typeface.BOLD);

                android.util.TypedValue typedValue = new android.util.TypedValue();
                if (context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)) {
                    holder.userName.setTextColor(typedValue.data);
                    holder.userStatus.setTextColor(typedValue.data);
                }
            } else {
                holder.userName.setTypeface(null, android.graphics.Typeface.NORMAL);
                holder.userStatus.setTypeface(null, android.graphics.Typeface.NORMAL);

                android.util.TypedValue typedValue = new android.util.TypedValue();
                // Reset Name to OnSurface
                if (context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)) {
                    holder.userName.setTextColor(typedValue.data);
                }
                
                // Set specific color for Active and Offline based on Theme
                boolean isDarkMode = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                
                if ("Active".equals(status)) {
                    if (isDarkMode) {
                        holder.userStatus.setTextColor(android.graphics.Color.parseColor("#81C784")); // Light Green for Dark Mode
                    } else {
                        holder.userStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32")); // Dark Green for Light Mode
                    }
                } else {
                    if (isDarkMode) {
                        holder.userStatus.setTextColor(android.graphics.Color.parseColor("#BDBDBD")); // Light Grey for Dark Mode
                    } else {
                        holder.userStatus.setTextColor(android.graphics.Color.parseColor("#757575")); // Dark Grey for Light Mode
                    }
                }
            }
            
            // Show/Hide Green Dot
            if ("Active".equals(status)) {
                holder.statusIndicator.setVisibility(View.VISIBLE);
            } else {
                holder.statusIndicator.setVisibility(View.GONE);
            }

            // Show/Hide Mute Icon
            if (users.isMuted()) {
                holder.muteIcon.setVisibility(View.VISIBLE);
            } else {
                holder.muteIcon.setVisibility(View.GONE);
            }

            // Set click listener
            holder.itemView.setOnClickListener(v -> {
                String userId = users.getUserId();
                if (userId == null) return;

                Intent intent = new Intent(context, ChatActivity.class);
                intent.putExtra("userId", userId);
                intent.putExtra("userName", users.getUserName());
                intent.putExtra("profilePic", users.getProfilePic());
                
                // Add other details for ChatToolbar -> OtherUserProfile navigation
                intent.putExtra("email", users.getEmail());
                intent.putExtra("phone", users.getPhone());
                intent.putExtra("address", users.getAddress());
                intent.putExtra("customId", users.getCustomId());

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
        ImageView imageView, muteIcon;
        TextView userName, userStatus;
        View statusIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.profile_image);
            muteIcon = itemView.findViewById(R.id.mute_icon);
            userName = itemView.findViewById(R.id.userName);
            userStatus = itemView.findViewById(R.id.userStatusText);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
        }
    }
}
