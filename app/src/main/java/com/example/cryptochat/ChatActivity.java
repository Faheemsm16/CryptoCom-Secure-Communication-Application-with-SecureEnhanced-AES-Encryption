package com.example.cryptochat;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ChatActivity extends AppCompatActivity {
        public static final String FRIEND_ID = "friendId";
        public static final String FRIEND_NAME = "friendName";
        public static final int DEFAULT_MSG_LENGTH_LIMIT = 500;
//        public static final String EXTRA_SELECTED_FRIEND_NAME = "selectedFriendName";
        public static final String EXTRA_LAST_MESSAGE = "lastMessage";
//        public static final String EXTRA_TIMESTAMP = "timestamp";
        private AES aes;
        private ChatMessageAdapter messageAdapter;
        private RecyclerView messageRecyclerView;
        private List<Message> messageList = new ArrayList<>();
        private EditText messageEditText;
        private ImageView send_button;
        private FirebaseDatabase mFirebaseDatabase;
        private ChildEventListener mChildEventListener;
        private DatabaseReference mDatabaseReference, onlineRef;
        private DateAndTime dt;
        private Lock sendMessageLock = new ReentrantLock();
        private String selectedFriendId;
        private String selectedFriendName;
        private boolean isSendingMessage = false;
        private String selectedFriendPhoneNumber;
//        static final int REQUEST_CHAT_ACTIVITY = 1; // Replace with your request code if needed
        private static final String SHARED_PREF_KEY_PHONE_NUMBER = "phoneNumber";
//        public static final String CURRENT_USER_PHONE_NUMBER = "currentUserPhoneNumber"; // Define the constant here
        public static final String SELECTED_FRIEND_PHONE_NUMBER = "selected_friend_phone_number";
        private static final String CHAT_ROOM_ID_PREFIX = "chat_";
        private static final String COUNTRY_CODE = "+91";

        @Override
        protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_chat);
                // Initialize Firebase Crashlytics
                FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);

                TextView friend_name_text_view = findViewById(R.id.chat_activity_friend_name);
                TextView friendPhoneTextView = findViewById(R.id.chat_activity_friend_phone);
                final TextView lastSeenOrOnlineText = findViewById(R.id.last_seen_text_view);

                // Get the selected friend's ID and name from the intent
                selectedFriendId = getIntent().getStringExtra(FRIEND_ID);
                selectedFriendName = getIntent().getStringExtra(FRIEND_NAME);
                selectedFriendPhoneNumber = getIntent().getStringExtra(SELECTED_FRIEND_PHONE_NUMBER);

                // Get the selected friend's phone number from the intent extras
                if (selectedFriendPhoneNumber == null || selectedFriendPhoneNumber.isEmpty()) {
                        // Phone number not provided in intent extras, try to fetch from contacts
                        getSelectedContactPhoneNumber();
                }

                // Assuming you have properly set selectedFriendPhoneNumber at this point
                if (selectedFriendPhoneNumber != null && !selectedFriendPhoneNumber.isEmpty()) {
                        // Fetch the full name of the contact from Firebase using the selected friend's phone number
                        fetchFullNameFromFirebase(selectedFriendPhoneNumber);
                } else {
                        // Handle the case where the phone number is not available
                        TextView friendNameTextView = findViewById(R.id.chat_activity_friend_name);
                        friendNameTextView.setText("Phone number not available");
                }

                // Retrieve the phone number from SharedPreferences
                SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
                String phoneNumber = sharedPreferences.getString(SHARED_PREF_KEY_PHONE_NUMBER, "");

                // Set the Friend's Name TextView to display the selected friend's phone number
                friendPhoneTextView.setText(selectedFriendPhoneNumber);

                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                String currentUserId = (currentUser != null) ? currentUser.getUid() : "";

                friend_name_text_view.setText(selectedFriendName);

                send_button = findViewById(R.id.send_button);
                messageRecyclerView = findViewById(R.id.recycler_view_messages);
                LinearLayoutManager layoutManager = new LinearLayoutManager(this);
                layoutManager.setStackFromEnd(true);
                messageRecyclerView.setLayoutManager(layoutManager);
                messageRecyclerView.setHasFixedSize(true);

                messageAdapter = new ChatMessageAdapter(messageList, currentUserId);
                messageRecyclerView.setAdapter(messageAdapter);
                messageEditText = findViewById(R.id.message_edit_text);
                dt = new DateAndTime();
                aes = new AES(this);

                // Check if Firebase Database is properly initialized
                if (FirebaseDatabase.getInstance() == null) {
                        // Handle the case where Firebase Database is not initialized
                        // You may want to show an error message or close the activity
                        return;
                }

                mFirebaseDatabase = FirebaseDatabase.getInstance();
                onlineRef = mFirebaseDatabase.getReference("users").child(selectedFriendId);

                // Get the currently signed-in user's display name from Firebase (if available)
                String currentUserDisplayName = (currentUser != null) ? currentUser.getDisplayName() : null;
                getSelectedContactPhoneNumber();

                // Pass the user names to generateChatRoomId
                String chatRoomId = generateChatRoomId(currentUserDisplayName, selectedFriendName);

                // Initialize the database reference for the chat messages
                init();

                setEditText();

                // Set the DatabaseReference for MyFirebaseMessagingService
                MyFirebaseMessagingService messagingService = new MyFirebaseMessagingService();
                messagingService.setDatabaseReference(mDatabaseReference);

                // Pass the correct friendId to fetchMessages
                fetchMessages();

