package com.example.cryptochat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatItemAdapter extends RecyclerView.Adapter<ChatItemAdapter.ViewHolder> {

    private List<ChatItem> chatItems;
    private OnItemClickListener listener;
    private AES aes;

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public ChatItemAdapter(List<ChatItem> chatItems, OnItemClickListener listener, Context context) {
        this.chatItems = chatItems;
        this.listener = listener;
        this.aes = new AES(context);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false);
        return new ViewHolder(view);
    }

    // Replace this placeholder method with your actual implementation to get the current user's ID
    private String getCurrentUserId() {
        // Implement logic to retrieve the current user's ID
        // For example, if using Firebase Authentication:
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            return user.getUid();
        } else {
            return ""; // Return empty string or handle null case accordingly
        }
    }

    // Replace this placeholder method with your actual implementation to get the full name of a user from the database
    private void getFullNameFromDatabase(String userId, FullNameCallback callback) {
        // Implement logic to fetch the full name from the database based on the userId
        // Once the full name is retrieved, call the callback with the result
        // For example, if using Firebase Realtime Database:
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference().child("users").child(userId);
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String fullName = dataSnapshot.child("fullName").getValue(String.class);
                    if (fullName != null) {
                        callback.onFullNameReceived(fullName);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Handle any errors
            }
        });
    }

    // Define a functional interface to handle the callback for retrieving full name
    private interface FullNameCallback {
        void onFullNameReceived(String fullName);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ChatItem chatItem = chatItems.get(holder.getAdapterPosition());

        if (chatItem == null) {
            return; // Ensure chatItem is not null
        }

        // Get the last message from the chat item
        ChatMessage latestMessage = chatItem.getLatestMessage();

        if (latestMessage == null) {
            return; // Ensure latestMessage is not null
        }

        // Set the contact name (assuming contactNameTextView is already set correctly)
        holder.contactNameTextView.setText(chatItem.getContactName());

        // Decrypt and set the message text
        String decryptedMessage = aes.Decrypt(latestMessage.getEncryptedMessageText(), holder.itemView.getContext());
        holder.messageTextView.setText(decryptedMessage);

        // Set the timestamp
        if (latestMessage.getTimestampMillis() > 0) {
            long timestampMillis = latestMessage.getTimestampMillis();
            String formattedDate = formatDate(timestampMillis);
            holder.timestampTextView.setText(formattedDate);
        } else {
            holder.timestampTextView.setText("");
        }

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(holder.getAdapterPosition());
            }
        });
    }

    private String formatDate(long timestampMillis) {
        if (timestampMillis <= 0) {
            return "";
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestampMillis));
    }

    @Override
    public int getItemCount() {
        return chatItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView contactNameTextView;
        TextView messageTextView;
        TextView timestampTextView;

        public ViewHolder(View itemView) {
            super(itemView);
            contactNameTextView = itemView.findViewById(R.id.contactNameTextView);
            messageTextView = itemView.findViewById(R.id.messageTextView);
            timestampTextView = itemView.findViewById(R.id.timestampTextView);
        }
    }

    // Method to update the dataset and notify the adapter
    public void updateDataSet(List<ChatItem> updatedData) {
        this.chatItems = updatedData;
        notifyDataSetChanged();
    }
}