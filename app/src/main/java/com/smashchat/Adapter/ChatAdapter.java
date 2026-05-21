package com.smashchat.Adapter;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.smashchat.Models.Messages;
import com.smashchat.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * ChatAdapter manages the message list in the ChatActivity,
 * toggling between sender and receiver layouts.
 */
public class ChatAdapter extends RecyclerView.Adapter {

    private ArrayList<Messages> messageList;
    private Context context;
    private String recId;

    private int SENDER_VIEW_TYPE = 1;
    private int RECEIVER_VIEW_TYPE = 2;

    public ChatAdapter(ArrayList<Messages> messageList, Context context, String recId) {
        this.messageList = messageList;
        this.context = context;
        this.recId = recId;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == SENDER_VIEW_TYPE) {
            View view = LayoutInflater.from(context).inflate(R.layout.sample_sender, parent, false);
            return new SenderViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.sample_receiver, parent, false);
            return new ReceiverViewHolder(view);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (messageList.get(position).getuId().equals(FirebaseAuth.getInstance().getUid())) {
            return SENDER_VIEW_TYPE;
        } else {
            return RECEIVER_VIEW_TYPE;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Messages messageModel = messageList.get(position);

        if (holder.getClass() == SenderViewHolder.class) {
            SenderViewHolder senderViewHolder = (SenderViewHolder) holder;
            
            if (messageModel.getType() == 1) {
                // Smile Emoji
                senderViewHolder.senderMsg.setText("");
                senderViewHolder.senderMsg.setBackground(null);
                senderViewHolder.senderMsg.setCompoundDrawablesWithIntrinsicBounds(null, null, ContextCompat.getDrawable(context, R.drawable.smile), null);
                senderViewHolder.senderMsg.setPadding(0, 0, 0, 0);
            } else {
                // Text Message
                senderViewHolder.senderMsg.setText(messageModel.getMessage());
                senderViewHolder.senderMsg.setBackgroundResource(R.drawable.sender_bg);
                senderViewHolder.senderMsg.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, context.getResources().getDisplayMetrics());
                senderViewHolder.senderMsg.setPadding(padding, padding, padding, padding);

                // Force theme-based color for sender text
                TypedValue typedValue = new TypedValue();
                if (context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)) {
                    senderViewHolder.senderMsg.setTextColor(typedValue.data);
                }
            }
            senderViewHolder.senderTime.setText(formatTime(messageModel.getTimestamp()));
        } else {
            ReceiverViewHolder receiverViewHolder = (ReceiverViewHolder) holder;
            
            if (messageModel.getType() == 1) {
                // Smile Emoji
                receiverViewHolder.receiverMsg.setText("");
                receiverViewHolder.receiverMsg.setBackground(null);
                receiverViewHolder.receiverMsg.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(context, R.drawable.smile), null, null, null);
                receiverViewHolder.receiverMsg.setPadding(0, 0, 0, 0);
            } else {
                // Text Message
                receiverViewHolder.receiverMsg.setText(messageModel.getMessage());
                receiverViewHolder.receiverMsg.setBackgroundResource(R.drawable.receiver_bg);
                receiverViewHolder.receiverMsg.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, context.getResources().getDisplayMetrics());
                receiverViewHolder.receiverMsg.setPadding(padding, padding, padding, padding);
            }
            receiverViewHolder.receiverTime.setText(formatTime(messageModel.getTimestamp()));
        }
    }

    private String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    public static class ReceiverViewHolder extends RecyclerView.ViewHolder {
        TextView receiverMsg, receiverTime;

        public ReceiverViewHolder(@NonNull View itemView) {
            super(itemView);
            receiverMsg = itemView.findViewById(R.id.receiverText);
            receiverTime = itemView.findViewById(R.id.receiverTime);
        }
    }

    public static class SenderViewHolder extends RecyclerView.ViewHolder {
        TextView senderMsg, senderTime;

        public SenderViewHolder(@NonNull View itemView) {
            super(itemView);
            senderMsg = itemView.findViewById(R.id.senderText);
            senderTime = itemView.findViewById(R.id.senderTime);
        }
    }
}