//                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("/users");
//
//                ref.orderByChild("phoneNumber").equalTo(selectedFriendPhoneNumber).addListenerForSingleValueEvent(new ValueEventListener() {
//                        @Override
//                        public void onDataChange(@NonNull DataSnapshot snapshot) {
//                                if (snapshot.exists()) {
//                                        // Get the first matching user (assuming phone numbers are unique)
//                                        DataSnapshot userSnapshot = snapshot.getChildren().iterator().next();
//                                        String status = String.valueOf(userSnapshot.child("isOnline").getValue());
//
//                                        // Set the status to the lastSeenOrOnlineText TextView
//                                        lastSeenOrOnlineText.setText(status);
//                                } else {
//                                        // Receiver's number is not present, display appropriate message
//                                        lastSeenOrOnlineText.setText("Receiver hasn't created an account");
//                                }
//                        }
//
//                        @Override
//                        public void onCancelled(@NonNull DatabaseError error) {
//                                // Handle onCancelled if needed
//                        }
//                });

                send_button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                                sendMessage();
                        }
                });
        }
        private void setEditText() {
                messageEditText.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                                messageRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                                messageRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
                                if (s.toString().trim().length() > 0) {
                                        send_button.setEnabled(true);
                                } else {
                                        send_button.setEnabled(false);
                                }
                        }

                        @Override
                        public void afterTextChanged(Editable s) {

                        }
                });
                messageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});
        }
        // Initialize the database reference for the chat messages
        private void init() {
                SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
                String phoneNumber = sharedPreferences.getString("phoneNumber", "");

                Log.d("Chat Room ID", "Current User's Phone Number: " + phoneNumber);
                Log.d("Chat Room ID", "Selected Friend's Phone Number: " + selectedFriendPhoneNumber);

                // Generate a unique chat room ID based on the phone numbers
                String chatRoomId = generateChatRoomId(phoneNumber, selectedFriendPhoneNumber);
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                String currentUserId = (currentUser != null) ? currentUser.getUid() : "";

                // Check if chatRoomId is not null before creating the database reference
                if (chatRoomId != null) {
                        mDatabaseReference = mFirebaseDatabase.getReference().child("messages").child(chatRoomId);

                        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
                        layoutManager.setStackFromEnd(true);
                        messageRecyclerView.setLayoutManager(layoutManager);
                        messageRecyclerView.setHasFixedSize(true);

                        // Pass the current user's sender ID when initializing the ChatMessageAdapter
                        messageAdapter = new ChatMessageAdapter(messageList, currentUserId);
                        messageRecyclerView.setAdapter(messageAdapter);
                } else {
                        // Handle the case where chatRoomId is null
                        Log.e("Init", "chatRoomId is null");
                }
        }
        // Modify the fetchMessages method
        private void fetchMessages() {
                SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
                String phoneNumber = sharedPreferences.getString(SHARED_PREF_KEY_PHONE_NUMBER, "");
                Log.d("Chat Room ID", "Current User's Phone Number: " + phoneNumber);
                Log.d("Chat Room ID", "Selected Friend's Phone Number: " + selectedFriendPhoneNumber);

                String chatRoomId = generateChatRoomId(phoneNumber, selectedFriendPhoneNumber);
                mDatabaseReference = mFirebaseDatabase.getReference().child("messages").child(chatRoomId);

                mChildEventListener = new ChildEventListener() {
                        @Override
                        public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                                Message message = snapshot.getValue(Message.class);
                                if (message != null) {
                                        // Decrypt the message and display it
                                        String decryptedMessage = aes.Decrypt(message.getMessageText(), com.example.cryptochat.ChatActivity.this);

                                        // Create a new Message object for displaying in the app with the decrypted message
                                        Message displayMessage = new Message();
                                        displayMessage.setMessageText(decryptedMessage); // Use the decrypted message text
                                        displayMessage.setSender_id(message.getSender_id()); // Use the sender's ID from the database
                                        displayMessage.setReceiver_id(message.getReceiver_id());
                                        displayMessage.setTimestamp(message.getTimestamp());
                                        displayMessage.setReceiver_no(message.getReceiver_no());

                                        displayMessage.setDate(message.getDate());
//                                        displayMessage.setSent(message.isSent());

                                        // Add the displayMessage to the list for displaying in the app
                                        messageList.add(displayMessage);
                                        messageAdapter.notifyDataSetChanged();
                                        messageRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
                                } else {
                                        // Handle the case where the message is null
                                        Log.e("Message", "Received a null message");
                                }
                        }

                        @Override
                        public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                        }
                        @Override
                        public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                                // Handle message removal if needed
                        }

                        @Override
                        public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                                // Handle message movement if needed
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                                // Handle onCancelled if needed
                        }
                };

                mDatabaseReference.addChildEventListener(mChildEventListener);
        }
        private void fetchFullNameFromFirebase(String phoneNumber) {
                DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
                usersRef.child(phoneNumber).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (snapshot.exists()) {
                                        // Get the full name from the snapshot
                                        String fullName = snapshot.child("fullName").getValue(String.class);
                                        if (fullName != null && !fullName.isEmpty()) {
                                                // Set the selected friend's name to the retrieved full name
                                                TextView friendNameTextView = findViewById(R.id.chat_activity_friend_name);
                                                friendNameTextView.setText(fullName);
                                        } else {
                                                // Set a message indicating that the full name is not available
                                                TextView friendNameTextView = findViewById(R.id.chat_activity_friend_name);
                                                friendNameTextView.setText("Full name not available");
                                        }
                                } else {
                                        // Set a message indicating that the user hasn't created an account
                                        TextView friendNameTextView = findViewById(R.id.chat_activity_friend_name);
                                        friendNameTextView.setText("User hasn't created an account");
                                }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                                // Handle onCancelled if needed
                                Log.e("FirebaseError", "Error fetching full name: " + error.getMessage());
                        }
                });
        }
        //        private void fetchFullNameFromFirebase(String phoneNumber) {
//                DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
//                usersRef.orderByChild("phoneNumber").equalTo(phoneNumber).addListenerForSingleValueEvent(new ValueEventListener() {
//                        @Override
//                        public void onDataChange(@NonNull DataSnapshot snapshot) {
//                                if (snapshot.exists()) {
//                                        // Get the first matching user (assuming phone numbers are unique)
//                                        DataSnapshot userSnapshot = snapshot.getChildren().iterator().next();
//                                        String fullName = userSnapshot.child("fullName").getValue(String.class);
//                                        if (fullName != null && !fullName.isEmpty()) {
//                                                // Set the selected friend's name to the retrieved full name
//                                                TextView friendNameTextView = findViewById(R.id.chat_activity_friend_name);
//                                                friendNameTextView.setText(fullName);
//                                        } else {
//                                                // Set a message indicating that the full name is not available
//                                                TextView friendNameTextView = findViewById(R.id.chat_activity_friend_name);
//                                                friendNameTextView.setText("Full name not available");
//                                        }
//                                } else {
//                                        // Set a message indicating that the user hasn't created an account
//                                        TextView friendNameTextView = findViewById(R.id.chat_activity_friend_name);
//                                        friendNameTextView.setText("User hasn't created an account");
//                                }
//                        }
//
//                        @Override
//                        public void onCancelled(@NonNull DatabaseError error) {
//                                // Handle onCancelled if needed
//                                Log.e("FirebaseError", "Error fetching full name: " + error.getMessage());
//                        }
//                });
//        }
        private String generateChatRoomId(String phoneNumber, String selectedFriendPhoneNumber) {
                if (phoneNumber != null && selectedFriendPhoneNumber != null) {
                        // Sort the phone numbers to ensure consistency in the chat room ID
                        List<String> phoneNumbers = new ArrayList<>();
                        phoneNumbers.add(phoneNumber);
                        phoneNumbers.add(selectedFriendPhoneNumber);
                        Collections.sort(phoneNumbers);

                        // Combine the sorted phone numbers to create the chat room ID
                        String chatRoomId = CHAT_ROOM_ID_PREFIX + phoneNumbers.get(0) + "_" + phoneNumbers.get(1);

                        Log.d("Chat Room ID", chatRoomId);
                        return chatRoomId;
                } else {
                        return "unknown_chat_room";
                }
        }
        private void getSelectedContactPhoneNumber() {
                // Assuming you have the selected contact's name in selectedFriendName
                if (selectedFriendName == null || selectedFriendName.isEmpty()) {
                        return; // Exit the method if selectedFriendName is null or empty
                }

                // Assuming you have the selected contact's name in selectedFriendName
                String contactName = selectedFriendName;

                // Define the projection for the query
                String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};

                // Define the selection criteria (search by contact name)
                String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " = ?";
                String[] selectionArgs = {contactName};

                // Perform the query to retrieve the phone number
                Cursor cursor = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        null
                );

                if (cursor != null) {
                        try {
                                if (cursor.moveToFirst()) {
                                        // Retrieve the phone number from the query result
                                        int phoneNumberColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                                        selectedFriendPhoneNumber = cursor.getString(phoneNumberColumnIndex);

                                        // Remove spaces from the selectedFriendPhoneNumber
                                        selectedFriendPhoneNumber = selectedFriendPhoneNumber.replaceAll("\\s+", "");

                                        // Add country code if it's not already present
                                        if (!selectedFriendPhoneNumber.startsWith("+")) {
                                                // Assuming the country code is +91
                                                selectedFriendPhoneNumber = COUNTRY_CODE + selectedFriendPhoneNumber;
                                        }
                                }
                        } finally {
                                cursor.close();
                        }
                }
        }
        //        private void getSelectedContactPhoneNumber() {
