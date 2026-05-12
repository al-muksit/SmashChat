package com.smashchat.Adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.smashchat.Models.Users;
import com.smashchat.R;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

public class BlockedUsersAdapter extends RecyclerView.Adapter<BlockedUsersAdapter.ViewHolder> {

    private ArrayList<Users> list;
    private Context context;
    private OnUnblockListener onUnblockListener;

    public interface OnUnblockListener {
        void onUnblock(Users user);
    }

    public BlockedUsersAdapter(ArrayList<Users> list, Context context, OnUnblockListener onUnblockListener) {
        this.list = list;
        this.context = context;
        this.onUnblockListener = onUnblockListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.sample_show_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Users user = list.get(position);

        Picasso.get().load(user.getProfilePic()).placeholder(R.drawable.profile).into(holder.image);
        holder.userName.setText(user.getUserName());
        
        // Show @userID (customId) instead of status
        if (user.getCustomId() != null && !user.getCustomId().isEmpty()) {
            holder.userStatusText.setText("@" + user.getCustomId());
        } else {
            holder.userStatusText.setText("No User ID");
        }

        // Status indicator should be hidden for blocked users in this list
        holder.statusIndicator.setVisibility(View.GONE);

        holder.itemView.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Unblock User")
                    .setMessage("Are you want to unblock this user?")
                    .setPositiveButton("Confirm", (dialog, which) -> {
                        if (onUnblockListener != null) {
                            onUnblockListener.onUnblock(user);
                        }
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView userName, userStatusText;
        View statusIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.profile_image);
            userName = itemView.findViewById(R.id.userName);
            userStatusText = itemView.findViewById(R.id.userStatusText);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
        }
    }
}
