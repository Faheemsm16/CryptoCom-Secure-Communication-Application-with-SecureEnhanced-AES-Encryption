package com.example.cryptochat;

public class Message {

    private String sender_id;
    private String receiver_id;
    private String receiverName;
    private String receiver_no;
    private String messageText;
    private String sender_no;
    private String timestamp;
    private String date;
//    private boolean sent;
    private String chatRoomId;
    private String messageId; // Add this line

    public Message(String sender_id, String receiver_id, String receiver_no, String receiverName, String sender_no, String messageText, String timestamp, String date) {
        this.sender_id = sender_id;
        this.receiver_id = receiver_id;
        this.receiver_no = receiver_no;
        this.receiverName = receiverName;
        this.sender_no = sender_no;
        this.messageText = messageText;
        this.timestamp = timestamp;
        this.date = date;
    }

    public Message() {
        // Default, no-argument constructor required by Firebase
    }

    public String getChatRoomId() {
        return chatRoomId;
    }

    public void setChatRoomId(String chatRoomId) {
        this.chatRoomId = chatRoomId;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setSender_id(String sender_id) {
        this.sender_id = sender_id;
    }
    public String getReceiver_no() {
        return receiver_no;
    }

    public void setReceiver_id(String receiver_id) {
        this.receiver_id = receiver_id;
    }
    public void setReceiver_no(String receiver_no) {
        this.receiver_no = receiver_no;
    }

    public String getSender_id() {
        return sender_id;
    }

    public String getReceiver_id() {
        return receiver_id;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

//    public boolean isSent() {
//        return sent;
//    }
//
//    public void setSent(boolean sent) {
//        this.sent = sent;
//    }
    public String getMessageId() {
        return messageId;
    }
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSender_no() {
        return sender_no;
    }

    public void setSender_no(String sender_no) {
        this.sender_no = sender_no;
    }
}