//
//                // Assuming you have the selected contact's name in selectedFriendName
//                if (selectedFriendName == null || selectedFriendName.isEmpty()) {
//                        return; // Exit the method if selectedFriendName is null or empty
//                }
//
//                // Assuming you have the selected contact's name in selectedFriendName
//                String contactName = selectedFriendName;
//
//                // Define the projection for the query
//                String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};
//
//                // Define the selection criteria (search by contact name)
//                String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " = ?";
//                String[] selectionArgs = {contactName};
//
//                // Perform the query to retrieve the phone number
//                Cursor cursor = getContentResolver().query(
//                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
//                        projection,
//                        selection,
//                        selectionArgs,
//                        null
//                );
//
//                if (cursor != null) {
//                        try {
//                                if (cursor.moveToFirst()) {
//                                        // Retrieve the phone number from the query result
//                                        int phoneNumberColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
//                                        selectedFriendPhoneNumber = cursor.getString(phoneNumberColumnIndex);
//
//                                        // Remove spaces from the selectedFriendPhoneNumber
//                                        selectedFriendPhoneNumber = selectedFriendPhoneNumber.replaceAll("\\s+", "");
//
//                                        // Add country code if it's not already present
//                                        if (!selectedFriendPhoneNumber.startsWith("+")) {
//                                                // Assuming the country code is +91
//                                                selectedFriendPhoneNumber = COUNTRY_CODE + selectedFriendPhoneNumber;
//                                        }
//                                }
//                        } finally {
//                                cursor.close();
//                        }
//                }
//        }
        // When sending a message

