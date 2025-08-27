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


        // Get the latest message in the chat item
        // Method to add a chat message to the list
        // Method to retrieve the latest message from the list
    //    public ChatMessage getLatestMessage() {
    //        // Check if the messages list is empty
    //        if (messages.isEmpty()) {
    //            return null; // Return null if there are no messages
    //        } else {
    //            // Get the last message in the list (assuming messages are sorted by timestamp)
    //            return messages.get(messages.size() - 1);
    //        }
    //    }

    //    public ChatMessage getLatestMessage() {
    //        if (messages != null && !messages.isEmpty()) {
    //            // Check if the messages list is not null and not empty
    //            // Return the latest message
    //            return messages.get(messages.size() - 1);
    //        } else {
    //            // Return null if the messages list is null or empty
    //            return null;
    //        }
    //    }

    //    public ChatMessage getLatestMessage() {
    //        ChatMessage latestMessage = null;
    //        long latestTimestamp = Long.MIN_VALUE; // Initialize with the smallest possible value
    //
    //        // Iterate through the messages list to find the message with the latest timestamp
    //        for (ChatMessage message : messages) {
    //            if (message.getTimestampMillis() > latestTimestamp) {
    //                latestTimestamp = message.getTimestampMillis();
    //                latestMessage = message;
    //            }
    //        }
    //
    //        return latestMessage;
    //    }

//        public ChatMessage getLatestMessage() {
//            if (messages == null || messages.isEmpty()) {
//                return null;
//            }
//
//            ChatMessage latestMessage = null;
//            long latestTimestamp = Long.MIN_VALUE; // Initialize with the smallest possible value
//
//            // Iterate through the messages list to find the message with the latest timestamp
//            for (ChatMessage message : messages) {
//                if (message != null && message.getTimestampMillis() > latestTimestamp) {
//                    latestTimestamp = message.getTimestampMillis();
//                    latestMessage = message;
//                }
//            }
//
//            return latestMessage;
//        }

    // Set the last message text for the chat item
    public void setLastMessageText(String lastMessageText) {
        if (latestMessage != null) {
            latestMessage.setMessageText(lastMessageText);
        }
    }



    //    public ChatMessage getLatestMessage() {
    //        if (messages != null && !messages.isEmpty()) {
    //            return messages.get(0);
    //        }
    //        return null;
    //    }

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

    //    public void addChatMessage(ChatMessage message) {
    //        if (messages == null) {
    //            messages = new ArrayList<>();
    //        }
    //        messages.add(message);
    //        // Update the latest message when a new message is added
    //        latestMessage = message;
    //    }

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
//        public void addChatMessage(ChatMessage message) {
//            if (messages == null) {
//                messages = new ArrayList<>();
//            }
//            messages.add(message);
//            // Check if the added message is newer than the current latest message
//            if (latestMessage == null || message.getTimestampMillis() > latestMessage.getTimestampMillis()) {
//                latestMessage = message;
//            }
//        }




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



//Get the latest message in the chat.
//    public ChatMessage getLatestMessage() {
//        if (messages != null && !messages.isEmpty()) {
//            return messages.get(0);
//        }
//        return null;
//    }

//    public ChatMessage getLatestMessage() {
//        if (messages != null && !messages.isEmpty()) {
//            // Sort messages based on timestamp to ensure the latest message is first
//            Collections.sort(messages, (msg1, msg2) -> Long.compare(msg2.getTimestampMillis(), msg1.getTimestampMillis()));
//            return messages.get(0); // Return the first message, which is the latest
//        }
//        return null; // Return null if no messages exist
//    }

//    public ChatMessage getLatestMessage() {
//        ChatMessage latestMessage = null;
//        long latestTimestamp = 0;
//
//        if (messages != null && !messages.isEmpty()) {
//            for (ChatMessage message : messages) {
//                if (message.getTimestampMillis() > latestTimestamp) {
//                    latestTimestamp = message.getTimestampMillis();
//                    latestMessage = message;
//                }
//            }
//        }
//
//        return latestMessage;
//    }

//    public ChatMessage getLatestMessage() {
//        if (messages == null || messages.isEmpty()) {
//            return null; // Return null if no messages exist
//        }
//
//        // Sort messages based on timestamp in descending order
//        Collections.sort(messages, (msg1, msg2) -> Long.compare(msg2.getTimestampMillis(), msg1.getTimestampMillis()));
//
//        // Return the first message (latest message) in the sorted list
//        return messages.get(0);
//    }


//    public ChatMessage getLatestMessage() {
//        ChatMessage latestMessage = null;
//        long latestTimestamp = 0;
//
//        if (messages != null && !messages.isEmpty()) {
//            for (ChatMessage message : messages) {
//                if (message.getTimestampMillis() > latestTimestamp) {
//                    latestTimestamp = message.getTimestampMillis();
//                    latestMessage = message;
//                }
//            }
//        }
//
//        return latestMessage;
//    }

//    public void addChatMessage(ChatMessage message, String currentUserId) {
//        if (messages == null) {
//            messages = new ArrayList<>();
//        }
//        messages.add(message);
//
//        // Update latestMessage if it's null or the new message is more recent and sent or received by the current user
//        if (latestMessage == null ||
//                (message.getTimestampMillis() > latestMessage.getTimestampMillis() &&
//                        (message.getSenderId().equals(currentUserId) ||
//                                message.getReceiverId().equals(currentUserId)))) {
//            latestMessage = message;
//        }
//    }


//    public ChatMessage getLatestMessage() {
//        return latestMessage;
//    }