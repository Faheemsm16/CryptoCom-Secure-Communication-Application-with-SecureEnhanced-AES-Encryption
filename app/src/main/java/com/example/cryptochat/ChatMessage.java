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

    // Method to check if the message was sent by the current user
//    public boolean isSentByCurrentUser(String currentUserUid) {
//        return senderId.equals(currentUserUid);
//    }
    public String getSenderNumber() {
        // Implement this method to return the sender's phone number
        return sender_no;
    }
//    public void setSenderPhoneNumber(String sender_no) {
//        this.sender_no = sender_no;
//    }
    public String getReceiverId() {
        return receiverId;
    }

//    public void setReceiverId(String receiverId) {
//        this.receiverId = receiverId;
//    }

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

    // Getter method for sent flag
//    public boolean isSent() {
//        return sent;
//    }

//    public String getSenderName() {
//        return senderName;
//    }

//    public void setSenderName(String senderName) {
//        this.senderName = senderName;
//    }

    public String getReceiverName() {
        return receiverName;
    }

//    public void setReceiverName(String receiverName) {
//        this.receiverName = receiverName;
//    }

    public String getEncryptedMessageText() {
        return encryptedMessageText;
    }

    public void setEncryptedMessageText(String encryptedMessageText) {
        this.encryptedMessageText = encryptedMessageText;
    }

//    public String getSenderNo() { return sender_no;
//    }

//    public void setSender_no(String sender_no) {
//        this.sender_no= sender_no;
//    }

}