//        private void sendMessage() {
//                sendMessageLock.lock();
//                try {
//                        String messageText = messageEditText.getText().toString().trim();
//                        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
//                        String phoneNumber = sharedPreferences.getString("phoneNumber", "");
//
//                        if (messageText.isEmpty()) {
//                                return;
//                        }
//
//                        if (!isNetworkConnected()) {
//                                Toast.makeText(this, "Ensure you're connected to the Internet to send the message", Toast.LENGTH_SHORT).show();
//                                return;
//                        }
//
//                        // Check if the message is already being sent
//                        if (isSendingMessage) {
//                                return;
//                        }
//
//                        isSendingMessage = true;
//
//                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
//                        if (currentUser == null) {
//                                return;
//                        }
//
//                        String currentUserId = currentUser.getUid();
//                        Log.d("Chat Room ID", "Current User's Phone Number: " + phoneNumber);
//                        Log.d("Chat Room ID", "Selected Friend's Phone Number: " + selectedFriendPhoneNumber);
//
//                        String chatRoomId = generateChatRoomId(phoneNumber, selectedFriendPhoneNumber);
//                        String encryptedMessage = aes.Encrypt(messageText, this);
//
//                        // Query the database to fetch the userId based on selectedFriendPhoneNumber
//                        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users").child(selectedFriendPhoneNumber);
//                        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
//                                @Override
//                                public void onDataChange(DataSnapshot dataSnapshot) {
//                                        if (dataSnapshot.exists()) {
//                                                // User data found, retrieve userId
//                                                String userId = dataSnapshot.child("userId").getValue(String.class);
//                                                if (userId != null) {
//                                                        // Create the Message object
//                                                        Message sentMessage = new Message();
//                                                        sentMessage.setMessageText(encryptedMessage);
//                                                        sentMessage.setSender_id(currentUserId);
////                                                        sentMessage.setReceiver_no(selectedFriendPhoneNumber);
//                                                        sentMessage.setReceiver_no(userId);
//                                                        sentMessage.setReceiver_id(selectedFriendPhoneNumber); // Set receiver's ID
//                                                        sentMessage.setReceiverName(selectedFriendName); // Include receiver's name
//                                                        sentMessage.setSender_no(phoneNumber); // Add sender's number
////                                                        sentMessage.setReceiver_id(userId); // Set receiver's ID
//
//                                                        sentMessage.setTimestamp(dt.getTimeWithSeconds());
//                                                        sentMessage.setDate(dt.getDATE());
//                                                        DatabaseReference chatRoomRef = mFirebaseDatabase.getReference("messages").child(chatRoomId).push();
//
//                                                        // Update the message in the database
//                                                        chatRoomRef.setValue(sentMessage, (databaseError, databaseReference) -> {
//                                                                if (databaseError == null) {
//                                                                        messageEditText.setText("");
////                                                                        Toast.makeText(ChatActivity.this, "Message sent", Toast.LENGTH_SHORT).show();
//
//                                                                } else {
//                                                                        Log.e("Message Sending", "Failed to send message: " + databaseError.getMessage());
//                                                                        Toast.makeText(ChatActivity.this, "Failed to send message. Please try again.", Toast.LENGTH_SHORT).show();
//                                                                }
//                                                                isSendingMessage = false; // Set sending message flag to false
//                                                        });
//                                                } else {
//                                                        // No user found with the given phone number
//                                                        Log.e("sendMessage", "No user found with the given phone number: " + selectedFriendPhoneNumber);
//                                                        Toast.makeText(ChatActivity.this, "User is not registered.", Toast.LENGTH_SHORT).show();
//                                                        isSendingMessage = false; // Set sending message flag to false
//                                                }
//                                        } else {
//                                                // User not found, handle the case where the user does not exist
//                                                Log.e("sendMessage", "No userId found for the user with phone number: " + selectedFriendPhoneNumber);
//                                                Toast.makeText(ChatActivity.this, "User is not registered.", Toast.LENGTH_SHORT).show();
//                                                isSendingMessage = false; // Set sending message flag to false
//                                        }
//                                }
//
//                                @Override
//                                public void onCancelled(DatabaseError databaseError) {
//                                        Log.e("sendMessage", "Database error: " + databaseError.getMessage());
//                                        Toast.makeText(ChatActivity.this, "Database error. Please try again.", Toast.LENGTH_SHORT).show();
//                                        isSendingMessage = false; // Set sending message flag to false
//                                }
//                        });
//                } finally {
//                        sendMessageLock.unlock();
//                }
//        }
//        working code:
//        private void sendMessage() {
//                sendMessageLock.lock();
//                try {
//                        String messageText = messageEditText.getText().toString().trim();
//                        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
//                        String phoneNumber = sharedPreferences.getString("phoneNumber", "");
//                        if (messageText.isEmpty()) {
//                                return;
//                        }
//                        if (!isNetworkConnected()) {
//                                Toast.makeText(this, "Ensure you're connected to the Internet to send the message", Toast.LENGTH_SHORT).show();
//                                return;
//                        }
//                        // Check if the message is already being sent
//                        if (isSendingMessage) {
//                                return;
//                        }
//                        isSendingMessage = true;
//
//                        // Check if the receiver is registered in the app
//                        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
//                        usersRef.orderByChild("phoneNumber").equalTo(selectedFriendPhoneNumber).addListenerForSingleValueEvent(new ValueEventListener() {
//                                @Override
//                                public void onDataChange(@NonNull DataSnapshot snapshot) {
//                                        if (snapshot.exists()) {
//
//                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
//                        if (currentUser == null) {
//                                return;
//                        }
//                        String currentUserDisplayName = (currentUser != null) ? currentUser.getDisplayName() : null;
//                        String currentUserId = currentUser.getUid();
//                        Log.d("Chat Room ID", "Current User's Phone Number: " + phoneNumber);
//                        Log.d("Chat Room ID", "Selected Friend's Phone Number: " + selectedFriendPhoneNumber);
//
//                        String chatRoomId = generateChatRoomId(phoneNumber, selectedFriendPhoneNumber);
//                        String encryptedMessage = aes.Encrypt(messageText, ChatActivity.this);
//
//                        // Include receiver's name in the Message object
//                        Message sentMessage = new Message();
//                        sentMessage.setMessageText(encryptedMessage);
//                        sentMessage.setSender_id(currentUserId);
//                        sentMessage.setReceiver_id(selectedFriendPhoneNumber);
//                        sentMessage.setReceiverName(selectedFriendName); // Include receiver's name
//                        sentMessage.setSender_no(phoneNumber); // Add sender's number
////                        sentMessage.setReceiver_no(selectedFriendId);
////                        sentMessage.setReceiver_no(selectedFriendName);
//
//                        sentMessage.setTimestamp(dt.getTimeWithSeconds());
//                        sentMessage.setDate(dt.getDATE());
//                        DatabaseReference chatRoomRef = mFirebaseDatabase.getReference("messages").child(chatRoomId).push();
//
//                        // Update the message in the database
//                        chatRoomRef.setValue(sentMessage, (databaseError, databaseReference) -> {
//                                if (databaseError == null) {
//                                        messageEditText.setText("");
//                                        isSendingMessage = false;
//                                } else {
//                                        Log.e("Message Sending", "Failed to send message: " + databaseError.getMessage());
//                                        isSendingMessage = false;
//                                        Toast.makeText(ChatActivity.this, "Failed to send message. Please try again.", Toast.LENGTH_SHORT).show();
//                                }
//                        });
//                        Intent intent = new Intent();
//                        intent.putExtra(EXTRA_SELECTED_FRIEND_NAME, selectedFriendName);
//                        intent.putExtra(EXTRA_LAST_MESSAGE, messageText);
//                        intent.putExtra(EXTRA_TIMESTAMP, dt.getTime());
//                        setResult(RESULT_OK, intent);
//                } else {
//                        // Receiver is not registered, display appropriate message
//                        Toast.makeText(ChatActivity.this, "Cannot send message. Receiver hasn't created an account.", Toast.LENGTH_SHORT).show();
//                }
//        }
//                        @Override
//                        public void onCancelled(@NonNull DatabaseError error) {
//                                // Handle onCancelled if needed
//                        }
//                });
//                } finally {
//                        sendMessageLock.unlock();
//                }
//        }
        private void sendMessage() {
        sendMessageLock.lock();
        try {
                String messageText = messageEditText.getText().toString().trim();
                SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
                String phoneNumber = sharedPreferences.getString("phoneNumber", "");

                if (messageText.isEmpty()) {
                        return;
                }

                if (!isNetworkConnected()) {
                        Toast.makeText(this, "Ensure you're connected to the Internet to send the message", Toast.LENGTH_SHORT).show();
                        return;
                }

                // Check if the message is already being sent
                if (isSendingMessage) {
                        return;
                }

                isSendingMessage = true;

                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser == null) {
                        return;
                }

                String currentUserId = currentUser.getUid();
                Log.d("Chat Room ID", "Current User's Phone Number: " + phoneNumber);
                Log.d("Chat Room ID", "Selected Friend's Phone Number: " + selectedFriendPhoneNumber);

                String chatRoomId = generateChatRoomId(phoneNumber, selectedFriendPhoneNumber);
                String encryptedMessage = aes.Encrypt(messageText, this);

                // Query the database to fetch the userId based on selectedFriendPhoneNumber
                DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users").child(selectedFriendPhoneNumber);
                usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                                if (dataSnapshot.exists()) {
                                        // User data found, retrieve userId
                                        String userId = dataSnapshot.child("userId").getValue(String.class);
                                        if (userId != null) {
                                                // Create the Message object
                                                Message sentMessage = new Message();
                                                sentMessage.setMessageText(encryptedMessage);
                                                sentMessage.setSender_id(currentUserId);
                                                sentMessage.setReceiver_id(selectedFriendPhoneNumber);
                                                sentMessage.setReceiverName(selectedFriendName); // Include receiver's name
                                                sentMessage.setSender_no(phoneNumber); // Add sender's number
                                                sentMessage.setReceiver_no(userId); // Set receiver's ID

                                                sentMessage.setTimestamp(dt.getTimeWithSeconds());
                                                sentMessage.setDate(dt.getDATE());
                                                DatabaseReference chatRoomRef = mFirebaseDatabase.getReference("messages").child(chatRoomId).push();

                                                // Update the message in the database
                                                chatRoomRef.setValue(sentMessage, (databaseError, databaseReference) -> {
                                                        if (databaseError == null) {
                                                                messageEditText.setText("");
//                                                                Toast.makeText(ChatActivity.this, "Message sent", Toast.LENGTH_SHORT).show();
                                                        } else {
                                                                Log.e("Message Sending", "Failed to send message: " + databaseError.getMessage());
                                                                Toast.makeText(ChatActivity.this, "Failed to send message. Please try again.", Toast.LENGTH_SHORT).show();
                                                        }
                                                        isSendingMessage = false; // Set sending message flag to false
                                                });
                                        } else {
                                                // No user found with the given phone number
                                                Log.e("sendMessage", "No user found with the given phone number: " + selectedFriendPhoneNumber);
                                                Toast.makeText(ChatActivity.this, "User with the selected phone number is not registered.", Toast.LENGTH_SHORT).show();
                                                isSendingMessage = false; // Set sending message flag to false
                                        }
                                } else {
                                        // User data does not exist for the selected friend's phone number
                                        Log.e("sendMessage", "No user data found for the user with phone number: " + selectedFriendPhoneNumber);
                                        Toast.makeText(ChatActivity.this, "User with the selected phone number is not registered.", Toast.LENGTH_SHORT).show();
                                        isSendingMessage = false; // Set sending message flag to false
                                }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                                Log.e("sendMessage", "Database error: " + databaseError.getMessage());
                                Toast.makeText(ChatActivity.this, "Database error. Please try again.", Toast.LENGTH_SHORT).show();
                                isSendingMessage = false; // Set sending message flag to false
                        }
                });
        } finally {
                sendMessageLock.unlock();
        }
}

