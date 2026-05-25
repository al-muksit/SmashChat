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
    private com.smashchat.Utils.DatabaseHelper databaseHelper;

    public interface OnUnblockListener {
        void onUnblock(Users user);
    }

    public BlockedUsersAdapter(ArrayList<Users> list, Context context, OnUnblockListener onUnblockListener) {
        this.list = list;
        this.context = context;
        this.onUnblockListener = onUnblockListener;
        this.databaseHelper = new com.smashchat.Utils.DatabaseHelper(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.sample_block_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Users user = list.get(position);

        // Try loading from local database first
        android.graphics.Bitmap localBitmap = databaseHelper.getImage(user.getUserId());
        if (localBitmap != null) {
            holder.image.setImageBitmap(localBitmap);
        } else if (user.getProfilePic() != null && !user.getProfilePic().isEmpty()) {
            Picasso.get().load(user.getProfilePic())
                    .placeholder(R.drawable.profile)
                    .error(R.drawable.profile)
                    .into(new com.squareup.picasso.Target() {
                        @Override
                        public void onBitmapLoaded(android.graphics.Bitmap bitmap, Picasso.LoadedFrom from) {
                            holder.image.setImageBitmap(bitmap);
                            databaseHelper.saveImage(user.getUserId(), bitmap);
                        }

                        @Override
                        public void onBitmapFailed(Exception e, android.graphics.drawable.Drawable errorDrawable) {
                            holder.image.setImageDrawable(errorDrawable);
                        }

                        @Override
                        public void onPrepareLoad(android.graphics.drawable.Drawable placeHolderDrawable) {
                            holder.image.setImageDrawable(placeHolderDrawable);
                        }
                    });
        } else {
            holder.image.setImageResource(R.drawable.profile);
        }

        holder.userName.setText(user.getUserName());
        
        // Show @userID (customId) - ensure only one '@'
        String customId = user.getCustomId();
        if (customId != null && !customId.isEmpty()) {
            if (customId.startsWith("@")) {
                holder.userIdText.setText(customId);
            } else {
                holder.userIdText.setText("@" + customId);
            }
        } else {
            holder.userIdText.setText("No User ID");
        }

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
        TextView userName, userIdText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.profile_image);
            userName = itemView.findViewById(R.id.userName);
            userIdText = itemView.findViewById(R.id.userIdText);
        }
    }
}
