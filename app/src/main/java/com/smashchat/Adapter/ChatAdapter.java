package com.smashchat.Adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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

    public interface OnMessageUpdateListener {
        void onMessageEdited(Messages message, String newMessage);
    }

    private OnMessageUpdateListener updateListener;

    public ChatAdapter(ArrayList<Messages> messageList, Context context, String recId) {
        this.messageList = messageList;
        this.context = context;
        this.recId = recId;
    }

    public void setOnMessageUpdateListener(OnMessageUpdateListener listener) {
        this.updateListener = listener;
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
            
            senderViewHolder.txtEdited.setVisibility(messageModel.isEdited() ? View.VISIBLE : View.GONE);

            if (messageModel.getType() == 1) {
                // Smile Emoji
                senderViewHolder.senderMsg.setText("");
                senderViewHolder.senderMsg.setBackground(null);
                senderViewHolder.senderMsg.setCompoundDrawablesWithIntrinsicBounds(null, null, ContextCompat.getDrawable(context, R.drawable.smile), null);
                senderViewHolder.senderMsg.setPadding(0, 0, 0, 0);
                senderViewHolder.senderMsg.setOnLongClickListener(null);
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

                senderViewHolder.senderMsg.setOnLongClickListener(v -> {
                    senderViewHolder.optionsLayout.setVisibility(View.VISIBLE);
                    return true;
                });
            }

            senderViewHolder.btnCopy.setOnClickListener(v -> {
                copyToClipboard(messageModel.getMessage());
                senderViewHolder.optionsLayout.setVisibility(View.GONE);
            });

            senderViewHolder.btnEdit.setOnClickListener(v -> {
                senderViewHolder.optionsLayout.setVisibility(View.GONE);
                senderViewHolder.editControlsLayout.setVisibility(View.VISIBLE);
                enableEditing(senderViewHolder.senderMsg, true);
            });

            senderViewHolder.btnCancel.setOnClickListener(v -> {
                senderViewHolder.editControlsLayout.setVisibility(View.GONE);
                enableEditing(senderViewHolder.senderMsg, false);
                senderViewHolder.senderMsg.setText(messageModel.getMessage());
            });

            senderViewHolder.btnOkay.setOnClickListener(v -> {
                String editedText = senderViewHolder.senderMsg.getText().toString().trim();
                if (!editedText.isEmpty()) {
                    if (updateListener != null) {
                        updateListener.onMessageEdited(messageModel, editedText);
                    }
                    senderViewHolder.editControlsLayout.setVisibility(View.GONE);
                    enableEditing(senderViewHolder.senderMsg, false);
                } else {
                    Toast.makeText(context, "Message cannot be empty", Toast.LENGTH_SHORT).show();
                }
            });

            senderViewHolder.senderTime.setText(formatTime(messageModel.getTimestamp()));
        } else {
            ReceiverViewHolder receiverViewHolder = (ReceiverViewHolder) holder;
            
            receiverViewHolder.txtEdited.setVisibility(messageModel.isEdited() ? View.VISIBLE : View.GONE);

            if (messageModel.getType() == 1) {
                // Smile Emoji
                receiverViewHolder.receiverMsg.setText("");
                receiverViewHolder.receiverMsg.setBackground(null);
                receiverViewHolder.receiverMsg.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(context, R.drawable.smile), null, null, null);
                receiverViewHolder.receiverMsg.setPadding(0, 0, 0, 0);
                receiverViewHolder.receiverMsg.setOnLongClickListener(null);
            } else {
                // Text Message
                receiverViewHolder.receiverMsg.setText(messageModel.getMessage());
                receiverViewHolder.receiverMsg.setBackgroundResource(R.drawable.receiver_bg);
                receiverViewHolder.receiverMsg.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, context.getResources().getDisplayMetrics());
                receiverViewHolder.receiverMsg.setPadding(padding, padding, padding, padding);

                receiverViewHolder.receiverMsg.setOnLongClickListener(v -> {
                    receiverViewHolder.optionsLayout.setVisibility(View.VISIBLE);
                    return true;
                });
            }

            receiverViewHolder.btnCopy.setOnClickListener(v -> {
                copyToClipboard(messageModel.getMessage());
                receiverViewHolder.optionsLayout.setVisibility(View.GONE);
            });

            receiverViewHolder.receiverTime.setText(formatTime(messageModel.getTimestamp()));
        }
    }

    private void enableEditing(EditText editText, boolean enable) {
        if (enable) {
            editText.setFocusable(true);
            editText.setFocusableInTouchMode(true);
            editText.setClickable(true);
            editText.requestFocus();
            editText.setSelection(editText.getText().length());
            
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            editText.setFocusable(false);
            editText.setFocusableInTouchMode(false);
            
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
            }
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Copied Message", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "Text Copied", Toast.LENGTH_SHORT).show();
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
        EditText receiverMsg;
        TextView receiverTime, txtEdited;
        LinearLayout optionsLayout;
        ImageView btnCopy;

        public ReceiverViewHolder(@NonNull View itemView) {
            super(itemView);
            receiverMsg = itemView.findViewById(R.id.receiverText);
            receiverTime = itemView.findViewById(R.id.receiverTime);
            txtEdited = itemView.findViewById(R.id.txtEdited);
            optionsLayout = itemView.findViewById(R.id.optionsLayout);
            btnCopy = itemView.findViewById(R.id.btnCopy);
        }
    }

    public static class SenderViewHolder extends RecyclerView.ViewHolder {
        EditText senderMsg;
        TextView senderTime, txtEdited, btnCancel, btnOkay;
        LinearLayout optionsLayout, editControlsLayout;
        ImageView btnCopy, btnEdit;

        public SenderViewHolder(@NonNull View itemView) {
            super(itemView);
            senderMsg = itemView.findViewById(R.id.senderText);
            senderTime = itemView.findViewById(R.id.senderTime);
            txtEdited = itemView.findViewById(R.id.txtEdited);
            optionsLayout = itemView.findViewById(R.id.optionsLayout);
            editControlsLayout = itemView.findViewById(R.id.editControlsLayout);
            btnCopy = itemView.findViewById(R.id.btnCopy);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnCancel = itemView.findViewById(R.id.btnCancel);
            btnOkay = itemView.findViewById(R.id.btnOkay);
        }
    }
}