//        private void displayLastSeenStatus(String friendUserId) {
//                DatabaseReference friendRef = FirebaseDatabase.getInstance().getReference("lastSeen").child(friendUserId);
//                final TextView lastSeenOrOnlineText = findViewById(R.id.last_seen_text_view);
//                friendRef.addListenerForSingleValueEvent(new ValueEventListener() {
//                        @Override
//                        public void onDataChange(@NonNull DataSnapshot snapshot) {
//                                if (snapshot.exists()) {
//                                        String onlineStatus = snapshot.child("onlineStatus").getValue(String.class);
//
//                                        if (onlineStatus != null && onlineStatus.equals("Online")) {
//                                                // Friend is currently online
//                                                lastSeenOrOnlineText.setText("Online");
//                                        } else {
//                                                // Friend is offline, calculate last seen time
//                                                String lastSeenTime = snapshot.child("onlineStatus").getValue(String.class);
//                                                if (lastSeenTime != null) {
//                                                        long timeDifference = calculateTimeDifference(lastSeenTime);
//                                                        // Display last seen time in minutes, hours, or days ago
//                                                        String lastSeenStatus = calculateLastSeenStatus(timeDifference);
//                                                        lastSeenOrOnlineText.setText(lastSeenStatus);
//                                                } else {
//                                                        lastSeenOrOnlineText.setText("Last seen: N/A");
//                                                }
//                                        }
//                                } else {
//                                        lastSeenOrOnlineText.setText("User not found");
//                                }
//                        }
//
//                        @Override
//                        public void onCancelled(@NonNull DatabaseError error) {
//                                Log.e("ChatActivity", "Database error: " + error.getMessage());
//                        }
//                });
//        }
        private boolean isNetworkConnected() {
                ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo networkInfo = null;
                if (connectivityManager != null) {
                        networkInfo = connectivityManager.getActiveNetworkInfo();
                }
                return networkInfo != null && networkInfo.isConnected();
        }
        @Override
        public void onBackPressed() {
                super.onBackPressed();
                if (mChildEventListener != null) {
                        mDatabaseReference.removeEventListener(mChildEventListener);
                        mChildEventListener = null;
                }
        }
        private void updateUserStatus(boolean isOnline) {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                // Retrieve the user's phone number from SharedPreferences
                SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
                String phoneNumber = sharedPreferences.getString("phoneNumber", "");
                if (currentUser != null) {
                        String userId = currentUser.getUid();
                        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("lastSeen").child(phoneNumber);

                        if (isOnline) {
                                // Set user's status as "Online"
                                userRef.child("onlineStatus").setValue("Online");
                        } else {
                                // Set user's status as current date and time when offline
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                                String currentTime = sdf.format(new Date());
                                userRef.child("onlineStatus").setValue(currentTime);
                        }
                }
        }
        @Override
        protected void onResume() {
                super.onResume();
                updateUserStatus(true);
                lastSeenData();
        }
        private long calculateTimeDifference(String lastSeenTime) {
                try {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                        Date lastSeenDateTime = sdf.parse(lastSeenTime);
                        long currentTime = System.currentTimeMillis();
                        return currentTime - lastSeenDateTime.getTime();
                } catch (ParseException e) {
                        e.printStackTrace();
                }
                return 0;
        }
        private String calculateLastSeenStatus(long timeDifference) {
                if (timeDifference < 60 * 1000) { // Less than 1 minute
                        return "Online";
                } else if (timeDifference < 60 * 60 * 1000) { // Less than 1 hour
                        long minutes = timeDifference / (60 * 1000);
                        return minutes + " mins ago";
                } else if (timeDifference < 24 * 60 * 60 * 1000) { // Less than 24 hours
                        long hours = timeDifference / (60 * 60 * 1000);
                        return hours + " hrs ago";
                } else { // More than 24 hours
                        long days = timeDifference / (24 * 60 * 60 * 1000);
                        return days + " days ago";
                }
        }
        private void lastSeenData() {
                final TextView lastSeenOrOnlineText = findViewById(R.id.last_seen_text_view);
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser == null) {
                        lastSeenOrOnlineText.setText("Please log in again");
                } else {
                        // Log the selected friend's phone number before the query
                        Log.d("ChatActivity", "Selected Friend's Phone Number: " + selectedFriendPhoneNumber);

                        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("/lastSeen");
                        Log.d("ChatActivity", "Entering the lastSeenRef Query");
                        ref.child(selectedFriendPhoneNumber).addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        Log.d("ChatActivity", "onDataChange: DataSnapshot exists: " + snapshot.exists());
                                        if (snapshot.exists()) {
                                                Log.d("ChatActivity", "onDataChange: snapshot: " + snapshot.getValue()); // Log the snapshot
                                                String onlineStatus = snapshot.child("onlineStatus").getValue(String.class);
                                                Log.d("ChatActivity", "Online status retrieved: " + onlineStatus);
                                                if (onlineStatus != null) {
                                                        if (onlineStatus.equals("Online")) {
                                                                lastSeenOrOnlineText.setText("Online");
                                                                Log.d("ChatActivity", "Friend is online");
                                                        } else {
                                                                String lastSeenTime = onlineStatus;
                                                                Log.d("ChatActivity", "Last seen time retrieved: " + lastSeenTime);
                                                                if (!lastSeenTime.isEmpty()) {
                                                                        long timeDifference = calculateTimeDifference(lastSeenTime);
                                                                        Log.d("ChatActivity", "Time difference calculated: " + timeDifference);
                                                                        String lastSeenStatus = calculateLastSeenStatus(timeDifference);
                                                                        lastSeenOrOnlineText.setText(lastSeenStatus);
                                                                        Log.d("ChatActivity", "Last seen status set: " + lastSeenStatus);
                                                                } else {
                                                                        lastSeenOrOnlineText.setText("Last seen: N/A");
                                                                        Log.d("ChatActivity", "Last seen time is empty");
                                                                }
                                                        }
                                                } else {
                                                        lastSeenOrOnlineText.setText("Online status not available");
                                                        Log.d("ChatActivity", "Online status is null");
                                                }
                                        } else {
                                                lastSeenOrOnlineText.setText("User not found");
                                                Log.d("ChatActivity", "User not found");
                                        }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                        Log.e("ChatActivity", "Database error: " + error.getMessage());
                                }
                        });
                }
        }
