package com.example.cryptochat;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

//Represents a chat item with information about a contact and their messages.
    public class ChatItem {
        private String contactId; // ID of the contact
        private String contactName; // Name of the contact
        private List<ChatMessage> messages; // List of messages in the chat
        private String timestampString; // Timestamp string
    private String receiverId;
        private ChatMessage latestMessage;


        // Default constructor required for Firebase
        public ChatItem() {
        }

    //Constructor to create a ChatItem.
        public ChatItem(String contactId, String contactName, List<ChatMessage> messages) {
            this.contactId = contactId;
            this.contactName = contactName;
            this.messages = messages;
            updateLatestMessage();
        }

    //Get the ID of the contact.
        public String getContactId() {
            return contactId;
        }

    //Set the ID of the contact.
        public void setContactId(String contactId) {
            this.contactId = contactId;
        }

    //Get the name of the contact.
        public String getContactName() {
            return contactName;
        }

    //Set the name of the contact.
        public void setContactName(String contactName) {
            this.contactName = contactName;
        }

    //Get the list of messages in the chat.
        public List<ChatMessage> getMessages() {
            return messages;
        }

    //Set the list of messages in the chat.
        public void setMessages(List<ChatMessage> messages) {
            this.messages = messages;
        }


    // Set the last message text for the chat item
    public void setLastMessageText(String lastMessageText) {
        if (latestMessage != null) {
            latestMessage.setMessageText(lastMessageText);
        }
    }

        public void setLatestMessage(ChatMessage latestMessage) {
            this.latestMessage = latestMessage;
        }

        // Set the last message in the chat item.
        public void setLastMessage(String lastMessage) {
            if (latestMessage != null) {
                latestMessage.setMessageText(lastMessage);
            }
        }


        // Set the timestamp of the latest message in the chat item.
        public void setTimestamp(long timestamp) {
            if (latestMessage != null) {
                latestMessage.setTimestampMillis(timestamp);
            }
        }

        public long getTimestamp() {
            if (latestMessage != null) {
                return latestMessage.getTimestampMillis();
            } else {
                return 0; // Return 0 if there is no latest message or the latest message has no timestamp
            }
        }

    // Get the latest message in the chat item
    public ChatMessage getLatestMessage() {
        return latestMessage;
    }

    // Add a chat message to the list of messages
    public void addChatMessage(ChatMessage message) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(message);
        updateLatestMessage();
        // Log the added message for debugging
        Log.d("ChatItem", "Added message to ChatItem: " + message.getMessageText());
    }

    // Update the latest message in the chat item
    private void updateLatestMessage() {
        if (messages != null && !messages.isEmpty()) {
            // Find the message with the latest timestamp
            ChatMessage newLatestMessage = null;
            for (ChatMessage message : messages) {
                if (newLatestMessage == null || message.getTimestampMillis() > newLatestMessage.getTimestampMillis()) {
                    newLatestMessage = message;
                }
            }
            latestMessage = newLatestMessage;
        } else {
            latestMessage = null;
        }
    }

        //Get the timestamp string.
        public String getTimestampString() {
            return timestampString;
        }

    //Set the timestamp string.
        public void setTimestampString(String timestampString) {
            this.timestampString = timestampString;
        }

    public void setReceiverId(String receiverId) {this.receiverId = receiverId;}
}