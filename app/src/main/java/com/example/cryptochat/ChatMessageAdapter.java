package com.example.cryptochat;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

//Adapter class for displaying chat messages in a RecyclerView.
public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int SENT_MESSAGE_TYPE = 0;
    private static final int RECEIVED_MESSAGE_TYPE = 1;

    private List<Message> messageList;
    private String currentUserSenderId;

//Constructor for the ChatMessageAdapter.
    public ChatMessageAdapter(List<Message> messageList, String currentUserSenderId) {
        this.messageList = messageList;
        this.currentUserSenderId = currentUserSenderId;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messageList.get(position);
        return message.getSender_id().equals(currentUserSenderId) ? SENT_MESSAGE_TYPE : RECEIVED_MESSAGE_TYPE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == SENT_MESSAGE_TYPE) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_sent, parent, false);
            return new SentMessageViewHolder(view);
        } else {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_received, parent, false);
            return new ReceivedMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messageList.get(position);
        if (holder instanceof SentMessageViewHolder) {
            SentMessageViewHolder sentHolder = (SentMessageViewHolder) holder;
            sentHolder.bind(message);
        } else if (holder instanceof ReceivedMessageViewHolder) {
            ReceivedMessageViewHolder receivedHolder = (ReceivedMessageViewHolder) holder;
            receivedHolder.bind(message);
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

//ViewHolder class for sent messages.
    public class SentMessageViewHolder extends RecyclerView.ViewHolder {

        private TextView sentMessageText;
        private TextView sentMessageTime;

        public SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            sentMessageText = itemView.findViewById(R.id.text_message_body_sent);
            sentMessageTime = itemView.findViewById(R.id.text_message_time_sent);
        }

        public void bind(Message message) {
            // Bind data for sent message layout here
            sentMessageText.setText(message.getMessageText());
            sentMessageTime.setText(message.getTimestamp());

            // Set the gravity to end for sent messages (right alignment)
            sentMessageText.setGravity(Gravity.END);
            sentMessageTime.setGravity(Gravity.END);
        }
    }

//ViewHolder class for received messages.
    public class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {

        private TextView receivedMessageText;
        private TextView receivedMessageTime;

        public ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            receivedMessageText = itemView.findViewById(R.id.text_message_body_received);
            receivedMessageTime = itemView.findViewById(R.id.text_message_time_received);
        }

        public void bind(Message message) {
            // Bind data for received message layout here
            receivedMessageText.setText(message.getMessageText());
            receivedMessageTime.setText(message.getTimestamp());

            // Set the gravity to start for received messages (left alignment)
            receivedMessageText.setGravity(Gravity.START);
            receivedMessageTime.setGravity(Gravity.START);
        }
    }
}