//        private void displayLastSeenStatus(String friendUserId, TextView lastSeenOrOnlineText) {
//                DatabaseReference friendRef = FirebaseDatabase.getInstance().getReference("lastSeen").child(friendUserId);
//                friendRef.addListenerForSingleValueEvent(new ValueEventListener() {
//                        @Override
//                        public void onDataChange(@NonNull DataSnapshot snapshot) {
//                                if (snapshot.exists()) {
//                                        String onlineStatus = snapshot.child("onlineStatus").getValue(String.class);
//                                        Log.d("ChatActivity", "Online status retrieved: " + onlineStatus);
//                                        if (onlineStatus != null) {
//                                                if (onlineStatus.equals("Online")) {
//                                                        lastSeenOrOnlineText.setText("Online");
//                                                        Log.d("ChatActivity", "Friend is online");
//                                                } else {
//                                                        String lastSeenTime = onlineStatus;
//                                                        Log.d("ChatActivity", "Last seen time retrieved: " + lastSeenTime);
//                                                        if (!lastSeenTime.isEmpty()) {
//                                                                long timeDifference = calculateTimeDifference(lastSeenTime);
//                                                                Log.d("ChatActivity", "Time difference calculated: " + timeDifference);
//                                                                String lastSeenStatus = calculateLastSeenStatus(timeDifference);
//                                                                lastSeenOrOnlineText.setText(lastSeenStatus);
//                                                                Log.d("ChatActivity", "Last seen status set: " + lastSeenStatus);
//                                                        } else {
//                                                                lastSeenOrOnlineText.setText("Last seen: N/A");
//                                                                Log.d("ChatActivity", "Last seen time is empty");
//                                                        }
//                                                }
//                                        } else {
//                                                lastSeenOrOnlineText.setText("Online status not available");
//                                                Log.d("ChatActivity", "Online status is null");
//                                        }
//                                } else {
//                                        lastSeenOrOnlineText.setText("User not found");
//                                        Log.d("ChatActivity", "User not found");
//                                }
//                        }
//
//                        @Override
//                        public void onCancelled(@NonNull DatabaseError error) {
//                                Log.e("ChatActivity", "Database error: " + error.getMessage());
//                        }
//                });
//        }
        @Override
        protected void onPause() {
                super.onPause();
                // Set user's status with current date and time when in background
                updateUserStatus(false);
        }

    // Helper method to convert date and time to millis
//        private long convertDateTimeToMillis(String date, String time) {
//                try {
//                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
//                        Date dateTime = sdf.parse(date + " " + time);
//                        return dateTime.getTime();
//                } catch (ParseException e) {
//                        e.printStackTrace();
//                }
//                return 0;
//        }

//        retrieve just the date and time and set the lastseen, as any of the below format acc to the condition:
//Online (if the user is online)
//2 mins ago (if the time is < 60 min)
//2 hrs ago (if the time is < 24 hrs and >= 60 min)
//2 days ago (if the time is >24 hrs)

//        private void setUserOnlineStatus() {
//                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
//                if (currentUser != null) {
//                        String userId = currentUser.getUid();
//                        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference().child("users").child(userId);
//                        usersRef.child("isOnline").setValue("Online")
//                                .addOnCompleteListener(task -> {
//                                        if (task.isSuccessful()) {
//                                                Log.d("OtherActivity", "User status updated as online");
//                                        } else {
//                                                Log.e("OtherActivity", "Failed to update user status: " + task.getException().getMessage());
//                                        }
//                                });
//                }
//        }
//
//        @Override
//        protected void onResume() {
//                super.onResume();
//                // Set user's status as online
//                setUserOnlineStatus();
//        }

//        @Override
//        protected void onPause() {
//                super.onPause();
//
//                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
//                if (currentUser != null) {
//                        String userId = currentUser.getUid();
//                        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
//
//                        // Update last seen status with timestamp when user leaves the chatroom
//                        String lastSeenTime = "Last seen at " + getCurrentTimestamp();
//                        userRef.child("lastSeen").setValue(lastSeenTime);
//                }
//        }
        // Helper method to get current timestamp
//        private String getCurrentTimestamp() {
//                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
//                return sdf.format(new Date());
//        }
//        @Override
//        protected void onStop() {
//                super.onStop();
//
//                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
//                if (currentUser != null) {
//                        // User has created an account, set last seen status
//                        setStatus("Last seen at " + dt.getTime());
//                }
//        }
//        private void setStatus(String status) {
//                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
//                if (currentUser != null) {
//                        String currentUserId = currentUser.getUid();
//
//                        // Store the last seen status under the user's node
//                        DatabaseReference userRef = mFirebaseDatabase.getReference("users").child(currentUserId);
//                        userRef.child("lastSeen").setValue(status);
//                }
//        }
}

