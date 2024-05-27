package com.example.cryptochat;

//  Represents a chat message.
public class ChatMessage {
    private String senderId;
    private String senderName; // Add this field
    private String receiverId;
    private String receiverName; // Add this field
    private String messageText;
    private String sender_no;
    private long timestampMillis;
//    private boolean sent;
    private String encryptedMessageText; // Add this field

//Default constructor required for Firebase.
    public ChatMessage(String senderId, String messageReceiverId, String messageText, long timestamp) {
    }

// Constructor for initializing a chat message.
    public ChatMessage(String senderId, String receiverId, String messageText, long timestampMillis, String receiverName) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.messageText = messageText;
        this.timestampMillis = timestampMillis;
        this.receiverName = receiverName;
    }

    // Getters and setters

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSenderNumber() {
        // Implement this method to return the sender's phone number
        return sender_no;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public void setTimestampMillis(long timestampMillis) {
        this.timestampMillis = timestampMillis;
    }


    public String getReceiverName() {
        return receiverName;
    }

    public String getEncryptedMessageText() {
        return encryptedMessageText;
    }

    public void setEncryptedMessageText(String encryptedMessageText) {
        this.encryptedMessageText = encryptedMessageText;
    }

}