//package com.example.cryptochat;
//
//import android.content.Context;
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.database.Cursor;
//import android.net.ConnectivityManager;
//import android.net.NetworkInfo;
//import android.os.Bundle;
//import android.provider.ContactsContract;
//import android.text.Editable;
//import android.text.InputFilter;
//import android.text.TextWatcher;
//import android.util.Log;
//import android.view.View;
//import android.widget.EditText;
//import android.widget.ImageView;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.recyclerview.widget.LinearLayoutManager;
//import androidx.recyclerview.widget.RecyclerView;
//
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.auth.FirebaseUser;
//import com.google.firebase.database.ChildEventListener;
//import com.google.firebase.database.DataSnapshot;
//import com.google.firebase.database.DatabaseError;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.ValueEventListener;
//import com.google.firebase.crashlytics.FirebaseCrashlytics;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.locks.Lock;
//import java.util.concurrent.locks.ReentrantLock;
//
//public class ChatActivity extends AppCompatActivity {
//
//        public static final String FRIEND_ID = "friendId";
//        public static final String FRIEND_NAME = "friendName";
//        public static final int DEFAULT_MSG_LENGTH_LIMIT = 500;
//        public static final String EXTRA_SELECTED_FRIEND_NAME = "selectedFriendName";
//        public static final String EXTRA_LAST_MESSAGE = "lastMessage";
//        public static final String EXTRA_TIMESTAMP = "timestamp";
//        private AES aes;
//        private ChatMessageAdapter messageAdapter;
//        private RecyclerView messageRecyclerView;
//        private List<Message> messageList = new ArrayList<>();
//        private EditText messageEditText;
//        private ImageView send_button;
//
//        private FirebaseDatabase mFirebaseDatabase;
//        private ChildEventListener mChildEventListener;
//        private DatabaseReference mDatabaseReference, onlineRef;
//        private DateAndTime dt;
//        private Lock sendMessageLock = new ReentrantLock();
//
//        // Store the selected friend's ID and name
//        private String selectedFriendId;
//        private String selectedFriendName;
//        private boolean isSendingMessage = false;
//        private String selectedFriendPhoneNumber;
//
//        static final int REQUEST_CHAT_ACTIVITY = 1; // Replace with your request code if needed
//
//        // Constants
//        private static final String SHARED_PREF_KEY_PHONE_NUMBER = "phoneNumber";
//        private static final String CHAT_ROOM_ID_PREFIX = "chat_";
//        private static final String COUNTRY_CODE = "+91";
//
//        @Override
//        protected void onCreate(Bundle savedInstanceState) {
//                super.onCreate(savedInstanceState);
//                setContentView(R.layout.activity_chat);
//                // Initialize Firebase Crashlytics
//                FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
//
//                TextView friend_name_text_view = findViewById(R.id.chat_activity_friend_name);
//                final TextView lastSeenOrOnlineText = findViewById(R.id.last_seen_text_view);
//
//                // Get the selected friend's ID and name from the intent
//                selectedFriendId = getIntent().getStringExtra(FRIEND_ID);
//                selectedFriendName = getIntent().getStringExtra(FRIEND_NAME);
//
//                // Retrieve the phone number from SharedPreferences
//                SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
//                String phoneNumber = sharedPreferences.getString(SHARED_PREF_KEY_PHONE_NUMBER, "");
//
//                if (selectedFriendName == null || selectedFriendName.isEmpty()) {
//                        // Handle the case where selectedFriendName is null or empty
//                        // You may want to show an error message or close the activity
//                        return;
//                }
//
//                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
//                String currentUserId = (currentUser != null) ? currentUser.getUid() : "";
//
//                friend_name_text_view.setText(selectedFriendName);
//
//                send_button = findViewById(R.id.send_button);
//                messageRecyclerView = findViewById(R.id.recycler_view_messages);
//                LinearLayoutManager layoutManager = new LinearLayoutManager(this);
//                layoutManager.setStackFromEnd(true);
//                messageRecyclerView.setLayoutManager(layoutManager);
//                messageRecyclerView.setHasFixedSize(true);
//
//                // Initialize Message Adapter
//                // Pass the current user's sender ID when initializing the ChatMessageAdapter
//                messageAdapter = new ChatMessageAdapter(messageList, currentUserId);
//                messageRecyclerView.setAdapter(messageAdapter);
//                messageEditText = findViewById(R.id.message_edit_text);
//                dt = new DateAndTime();
//                aes = new AES(this);
//
//                // Check if Firebase Database is properly initialized
//                if (FirebaseDatabase.getInstance() == null) {
//                        // Handle the case where Firebase Database is not initialized
//                        // You may want to show an error message or close the activity
//                        return;
//                }
//
//                mFirebaseDatabase = FirebaseDatabase.getInstance();
//                onlineRef = mFirebaseDatabase.getReference("users").child(selectedFriendId);
//
//                // Get the currently signed-in user's display name from Firebase (if available)
//                String currentUserDisplayName = (currentUser != null) ? currentUser.getDisplayName() : null;
//                getSelectedContactPhoneNumber();
//
//                // Pass the user names to generateChatRoomId
//                String chatRoomId = generateChatRoomId(currentUserDisplayName, selectedFriendName);
//
//                // Initialize the database reference for the chat messages
//                init();
//
//                setEditText();
//
//                // Set the DatabaseReference for MyFirebaseMessagingService
//                MyFirebaseMessagingService messagingService = new MyFirebaseMessagingService();
//                messagingService.setDatabaseReference(mDatabaseReference);
//
//                // Pass the correct friendId to fetchMessages
//                fetchMessages();
//
//                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("/users");
//
//                ref.orderByChild("phoneNumber").equalTo(selectedFriendPhoneNumber).addValueEventListener(new ValueEventListener() {
//                        @Override
//                        public void onDataChange(@NonNull DataSnapshot snapshot) {
//                                if (snapshot.exists()) {
//                                        // Get the first matching user (assuming phone numbers are unique)
//                                        DataSnapshot userSnapshot = snapshot.getChildren().iterator().next();
//                                        String status = String.valueOf(userSnapshot.child("isOnline").getValue());
//
//                                        // Set the status to the lastSeenOrOnlineText TextView
//                                        lastSeenOrOnlineText.setText(status);
//                                } else {
//                                        // Handle the case where no matching user was found
//                                }
//                        }
//
//                        @Override
//                        public void onCancelled(@NonNull DatabaseError error) {
//                                // Handle onCancelled if needed
//                        }
//                });
//
//                send_button.setOnClickListener(new View.OnClickListener() {
//                        @Override
//                        public void onClick(View v) {
//                                sendMessage();
//                        }
//                });
//        }
//
//        private void setEditText() {
//                messageEditText.addTextChangedListener(new TextWatcher() {
//                        @Override
//                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
//                                messageRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
//                        }
//
//                        @Override
//                        public void onTextChanged(CharSequence s, int start, int before, int count) {
//                                messageRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
//                                if (s.toString().trim().length() > 0) {
//                                        send_button.setEnabled(true);
//                                } else {
//                                        send_button.setEnabled(false);
//                                }
//                        }
//
//                        @Override
//                        public void afterTextChanged(Editable s) {
//
//                        }
//                });
//                messageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});
//        }
//
//        // Initialize the database reference for the chat messages
//        private void init() {
//                SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
//                String phoneNumber = sharedPreferences.getString("phoneNumber", "");
//
//                // Generate a unique chat room ID based on the phone numbers
//                String chatRoomId = generateChatRoomId(phoneNumber, selectedFriendPhoneNumber);
//                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
//                String currentUserId = (currentUser != null) ? currentUser.getUid() : "";
//
//                // Check if chatRoomId is not null before creating the database reference
//                if (chatRoomId != null) {
//                        mDatabaseReference = mFirebaseDatabase.getReference().child("messages").child(chatRoomId);
//
//                        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
//                        layoutManager.setStackFromEnd(true);
//                        messageRecyclerView.setLayoutManager(layoutManager);
//                        messageRecyclerView.setHasFixedSize(true);
//
//                        // Pass the current user's sender ID when initializing the ChatMessageAdapter
//                        messageAdapter = new ChatMessageAdapter(messageList, currentUserId);
//                        messageRecyclerView.setAdapter(messageAdapter);                        messageRecyclerView.setAdapter(messageAdapter);
//                } else {
//                        // Handle the case where chatRoomId is null
//                        Log.e("Init", "chatRoomId is null");
//                }
//        }
//
//        // Modify the fetchMessages method
//        private void fetchMessages() {
//                SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
//                String phoneNumber = sharedPreferences.getString(SHARED_PREF_KEY_PHONE_NUMBER, "");
//                String chatRoomId = generateChatRoomId(phoneNumber, selectedFriendPhoneNumber);
//                mDatabaseReference = mFirebaseDatabase.getReference().child("messages").child(chatRoomId);
//
//                mChildEventListener = new ChildEventListener() {
//                        @Override
//                        public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
//                                Message message = snapshot.getValue(Message.class);
//                                if (message != null) {
//                                        // Decrypt the message and display it
//                                        String decryptedMessage = aes.Decrypt(message.getMessageText(), com.example.cryptochat.ChatActivity.this);
//
//                                        // Create a new Message object for displaying in the app with the decrypted message
//                                        Message displayMessage = new Message();
//                                        displayMessage.setMessageText(decryptedMessage); // Use the decrypted message text
//                                        displayMessage.setSender_id(message.getSender_id()); // Use the sender's ID from the database
//                                        displayMessage.setReceiver_id(message.getReceiver_id());
//                                        displayMessage.setTimestamp(message.getTimestamp());
//                                        displayMessage.setDate(message.getDate());
//                                        displayMessage.setSent(message.isSent());
//
//                                        // Add the displayMessage to the list for displaying in the app
//                                        messageList.add(displayMessage);
//                                        messageAdapter.notifyDataSetChanged();
//                                        messageRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
//                                } else {
//                                        // Handle the case where the message is null
//                                        Log.e("Message", "Received a null message");
//                                }
//                        }
//
//                        @Override
//                        public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
//                                // Handle message updates if needed
//                        }
//
//                        @Override
//                        public void onChildRemoved(@NonNull DataSnapshot snapshot) {
//                                // Handle message removal if needed
//                        }
//
//                        @Override
//                        public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
//                                // Handle message movement if needed
//                        }
//
//                        @Override
//                        public void onCancelled(@NonNull DatabaseError error) {
//                                // Handle onCancelled if needed
//                        }
//                };
//
//                mDatabaseReference.addChildEventListener(mChildEventListener);
//        }
//
//        private String generateChatRoomId(String phoneNumber, String selectedFriendPhoneNumber) {
//                if (phoneNumber != null && selectedFriendPhoneNumber != null) {
//                        // Sort the phone numbers to ensure consistency in the chat room ID
//                        List<String> phoneNumbers = new ArrayList<>();
//                        phoneNumbers.add(phoneNumber);
//                        phoneNumbers.add(selectedFriendPhoneNumber);
//                        Collections.sort(phoneNumbers);
//
//                        // Combine the sorted phone numbers to create the chat room ID
//                        String chatRoomId = CHAT_ROOM_ID_PREFIX + phoneNumbers.get(0) + "_" + phoneNumbers.get(1);
//
//                        Log.d("Chat Room ID", chatRoomId);
//                        return chatRoomId;
//                } else {
//                        return "unknown_chat_room";
//                }
//        }
//
//        private void getSelectedContactPhoneNumber() {
//                // Assuming you have the selected contact's name in selectedFriendName
//                String contactName = selectedFriendName;
//
//                // Define the projection for the query
//                String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};
//
//                // Define the selection criteria (search by contact name)
//                String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " = ?";
//                String[] selectionArgs = {contactName};
//
//                // Perform the query to retrieve the phone number
//                Cursor cursor = getContentResolver().query(
//                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
//                        projection,
//                        selection,
//                        selectionArgs,
//                        null
//                );
//
//                if (cursor != null) {
//                        try {
//                                if (cursor.moveToFirst()) {
//                                        // Retrieve the phone number from the query result
//                                        int phoneNumberColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
//                                        selectedFriendPhoneNumber = cursor.getString(phoneNumberColumnIndex);
//
//                                        // Remove spaces from the selectedFriendPhoneNumber
//                                        selectedFriendPhoneNumber = selectedFriendPhoneNumber.replaceAll("\\s+", "");
//
//                                        // Add country code if it's not already present
//                                        if (!selectedFriendPhoneNumber.startsWith("+")) {
//                                                // Assuming the country code is +91
//                                                selectedFriendPhoneNumber = COUNTRY_CODE + selectedFriendPhoneNumber;
//                                        }
//                                }
//                        } finally {
//                                cursor.close();
//                        }
//                }
//        }
//
//        // When sending a message
//        private void sendMessage() {
//                sendMessageLock.lock();
//                try {
//                        String messageText = messageEditText.getText().toString().trim();
//                        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
//                        String phoneNumber = sharedPreferences.getString("phoneNumber", "");
//                        if (messageText.isEmpty()) {
//                                return;
//                        }
//                        if (!isNetworkConnected()) {
//                                Toast.makeText(this, "Ensure you're connected to the Internet to send the message", Toast.LENGTH_SHORT).show();
//                                return;
//                        }
//                        // Check if the message is already being sent
//                        if (isSendingMessage) {
//                                return;
//                        }
//                        isSendingMessage = true;
//                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
//                        if (currentUser == null) {
//                                return;
//                        }
//                        String currentUserDisplayName = (currentUser != null) ? currentUser.getDisplayName() : null;
//                        String currentUserId = currentUser.getUid();
//                        String chatRoomId = generateChatRoomId(phoneNumber, selectedFriendPhoneNumber);
//                        String encryptedMessage = aes.Encrypt(messageText, this);
//
//                        // Include receiver's name in the Message object
//                        Message sentMessage = new Message();
//                        sentMessage.setMessageText(encryptedMessage);
//                        sentMessage.setSender_id(currentUserId);
//                        sentMessage.setReceiver_id(selectedFriendPhoneNumber);
//                        sentMessage.setReceiverName(selectedFriendName); // Include receiver's name
//
//                        sentMessage.setTimestamp(dt.getTimeWithSeconds());
//                        sentMessage.setDate(dt.getDATE());
//                        DatabaseReference chatRoomRef = mFirebaseDatabase.getReference("messages").child(chatRoomId).push();
//
//                        // Update the message in the database
//                        chatRoomRef.setValue(sentMessage, (databaseError, databaseReference) -> {
//                                if (databaseError == null) {
//                                        messageEditText.setText("");
//                                        isSendingMessage = false;
//                                } else {
//                                        Log.e("Message Sending", "Failed to send message: " + databaseError.getMessage());
//                                        isSendingMessage = false;
//                                        Toast.makeText(this, "Failed to send message. Please try again.", Toast.LENGTH_SHORT).show();
//                                }
//                        });
//                        Intent intent = new Intent();
//                        intent.putExtra(EXTRA_SELECTED_FRIEND_NAME, selectedFriendName);
//                        intent.putExtra(EXTRA_LAST_MESSAGE, messageText);
//                        intent.putExtra(EXTRA_TIMESTAMP, dt.getTime());
//                        setResult(RESULT_OK, intent);
//                } finally {
//                        sendMessageLock.unlock();
//                }
//        }
//
//        private boolean isNetworkConnected() {
//                ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
//                NetworkInfo networkInfo = null;
//                if (connectivityManager != null) {
//                        networkInfo = connectivityManager.getActiveNetworkInfo();
//                }
//                return networkInfo != null && networkInfo.isConnected();
//        }
//
//        @Override
//        public void onBackPressed() {
//                super.onBackPressed();
//                if (mChildEventListener != null) {
//                        mDatabaseReference.removeEventListener(mChildEventListener);
//                        mChildEventListener = null;
//                }
//        }
//
//        @Override
//        protected void onResume() {
//                super.onResume();
//                final TextView lastSeenOrOnlineText = findViewById(R.id.last_seen_text_view);
//
//                if (FirebaseAuth.getInstance().getCurrentUser() == null) {
//                        // User hasn't created an account, display something else
//                        lastSeenOrOnlineText.setText("Receiver hasn't created an account");
//                } else {
//                        // User has created an account, set online status
//                        setStatus("Online");
//                }
//        }
//
//        @Override
//        protected void onPause() {
//                super.onPause();
//
//                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
//                        // User has created an account, set last seen status
//                        setStatus("Last seen at " + dt.getTime());
//                }
//        }
//
//
//        private void setStatus(String status) {
//                Map<String, Object> mp = new HashMap<>();
//                mp.put("isOnline", status);
//                onlineRef.updateChildren(mp);
//        }
//}