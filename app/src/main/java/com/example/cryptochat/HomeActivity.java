package com.example.cryptochat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.view.MenuInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends AppCompatActivity {
    private static final int REQUEST_CONTACTS_PERMISSION = 100;
    private RecyclerView chatRecyclerView;
    private ImageView searchIcon, optionIcon, plusIcon;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SearchView searchView;
    private List<ChatItem> chatItems = new ArrayList<>();
    private List<ChatItem> filteredChatItems = new ArrayList<>();
    private String userId;
    private String receiverId;
    private AES aes;
    private static final int REQUEST_CONTACT_SELECTION = 1;
    private static final int REQUEST_CHAT_ACTIVITY = 2;
    private List<Contact> contactsList = new ArrayList<>();
    private boolean doubleBackToExitPressedOnce = false;
    private static final String PREFS_NAME = "MyPrefs";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        // Initialize Firebase Crashlytics
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);

        initializeUI();
        setupRecyclerView();
        setListeners();

        // Load the contacts list for the receiver's device
        checkContactsPermission();
    }

    //Initialization of UI
    private void initializeUI() {
        searchView = findViewById(R.id.searchView);
        searchIcon = findViewById(R.id.searchIcon);
        optionIcon = findViewById(R.id.optionIcon);
        plusIcon = findViewById(R.id.plusIcon);
        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
    }
    private void setupRecyclerView() {

        // Retrieve the phone number from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        String phoneNumber = sharedPreferences.getString("phoneNumber", "");

        // Get the current user from Firebase Authentication
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            userId = currentUser.getUid(); // Set userId to the UID of the logged-in user
        } else {
            // Handle the case where the user is not logged in
        }

        aes = new AES(this);

        // Set up the RecyclerView with an adapter for chat messages
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        chatRecyclerView.setLayoutManager(layoutManager);
        ChatItemAdapter adapter = new ChatItemAdapter(filteredChatItems, position -> {
            String receiverId = getCurrentReceiverId(filteredChatItems.get(position));
            startChatWithContact(filteredChatItems.get(position).getContactId(),
                    filteredChatItems.get(position).getContactName(),
                    receiverId);
        }, this);
        chatRecyclerView.setAdapter(adapter);
    }
    private void setListeners() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // Trigger refresh action
            refreshData();
            updateChatItems();

        });

        // Search icon click listener
        searchIcon.setOnClickListener(v -> {
            // Show or hide the search bar based on its current visibility
            int visibility = searchView.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE;
            searchView.setVisibility(visibility);

            // If the search bar becomes visible, focus and open the keyboard
            if (visibility == View.VISIBLE) {
                searchView.setIconified(false);
                searchView.requestFocus();
            }

            // Clear the search query when hiding the search bar
            if (visibility == View.INVISIBLE) {
                searchView.setQuery("", false);  // Clear the search query
                filterChatItems("");  // Filter with an empty query to show all items
            }
        });

        // SearchView query listener
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // Handle search query submission if needed
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // Filter the chatItems based on the search query as the user types
                filterChatItems(newText);
                return true;
            }
        });


        // Option icon click listener
        optionIcon.setOnClickListener(v -> showOptionsMenu(v));

        // Plus icon click listener
        plusIcon.setOnClickListener(v -> startContactsActivity());

    }

    //Contacts and Permission
    private void checkContactsPermission() {
        // Check if permission to read contacts is granted
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.READ_CONTACTS},
                    REQUEST_CONTACTS_PERMISSION);
        } else {
            // Permission is already granted, proceed with loading contacts
            loadContactsList();
        }
    }
    private void startContactsActivity() {
        Intent contactsIntent = new Intent(com.example.cryptochat.HomeActivity.this, ContactsActivity.class);
        startActivityForResult(contactsIntent, REQUEST_CONTACT_SELECTION);
    }
    private void loadContactsList() {
        // Retrieve the phone number from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        String phoneNumber = sharedPreferences.getString("phoneNumber", "");

        // Fetch contacts asynchronously
        new AsyncTask<Void, Void, List<Contact>>() {
            @Override
            protected List<Contact> doInBackground(Void... voids) {
                // Replace this with your actual method to fetch contacts based on the receiver's phone number
                return getContactsForPhoneNumber(phoneNumber);
            }

            @Override
            protected void onPostExecute(List<Contact> contacts) {
                super.onPostExecute(contacts);
                // Update the contactsList and filterChatItems
                contactsList.clear();
                contactsList.addAll(contacts);
                filterChatItems("");
            }
        }.execute();
    }

    //Options Menu
    private void showOptionsMenu(View v) {
        PopupMenu popupMenu = new PopupMenu(this, optionIcon);
        MenuInflater inflater = popupMenu.getMenuInflater();
        inflater.inflate(R.menu.menu_home, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_my_profile) {
                startProfileActivity();
                return true;
            } else if (itemId == R.id.menu_logout) {
                logoutAndNavigateToLogin();
                return true;
            } else {
                return false;
            }
        });

        popupMenu.show();
    }

    //Navigation and Activity Results
    private void startChatWithContact(String contactId, String contactName, String receiverId) {
        Intent intent = new Intent(com.example.cryptochat.HomeActivity.this, ChatActivity.class);
        intent.putExtra(ChatActivity.FRIEND_ID, contactId); // Pass the contact's ID
        intent.putExtra(ChatActivity.FRIEND_NAME, contactName); // Pass the contact's name
        intent.putExtra(ChatActivity.SELECTED_FRIEND_PHONE_NUMBER, receiverId); // Pass the receiver's ID
        startActivityForResult(intent, REQUEST_CHAT_ACTIVITY); // Start ChatActivity for result
    }
    private void startProfileActivity() {
        Intent profileIntent = new Intent(com.example.cryptochat.HomeActivity.this, ProfileActivity.class);
        profileIntent.putExtra("phoneNumber", getPhoneNumberFromPreferences());
        startActivity(profileIntent);
    }
    private void logoutAndNavigateToLogin() {
        FirebaseAuth.getInstance().signOut();

        Intent intent = new Intent(com.example.cryptochat.HomeActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHAT_ACTIVITY && resultCode == RESULT_OK && data != null) {
            // Extract the ChatMessage object from the intent received from ChatActivity
            ChatMessage latestMessage = data.getParcelableExtra(ChatActivity.EXTRA_LAST_MESSAGE);

            // Update the chat item with the latest message
            updateChatItemWithLatestMessage(latestMessage);
        }
    }

    //Data Refresh
    private void refreshData() {
        // Perform data refresh operation, e.g., reload messages from Firebase
        // Once the data is refreshed, update the RecyclerView adapter
        // You can call your existing updateChatItems() method here or implement a similar logic
        updateChatItems(); // Example call

        // After refreshing data, complete the refreshing animation
        swipeRefreshLayout.setRefreshing(false);
    }

    //Chat Item Handling
    private void processMessages(DataSnapshot dataSnapshot) {
        for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
            String senderId = chatSnapshot.child("sender_id").getValue(String.class);
            String receiverId = chatSnapshot.child("receiver_id").getValue(String.class); // Correctly retrieve receiverId
            String receiverName = chatSnapshot.child("receiverName").getValue(String.class);
            ChatMessage message = createChatMessageFromSnapshot(chatSnapshot);

            // Check if the message is relevant to the current user
            if ((senderId != null && senderId.equals(userId)) || (receiverId != null && receiverId.equals(userId))) {
                // Always add messages to chatItems, regardless of whether they are sent or received
                String chatRoomId = getChatRoomId(senderId, receiverId); // Get the chat room ID
                addOrUpdateChatItem(chatRoomId, receiverName, receiverId, message); // Pass receiverId to the method
            }
        }
    }
    private void updateChatItems() {
        DatabaseReference messagesRef = FirebaseDatabase.getInstance().getReference().child("messages");

        messagesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // Clear existing chat items
                chatItems.clear();

                // Process all messages
                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                    processMessages(chatSnapshot);
                }

                // Update the RecyclerView adapter
                updateRecyclerViewAdapter();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Handle cancellation of database operations if needed
            }
        });
    }
    private void addOrUpdateChatItem(String chatRoomId, String contactName, String receiverId, ChatMessage message) {
        // Find or create chat item for the chat room
        ChatItem chatItem = findChatItemForChatRoom(chatRoomId);
        if (chatItem == null) {
            // Create a new chat item if it doesn't exist
            chatItem = new ChatItem();
            chatItem.setContactId(chatRoomId); // Set the contact ID here
            chatItem.setContactName(contactName);
            chatItems.add(chatItem);
        }

        // Update the chat item with the latest message and receiverId
        chatItem.addChatMessage(message);
        chatItem.setReceiverId(receiverId); // Set the receiverId here
    }
    private void updateRecyclerViewAdapter() {
        // Sort the chatItems based on the timestamp of the latest message in each ChatItem
        Collections.sort(chatItems, (item1, item2) -> {
            ChatMessage latestMessage1 = item1.getLatestMessage();
            ChatMessage latestMessage2 = item2.getLatestMessage();
            if (latestMessage1 != null && latestMessage2 != null) {
                return Long.compare(latestMessage2.getTimestampMillis(), latestMessage1.getTimestampMillis());
            } else if (latestMessage1 != null) {
                return -1; // item1 has a message, so it should come first
            } else if (latestMessage2 != null) {
                return 1; // item2 has a message, so it should come first
            } else {
                return 0; // Both items have no messages or null messages, no preference for order
            }
        });

        // Update the RecyclerView adapter
        runOnUiThread(() -> {
            ChatItemAdapter adapter = new ChatItemAdapter(chatItems, position -> {
                // Handle item click here
                Log.d("ChatItem", "Clicked on item at position: " + position);
                ChatItem clickedItem = chatItems.get(position);
                String receiverId = getCurrentReceiverId(clickedItem);
                Log.d("ChatItem", "Receiver ID for clicked item: " + receiverId);
                startChatWithContact(clickedItem.getContactId(), clickedItem.getContactName(), receiverId);
            }, com.example.cryptochat.HomeActivity.this);
            chatRecyclerView.setAdapter(adapter);
        });
    }
    private ChatMessage createChatMessageFromSnapshot(DataSnapshot snapshot) {
        String senderId = snapshot.child("sender_id").getValue(String.class);
        String receiverId = snapshot.child("receiver_id").getValue(String.class); // Correctly retrieve receiverId
        String messageText = snapshot.child("messageText").getValue(String.class);
        String receiverName = snapshot.child("receiverName").getValue(String.class);
        String encryptedMessageText = snapshot.child("messageText").getValue(String.class);
        String timestampString = snapshot.child("timestamp").getValue(String.class);
        long timestamp = 0;

        if (timestampString != null && !timestampString.isEmpty()) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yy HH:mm:ss", Locale.getDefault());
                Date date = dateFormat.parse(snapshot.child("date").getValue(String.class) + " " + timestampString);
                timestamp = date.getTime();
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

//        boolean sent = snapshot.child("sent").getValue(Boolean.class);

        ChatMessage message = new ChatMessage(senderId, receiverId, messageText, timestamp, receiverName); // Pass receiverId to ChatMessage constructor
        message.setEncryptedMessageText(encryptedMessageText);
        message.setTimestampMillis(timestamp);

        return message;
    }
    private void updateChatItemWithLatestMessage(ChatMessage latestMessage) {
        // Extract necessary information from the latestMessage
        String friendName;
        String lastMessage;
        long timestamp;

        if (latestMessage != null) {
            friendName = latestMessage.getReceiverName(); // Assuming receiverName is the friend's name
            lastMessage = latestMessage.getMessageText();
            timestamp = latestMessage.getTimestampMillis();
        } else {
            // Handle the case where latestMessage is null
            return;
        }

        // Find the corresponding ChatItem for the friend
        ChatItem chatItem = findChatItemForContact(friendName);
        if (chatItem == null) {
            // If the ChatItem doesn't exist, create a new one
            chatItem = new ChatItem();
            chatItem.setContactName(friendName);
            chatItem.setLastMessage(lastMessage);
            chatItem.setTimestamp(timestamp);
            chatItems.add(chatItem);
        } else {
            // If the ChatItem exists, update its last message and timestamp
            chatItem.setLastMessage(lastMessage);
            chatItem.setTimestamp(timestamp);
        }

        // Update the RecyclerView adapter
        updateRecyclerViewAdapter();
    }
    private void filterChatItems(String query) {
        filteredChatItems.clear();

        // Iterate through chatItems and add items that match the search query
        for (ChatItem chatItem : chatItems) {
            if (chatItem.getContactName().toLowerCase().contains(query.toLowerCase())) {
                filteredChatItems.add(chatItem);
            }
        }

        // Update the RecyclerView adapter with the filtered list
        ChatItemAdapter chatItemAdapter = new ChatItemAdapter(filteredChatItems, position -> {
            // Access receiverId from the enclosing scope
            startChatWithContact(filteredChatItems.get(position).getContactId(),
                    filteredChatItems.get(position).getContactName(),
                    receiverId);
        }, com.example.cryptochat.HomeActivity.this);


        chatRecyclerView.setAdapter(chatItemAdapter);
    }


    //Helper Methods
    private String getPhoneNumberFromPreferences() {
        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        return sharedPreferences.getString("phoneNumber", "");
    }
    private List<Contact> getContactsForPhoneNumber(String phoneNumber) {
        List<Contact> contacts = new ArrayList<>();

        // Define the query parameters
        String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER};
        String selection = ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?";
        String[] selectionArgs = new String[]{phoneNumber};

        // Query the contacts using ContentResolver
        Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
        );

        if (cursor != null) {
            try {
                int nameColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int phoneNumberColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                while (cursor.moveToNext()) {
                    // Check if the column indices are valid
                    if (nameColumnIndex != -1 && phoneNumberColumnIndex != -1) {
                        String contactName = cursor.getString(nameColumnIndex);
                        String contactPhoneNumber = cursor.getString(phoneNumberColumnIndex);

                        Contact contact = new Contact();
                        contact.setName(contactName);
                        contact.setPhoneNumber(contactPhoneNumber);
                        contacts.add(contact);
                    }
                }
            } finally {
                cursor.close();
            }
        }

        return contacts;
    }
    private String getChatRoomId(String senderId, String receiverId) {
        if (senderId.equals(userId)) {
            return receiverId; // If the sender is the current user, use the receiver's ID as the chat room ID
        } else {
            return senderId; // If the receiver is the current user, use the sender's ID as the chat room ID
        }
    }
    private String getCurrentReceiverId(ChatItem chatItem) {
        // Determine the receiverId based on the current user's phone number and the contactId
        String currentUserPhoneNumber = getCurrentUserPhoneNumber();
        String receiverId = null;

        // Retrieve the receiver ID from the latest message in the chat item
        if (!chatItem.getMessages().isEmpty()) {
            ChatMessage latestMessage = chatItem.getMessages().get(0); // Assuming the latest message is at index 0
            receiverId = latestMessage.getReceiverId();
        }

        Log.d("ReceiverDebug", "Receiver ID from ChatItem: " + receiverId);
        Log.d("ReceiverDebug", "ChatItem Contact ID: " + chatItem.getContactId());
        Log.d("ReceiverDebug", "Current User Phone Number: " + currentUserPhoneNumber);

        // Return the receiver ID or contact ID if receiver ID is null
        return receiverId != null ? receiverId : chatItem.getContactId();
    }
    private String getCurrentUserPhoneNumber() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return sharedPreferences.getString("phoneNumber", "");
    }
    private ChatItem findChatItemForChatRoom(String chatRoomId) {
        for (ChatItem chatItem : chatItems) {
            String contactId = chatItem.getContactId();
            if (contactId != null && contactId.equals(chatRoomId)) {
                return chatItem;
            }
        }
        return null;
    }
    private ChatItem findChatItemForContact(String contactId) {
        for (ChatItem chatItem : chatItems) {
            if (chatItem.getContactId().equals(contactId)) {
                return chatItem;
            }
        }
        return null;
    }


    // FullName from DB
    private void getFullNameFromDatabase(String userId, final com.example.cryptochat.HomeActivity.FullNameCallback callback) {
        if (userId != null) {
            DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference().child("users").child(userId);
            usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        // Retrieve the full name from the dataSnapshot
                        String fullName = dataSnapshot.child("fullName").getValue(String.class);

                        // Pass the retrieved full name to the callback method
                        callback.onFullNameReceived(fullName);
                    } else {
                        // Handle the case where the user data does not exist
                        callback.onFullNameReceived(null);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    // Handle potential errors
                    callback.onFullNameReceived(null);
                }
            });
        } else {
            // Handle the case where userId is null
            callback.onFullNameReceived(null);
        }
    }
    // Define a callback interface to receive the full name asynchronously
    private interface FullNameCallback {
        void onFullNameReceived(String fullName);
    }

    // Storing lastseen data
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

    //LifeCycle
    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CONTACTS_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, load contacts
                loadContactsList();
            } else {
                // Permission denied, inform the user and exit the app
                Toast.makeText(this, "Contacts permission is required for the app to function properly.", Toast.LENGTH_SHORT).show();
                new Handler().postDelayed(() -> {
                    finish();
                }, 2000);
            }
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        updateChatItems();
        updateUserStatus(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Set user's status with current date and time when in background
        updateUserStatus(false);
    }

}

//    private void addOrUpdateChatItemForUser(String contactId, String contactName, String userId, ChatMessage message) {
//        // Find or create chat item for the user
//        ChatItem chatItem = findChatItemForUser(userId, contactId);
//        if (chatItem == null) {
//            chatItem = new ChatItem();
//            chatItem.setContactId(contactId);
//            chatItem.setContactName(contactName);
//            chatItems.add(chatItem);
//        }
//
//        // Update the latest message for the user
//        chatItem.addChatMessage(message);
//    }
//    private ChatItem findChatItemForUser(String userId, String contactId) {
//        for (ChatItem chatItem : chatItems) {
//            if (chatItem.getContactId().equals(contactId)) {
//                return chatItem;
//            }
//        }
//        return null;
//    }
//    private void updateChatItemAndAdapter(ChatItem chatItem, ChatMessage latestMessage) {
//        String senderNo = latestMessage.getSenderNo();
//        String receiverId = latestMessage.getReceiverId();
//
//        // Determine the contact ID based on the sender and receiver
//        String currentUserPhoneNumber = getCurrentUserPhoneNumber();
//        String contactId = currentUserPhoneNumber.equals(senderNo) ? receiverId : senderNo;
//
//        // Check if contactId is null
//        if (contactId != null) {
//            boolean chatItemExists = false;
//
//            // Iterate through existing chatItems to find if a ChatItem with the same contactId exists
//            for (ChatItem existingChatItem : chatItems) {
//                if (existingChatItem.getContactId().equals(contactId)) {
//                    chatItemExists = true;
//                    // Update the existing ChatItem with the latest message
//                    existingChatItem.addChatMessage(latestMessage);
//                    ; // Assuming addChatMessage() method exists
//                    break;
//                }
//            }
//
//            // If the ChatItem doesn't exist, create a new one
//            if (!chatItemExists) {
//                chatItem.setContactId(contactId);
//
//                // Retrieve the contact's full name from the database
//                getFullNameFromDatabase(contactId, fullName -> {
//                    if (fullName != null) {
//                        chatItem.setContactName(fullName);
//                    } else {
//                        // If full name is not found, set it as "Unknown Contact"
//                        chatItem.setContactName("Unknown Contact");
//                    }
//
//                    // Add the latest message to the chat item
//                    List<ChatMessage> messages = new ArrayList<>();
//                    messages.add(latestMessage);
//                    chatItem.setMessages(messages);
//                    chatItems.add(chatItem);
//
//                    // Update the RecyclerView adapter
//                    updateRecyclerViewAdapter();
//                });
//            } else {
//                // If the ChatItem already exists, update the RecyclerView adapter
//                updateRecyclerViewAdapter();
//            }
//        } else {
//            Log.e("Error", "Contact ID is null");
//        }
//    }
//    private List<ChatItem> getUniqueChatItems(List<ChatItem> chatItems) {
//        Map<String, ChatItem> uniqueItemsMap = new HashMap<>();
//        for (ChatItem item : chatItems) {
//            if (!uniqueItemsMap.containsKey(item.getContactId())) {
//                uniqueItemsMap.put(item.getContactId(), item);
//            }
//        }
//        return new ArrayList<>(uniqueItemsMap.values());
//    }
//    private boolean isChatRoomForCurrentUser(String chatRoomId) {
//        return chatRoomId.contains(userId);
//    }

//
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.database.Cursor;
//import android.os.AsyncTask;
//import android.os.Bundle;
//import android.os.Handler;
//import android.provider.ContactsContract;
//import android.util.Log;
//import android.view.MenuInflater;
//import android.view.View;
//import android.widget.ImageView;
//import android.widget.PopupMenu;
//import android.widget.Toast;
//
//import androidx.annotation.Nullable;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.appcompat.widget.SearchView;
//import androidx.recyclerview.widget.LinearLayoutManager;
//import androidx.recyclerview.widget.RecyclerView;
//import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
//
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.auth.FirebaseUser;
//import com.google.firebase.crashlytics.FirebaseCrashlytics;
//import com.google.firebase.database.DataSnapshot;
//import com.google.firebase.database.DatabaseError;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.ValueEventListener;
//
//import org.checkerframework.checker.nullness.qual.NonNull;
//
//import java.text.ParseException;
//import java.text.SimpleDateFormat;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.Date;
//import java.util.List;
//import java.util.Locale;
//
//public class HomeActivity extends AppCompatActivity {
//
//    private RecyclerView chatRecyclerView;
//    private ImageView searchIcon, optionIcon, plusIcon;
//    private SwipeRefreshLayout swipeRefreshLayout;
//    private SearchView searchView;
//    private List<ChatItem> chatItems = new ArrayList<>();
//    private List<ChatItem> filteredChatItems = new ArrayList<>();
//    private String userId;
//    private String receiverId;
//    private String receiverNo;
//    public static final String FRIEND_ID = "friendId";
//    public static final String FRIEND_NAME = "friendName";
//    public static final String SELECTED_FRIEND_PHONE_NUMBER = "selected_friend_phone_number";
//    private AES aes;
//    private static final int REQUEST_CONTACT_SELECTION = 1;
//    private static final int REQUEST_CHAT_ACTIVITY = 2;
//    private List<Contact> contactsList = new ArrayList<>();
//    private boolean doubleBackToExitPressedOnce = false;
//    private static final String PREFS_NAME = "MyPrefs";
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_home);
//        // Initialize Firebase Crashlytics
//        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
//
//        initializeUI();
//        setupRecyclerView();
//        setListeners();
//
//        // Load the contacts list for the receiver's device
//        loadContactsList();
//
//        // Get the intent extras
//        String friendId = getIntent().getStringExtra(FRIEND_ID);
//        String friendName = getIntent().getStringExtra(FRIEND_NAME);
////        String phoneNumber = getIntent().getStringExtra(SELECTED_FRIEND_PHONE_NUMBER);
////        receiverNo = getIntent().getStringExtra(SELECTED_FRIEND_PHONE_NUMBER);
//
//        // Log the received values
//        Log.d("DEBUG", "Received friendId: " + friendId + ", friendName: " + friendName + ", phoneNumber: " + receiverNo);
//
//    }
//    private void initializeUI() {
//        searchView = findViewById(R.id.searchView);
//        searchIcon = findViewById(R.id.searchIcon);
//        optionIcon = findViewById(R.id.optionIcon);
//        plusIcon = findViewById(R.id.plusIcon);
//        chatRecyclerView = findViewById(R.id.chatRecyclerView);
//        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
//        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
//        new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
//    }
//    private void setupRecyclerView() {
//
//        // Retrieve the phone number from SharedPreferences
//        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
//        String phoneNumber = sharedPreferences.getString("phoneNumber", "");
//
//        // Get the current user from Firebase Authentication
//        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
//
//        if (currentUser != null) {
//            userId = currentUser.getUid(); // Set userId to the UID of the logged-in user
//            Log.d("DEBUG", "Current User ID: " + userId);
//
//        } else {
//            // Handle the case where the user is not logged in
//        }
//
//        aes = new AES(this);
//
//        // Set up the RecyclerView with an adapter for chat messages
//        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
//        chatRecyclerView.setLayoutManager(layoutManager);
//        ChatItemAdapter adapter = new ChatItemAdapter(filteredChatItems, position -> {
//            // Get the contactId of the clicked item
//            String contactId = filteredChatItems.get(position).getContactId();
//            // Call getCurrentReceiverNo with the contactId and provide a callback
//            getCurrentReceiverNo(contactId, new ReceiverNumberCallback() {
//                @Override
//                public void onReceiverNumberReceived(String receiverNo) {
//                    if (receiverNo != null) {
//                        // Start the chat activity with the received receiver number
////                        startChatWithContact(contactId,
////                                filteredChatItems.get(position).getContactName(),
////                                receiverNo);
//                        startChatWithContact(contactId,
//                                filteredChatItems.get(position).getContactName());
//                    } else {
//                        // Handle the case where the receiver number is null
//                        Log.d("DEBUG", "Receiver number is null");
//                        // You may want to show an error message or handle this case differently
//                    }
//                }
//            });
//        }, this);
//        chatRecyclerView.setAdapter(adapter);
//
//    }
//    private void setListeners() {
//        swipeRefreshLayout.setOnRefreshListener(() -> {
//            // Trigger refresh action
//            refreshData();
//            updateChatItems();
//
//        });
//
//        // Search icon click listener
//        searchIcon.setOnClickListener(v -> {
//            // Show or hide the search bar based on its current visibility
//            int visibility = searchView.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE;
//            searchView.setVisibility(visibility);
//
//            // If the search bar becomes visible, focus and open the keyboard
//            if (visibility == View.VISIBLE) {
//                searchView.setIconified(false);
//                searchView.requestFocus();
//            }
//
//            // Clear the search query when hiding the search bar
//            if (visibility == View.INVISIBLE) {
//                searchView.setQuery("", false);  // Clear the search query
//                filterChatItems("");  // Filter with an empty query to show all items
//            }
//        });
//
//        // SearchView query listener
//        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
//            @Override
//            public boolean onQueryTextSubmit(String query) {
//                // Handle search query submission if needed
//                return false;
//            }
//
//            @Override
//            public boolean onQueryTextChange(String newText) {
//                // Filter the chatItems based on the search query as the user types
//                filterChatItems(newText);
//                return true;
//            }
//        });
//
//
//        // Option icon click listener
//        optionIcon.setOnClickListener(v -> showOptionsMenu(v));
//
//        // Plus icon click listener
//        plusIcon.setOnClickListener(v -> startContactsActivity());
//
//    }
//    private void startContactsActivity() {
//        Intent contactsIntent = new Intent(HomeActivity.this, ContactsActivity.class);
//        startActivityForResult(contactsIntent, REQUEST_CONTACT_SELECTION);
//    }
//    private void showOptionsMenu(View v) {
//        PopupMenu popupMenu = new PopupMenu(this, optionIcon);
//        MenuInflater inflater = popupMenu.getMenuInflater();
//        inflater.inflate(R.menu.menu_home, popupMenu.getMenu());
//
//        popupMenu.setOnMenuItemClickListener(item -> {
//            int itemId = item.getItemId();
//            if (itemId == R.id.menu_my_profile) {
//                startProfileActivity();
//                return true;
//            } else if (itemId == R.id.menu_logout) {
//                logoutAndNavigateToLogin();
//                return true;
//            } else {
//                return false;
//            }
//        });
//
//        popupMenu.show();
//    }
//    private void startProfileActivity() {
//        Intent profileIntent = new Intent(HomeActivity.this, ProfileActivity.class);
//        profileIntent.putExtra("phoneNumber", getPhoneNumberFromPreferences());
//        startActivity(profileIntent);
//    }
//    private String getPhoneNumberFromPreferences() {
//        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
//        return sharedPreferences.getString("phoneNumber", "");
//    }
//    private void logoutAndNavigateToLogin() {
//        FirebaseAuth.getInstance().signOut();
//
//        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//        startActivity(intent);
//        finish();
//    }
//
//    private void loadContactsList() {
//        // Retrieve the phone number from SharedPreferences
//        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
//        String phoneNumber = sharedPreferences.getString("phoneNumber", "");
//
//        // Fetch contacts asynchronously
//        new AsyncTask<Void, Void, List<Contact>>() {
//            @Override
//            protected List<Contact> doInBackground(Void... voids) {
//                // Replace this with your actual method to fetch contacts based on the receiver's phone number
//                return getContactsForPhoneNumber(phoneNumber);
//            }
//
//            @Override
//            protected void onPostExecute(List<Contact> contacts) {
//                super.onPostExecute(contacts);
//                // Update the contactsList and filterChatItems
//                contactsList.clear();
//                contactsList.addAll(contacts);
//                // Log the contents of contactsList
//                Log.d("DEBUG", "Contacts List: " + contactsList);
//                filterChatItems("");
//            }
//        }.execute();
//    }
//    private List<Contact> getContactsForPhoneNumber(String phoneNumber) {
//        List<Contact> contacts = new ArrayList<>();
//
//        // Define the query parameters
//        String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
//                ContactsContract.CommonDataKinds.Phone.NUMBER};
//        String selection = ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?";
//        String[] selectionArgs = new String[]{phoneNumber};
//
//        // Query the contacts using ContentResolver
//        Cursor cursor = getContentResolver().query(
//                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
//                projection,
//                selection,
//                selectionArgs,
//                null
//        );
//
//        if (cursor != null) {
//            try {
//                int nameColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
//                int phoneNumberColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
//
//                while (cursor.moveToNext()) {
//                    // Check if the column indices are valid
//                    if (nameColumnIndex != -1 && phoneNumberColumnIndex != -1) {
//                        String contactName = cursor.getString(nameColumnIndex);
//                        String contactPhoneNumber = cursor.getString(phoneNumberColumnIndex);
//
//                        Contact contact = new Contact();
//                        contact.setName(contactName);
//                        contact.setPhoneNumber(contactPhoneNumber);
//                        contacts.add(contact);
//                    }
//                }
//            } finally {
//                cursor.close();
//            }
//        }
//
//        return contacts;
//    }
//    private void filterChatItems(String query) {
//        filteredChatItems.clear();
//
//        // Iterate through chatItems and add items that match the search query
//        for (ChatItem chatItem : chatItems) {
//            if (chatItem.getContactName().toLowerCase().contains(query.toLowerCase())) {
//                filteredChatItems.add(chatItem);
//            }
//        }
//
//        // Update the RecyclerView adapter with the filtered list
//        ChatItemAdapter chatItemAdapter = new ChatItemAdapter(filteredChatItems, position -> {
//            // Access receiverId from the enclosing scope
////            startChatWithContact(filteredChatItems.get(position).getContactId(),
////                    filteredChatItems.get(position).getContactName(),
////                    receiverNo);
//            startChatWithContact(filteredChatItems.get(position).getContactId(),
//                    filteredChatItems.get(position).getContactName());
//        }, HomeActivity.this);
//
//        chatRecyclerView.setAdapter(chatItemAdapter);
//    }
//    private void processMessages(DataSnapshot dataSnapshot) {
//        for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
//            // Retrieve message details from the snapshot
//            String messageId = chatSnapshot.getKey();
//            String senderId = chatSnapshot.child("sender_id").getValue(String.class);
//            String receiverId = chatSnapshot.child("receiver_id").getValue(String.class);
//            String receiverName = chatSnapshot.child("receiverName").getValue(String.class);
//            String receiverNo = chatSnapshot.child("receiver_no").getValue(String.class);
//            String messageText = chatSnapshot.child("messageText").getValue(String.class);
//            boolean sent = chatSnapshot.child("sent").getValue(Boolean.class);
//            String timestamp = chatSnapshot.child("timestamp").getValue(String.class);
//
//            // Log all message details
//            Log.d("PROCESS_MESSAGES", "Message ID: " + messageId);
//            Log.d("PROCESS_MESSAGES", "Sender ID: " + senderId);
//            Log.d("PROCESS_MESSAGES", "Receiver ID: " + receiverId);
//            Log.d("PROCESS_MESSAGES", "Receiver Name: " + receiverName);
//            Log.d("PROCESS_MESSAGES", "Receiver No: " + receiverNo);
//            Log.d("PROCESS_MESSAGES", "Message Text: " + messageText);
//            Log.d("PROCESS_MESSAGES", "Sent: " + sent);
//            Log.d("PROCESS_MESSAGES", "Timestamp: " + timestamp);
//
//            // Check if the message involves the current user as either sender or receiver
//            boolean sentByCurrentUser = senderId.equals(userId);
//            boolean receivedByCurrentUser = receiverId.equals(userId);
//
//            // Log current user ID and message relevance
//            Log.d("PROCESS_MESSAGES", "Current User ID: " + userId);
//            Log.d("PROCESS_MESSAGES", "Sent by Current User: " + sentByCurrentUser);
//            Log.d("PROCESS_MESSAGES", "Received by Current User: " + receivedByCurrentUser);
//
//            // Add messages involving the current user to the chatItems list
//            if (sentByCurrentUser || receivedByCurrentUser) {
//                String contactId = sentByCurrentUser ? receiverId : senderId;
//                ChatMessage message = createChatMessageFromSnapshot(chatSnapshot);
//                addOrUpdateChatItem(contactId, receiverName, message);
//            } else {
//                // This message is irrelevant only if it doesn't involve the current user
//                Log.d("PROCESS_MESSAGES", "Irrelevant message: " + messageText);
//            }
//        }
//    }
//    private void addOrUpdateChatItem(String contactId, String contactName, ChatMessage message) {
//        // Find or create chat item for the contact
//        ChatItem chatItem = findChatItemForContact(contactId);
//        if (chatItem == null) {
//            // Create a new chat item if it doesn't exist
//            chatItem = new ChatItem();
//            chatItem.setContactId(contactId);
//            chatItem.setContactName(contactName);
//            chatItems.add(chatItem);
//        }
//
//        // Update the chat item with the latest message
//        chatItem.addChatMessage(message);
//
//        Log.d("ADD_OR_UPDATE_ITEM", "Chat item updated: " + chatItem.getContactName() + " - Latest message: " + message.getMessageText());
//    }
//    private void updateChatItems() {
//        DatabaseReference messagesRef = FirebaseDatabase.getInstance().getReference().child("messages");
//
//        messagesRef.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                // Clear existing chat items
//                chatItems.clear();
//
//                // Process all messages
//                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
//                    processMessages(chatSnapshot);
//                }
//
//                // Update the RecyclerView adapter
//                updateRecyclerViewAdapter();
//
//                // Set the last message text for each chat item
//                setLastMessageTextForChatItems();
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError databaseError) {
//                // Handle cancellation of database operations if needed
//            }
//        });
//    }
//
//    private void updateRecyclerViewAdapter() {
//        // Sort the chatItems based on the timestamp of the latest message in each ChatItem
//        Collections.sort(chatItems, (item1, item2) -> {
//            ChatMessage latestMessage1 = item1.getLatestMessage();
//            ChatMessage latestMessage2 = item2.getLatestMessage();
//            if (latestMessage1 != null && latestMessage2 != null) {
//                return Long.compare(latestMessage2.getTimestampMillis(), latestMessage1.getTimestampMillis());
//            } else if (latestMessage1 != null) {
//                return -1; // item1 has a message, so it should come first
//            } else if (latestMessage2 != null) {
//                return 1; // item2 has a message, so it should come first
//            } else {
//                return 0; // Both items have no messages or null messages, no preference for order
//            }
//        });
//
//        // Update the RecyclerView adapter
//        runOnUiThread(() -> {
//            ChatItemAdapter adapter = new ChatItemAdapter(chatItems, position -> {
//                // Handle item click here
//                ChatItem clickedItem = chatItems.get(position);
//                // Get the contactId of the clicked item
//                String contactId = clickedItem.getContactId();
//                // Call getCurrentReceiverNo with the contactId and provide a callback
//                getCurrentReceiverNo(contactId, new ReceiverNumberCallback() {
//                    @Override
//                    public void onReceiverNumberReceived(String receiverNo) {
//                        if (receiverNo != null) {
//                            // Start the chat activity with the received receiver number
////                            startChatWithContact(contactId, clickedItem.getContactName(), receiverNo);
//                            startChatWithContact(contactId, clickedItem.getContactName());
//                        } else {
//                            // Handle the case where the receiver number is null
//                            Log.d("DEBUG", "Receiver number is null");
//                            // You may want to show an error message or handle this case differently
//                        }
//                    }
//                });
//            }, HomeActivity.this);
//            chatRecyclerView.setAdapter(adapter);
//        });
//    }
//
//    // Method to set the last message text for each chat item
//    private void setLastMessageTextForChatItems() {
//        for (ChatItem chatItem : chatItems) {
//            ChatMessage latestMessage = chatItem.getLatestMessage();
//            if (latestMessage != null) {
//                String lastMessageText = latestMessage.getMessageText();
//                chatItem.setLastMessageText(lastMessageText);
//            }
//        }
//    }
//    private ChatItem findChatItemForContact(String contactId) {
//        for (ChatItem chatItem : chatItems) {
//            if (chatItem.getContactId().equals(contactId)) {
//                return chatItem;
//            }
//        }
//        return null;
//    }
//    private void refreshData() {
//        // Perform data refresh operation, e.g., reload messages from Firebase
//        // Once the data is refreshed, update the RecyclerView adapter
//        // You can call your existing updateChatItems() method here or implement a similar logic
//        updateChatItems(); // Example call
//
//        // After refreshing data, complete the refreshing animation
//        swipeRefreshLayout.setRefreshing(false);
//    }
//
//    // Method to retrieve the phone number for a given contactId
////    private void getCurrentReceiverNo(String contactId, ReceiverNumberCallback callback) {
////        DatabaseReference messagesRef = FirebaseDatabase.getInstance().getReference().child("messages");
////
////        messagesRef.addListenerForSingleValueEvent(new ValueEventListener() {
////            @Override
////            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
////                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
////                    // Check if the contactId matches the receiver_id in any message
////                    String receiverId = chatSnapshot.child("receiver_id").getValue(String.class);
////                    if (receiverId != null && receiverId.equals(contactId)) {
////                        // If found, return the receiver's phone number
////                        String receiverNo = chatSnapshot.child("receiver_no").getValue(String.class);
////                        if (receiverNo != null) {
////                            Log.d("DEBUG", "Receiver number found: " + receiverNo);
////                            // Call the callback with the receiver's phone number
////                            callback.onReceiverNumberReceived(receiverNo);
////                            return; // Exit the loop once the receiver's number is found
////                        }
////                    }
////                }
////                // If the receiver's phone number is not found, call the callback with null
////                Log.d("DEBUG", "Receiver number not found for contactId: " + contactId);
////                callback.onReceiverNumberReceived(null);
////            }
////
////            @Override
////            public void onCancelled(@NonNull DatabaseError databaseError) {
////                // Handle cancellation of database operations if needed
////                Log.d("DEBUG", "Error retrieving receiver number: " + databaseError.getMessage());
////                // Call the callback with null in case of error
////                callback.onReceiverNumberReceived(null);
////            }
////        });
////    }
//    private void getCurrentReceiverNo(String contactId, ReceiverNumberCallback callback) {
//        DatabaseReference messagesRef = FirebaseDatabase.getInstance().getReference().child("messages");
//
//        messagesRef.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                for (DataSnapshot chatRoomSnapshot : dataSnapshot.getChildren()) {
//                    // Navigate to the chatroom node based on chatId
//                    DataSnapshot chatIdSnapshot = chatRoomSnapshot.child(contactId);
//                    if (chatIdSnapshot.exists()) {
//                        // Get the receiver_no from the chatId node
//                        String receiverNo = chatIdSnapshot.child("receiver_no").getValue(String.class);
//                        if (receiverNo != null) {
//                            // Receiver number found, invoke callback
//                            callback.onReceiverNumberReceived(receiverNo);
//                            return; // Exit loop
//                        }
//                    }
//                }
//                // If receiver number is not found, invoke callback with null
//                callback.onReceiverNumberReceived(null);
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError databaseError) {
//                // Handle cancellation of database operations if needed
//                Log.d("DEBUG", "Error retrieving receiver number: " + databaseError.getMessage());
//                // Call the callback with null in case of error
//                callback.onReceiverNumberReceived(null);
//            }
//        });
//    }
//
//    // Define a callback interface to receive the receiver's phone number asynchronously
//    private interface ReceiverNumberCallback {
//        void onReceiverNumberReceived(String receiverNumber);
//    }
//
//    private String getCurrentUserPhoneNumber() {
//        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
//        return sharedPreferences.getString("phoneNumber", "");
//    }
//    private ChatMessage createChatMessageFromSnapshot(DataSnapshot snapshot) {
//        String senderId = snapshot.child("sender_id").getValue(String.class);
//        String receiverId = snapshot.child("receiver_id").getValue(String.class); // Correctly retrieve receiverId
//        String receiverNo = snapshot.child("receiver_no").getValue(String.class); // Correctly retrieve receiverId
//        String encryptedMessageText = snapshot.child("messageText").getValue(String.class);
//        String receiverName = snapshot.child("receiverName").getValue(String.class);
//        String timestampString = snapshot.child("timestamp").getValue(String.class);
//        long timestamp = 0;
//
//        if (timestampString != null && !timestampString.isEmpty()) {
//            try {
//                SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yy HH:mm:ss", Locale.getDefault());
//                Date date = dateFormat.parse(snapshot.child("date").getValue(String.class) + " " + timestampString);
//                timestamp = date.getTime();
//            } catch (ParseException e) {
//                e.printStackTrace();
//            }
//        }
//
//        boolean sent = snapshot.child("sent").getValue(Boolean.class);
//
//        // Decrypt the encrypted message text
//        String decryptedMessageText = aes.Decrypt(encryptedMessageText, com.example.cryptochat.HomeActivity.this); // Assuming 'aes' is an instance of your AES class and 'context' is the current context
//
//        ChatMessage message = new ChatMessage(senderId, receiverNo, decryptedMessageText, timestamp, sent, receiverName); // Pass receiverId to ChatMessage constructor
//        message.setEncryptedMessageText(encryptedMessageText);
//        message.setTimestampMillis(timestamp);
//
//        return message;
//    }
//
//    private void startChatWithContact(String contactId, String contactName) {
//        Intent intent = new Intent(HomeActivity.this, ChatActivity.class);
//        intent.putExtra(ChatActivity.FRIEND_ID, contactId);
//        intent.putExtra(ChatActivity.FRIEND_NAME, contactName);
////        intent.putExtra(ChatActivity.SELECTED_FRIEND_PHONE_NUMBER, receiverNo);
//        startActivityForResult(intent, REQUEST_CHAT_ACTIVITY);
//    }
//    private void getFullNameFromDatabase(String userId, final FullNameCallback callback) {
//        if (userId != null) {
//            DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference().child("users").child(userId);
//            usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
//                @Override
//                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                    if (dataSnapshot.exists()) {
//                        // Retrieve the full name from the dataSnapshot
//                        String fullName = dataSnapshot.child("fullName").getValue(String.class);
//
//                        // Pass the retrieved full name to the callback method
//                        callback.onFullNameReceived(fullName);
//                    } else {
//                        // Handle the case where the user data does not exist
//                        callback.onFullNameReceived(null);
//                    }
//                }
//
//                @Override
//                public void onCancelled(@NonNull DatabaseError databaseError) {
//                    // Handle potential errors
//                    callback.onFullNameReceived(null);
//                }
//            });
//        } else {
//            // Handle the case where userId is null
//            callback.onFullNameReceived(null);
//        }
//    }
//    // Define a callback interface to receive the full name asynchronously
//    private interface FullNameCallback {
//        void onFullNameReceived(String fullName);
//    }
//    private void updateChatItemWithLatestMessage(ChatMessage latestMessage) {
//        if (latestMessage == null) {
//            // Handle the case where latestMessage is null
//            return;
//        }
//
//        // Extract necessary information from the latestMessage
//        String friendName = latestMessage.getReceiverName(); // Assuming receiverName is the friend's name
//        String lastMessage = latestMessage.getMessageText();
//        long timestamp = latestMessage.getTimestampMillis();
//        String senderId = latestMessage.getSenderId();
//
//        // Find the corresponding ChatItem for the friend
//        ChatItem chatItem = findChatItemForContact(friendName);
//        if (chatItem == null) {
//            // If the ChatItem doesn't exist, create a new one
//            chatItem = new ChatItem();
//            chatItem.setContactName(friendName);
//            chatItem.setLastMessage(lastMessage);
//            chatItem.setTimestamp(timestamp);
//            chatItems.add(chatItem);
//        } else {
//            // If the ChatItem exists and the latest message was received
//            if (!latestMessage.isSentByCurrentUser(senderId) && timestamp > chatItem.getTimestamp()) {
//                // Update its last message and timestamp
//                chatItem.setLastMessage(lastMessage);
//                chatItem.setTimestamp(timestamp);
//            }
//        }
//
//        // Update the RecyclerView adapter
//        updateRecyclerViewAdapter();
//    }
//    @Override
//    public void onBackPressed() {
//        if (doubleBackToExitPressedOnce) {
//            super.onBackPressed();
//            return;
//        }
//
//        this.doubleBackToExitPressedOnce = true;
//        Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
//
//        new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
//    }
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        if (requestCode == REQUEST_CHAT_ACTIVITY && resultCode == RESULT_OK && data != null) {
//            // Extract the ChatMessage object from the intent received from ChatActivity
//            ChatMessage latestMessage = data.getParcelableExtra(ChatActivity.EXTRA_LAST_MESSAGE);
//
//            // Update the chat item with the latest message
//            updateChatItemWithLatestMessage(latestMessage);
//        }
//    }
//    @Override
//    protected void onResume() {
//        super.onResume();
//        updateChatItems();
//    }
//}
//public class HomeActivity extends AppCompatActivity {
//
//    private RecyclerView chatRecyclerView;
//    private ImageView searchIcon, optionIcon, plusIcon;
//    private SwipeRefreshLayout swipeRefreshLayout;
//    private SearchView searchView;
//    private List<ChatItem> chatItems = new ArrayList<>();
//    private List<ChatItem> filteredChatItems = new ArrayList<>();
//    private String userId;
//    private String receiverId;
//    private String receiverNo;
//    public static final String FRIEND_ID = "friendId";
//    public static final String FRIEND_NAME = "friendName";
//    public static final String SELECTED_FRIEND_PHONE_NUMBER = "selected_friend_phone_number";
//    private AES aes;
//    private static final int REQUEST_CONTACT_SELECTION = 1;
//    private static final int REQUEST_CHAT_ACTIVITY = 2;
//    private List<Contact> contactsList = new ArrayList<>();
//    private boolean doubleBackToExitPressedOnce = false;
//    private static final String PREFS_NAME = "MyPrefs";
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_home);
//        // Initialize Firebase Crashlytics
//        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
//
//        initializeUI();
//        setupRecyclerView();
//        setListeners();
//
//        // Load the contacts list for the receiver's device
//        loadContactsList();
//
//        // Get the intent extras
//        String friendId = getIntent().getStringExtra(FRIEND_ID);
//        String friendName = getIntent().getStringExtra(FRIEND_NAME);
////        String phoneNumber = getIntent().getStringExtra(SELECTED_FRIEND_PHONE_NUMBER);
//        receiverNo = getIntent().getStringExtra(SELECTED_FRIEND_PHONE_NUMBER);
//
//        // Log the received values
//        Log.d("DEBUG", "Received friendId: " + friendId + ", friendName: " + friendName + ", phoneNumber: " + receiverNo);
//
//    }
//    private void initializeUI() {
//        searchView = findViewById(R.id.searchView);
//        searchIcon = findViewById(R.id.searchIcon);
//        optionIcon = findViewById(R.id.optionIcon);
//        plusIcon = findViewById(R.id.plusIcon);
//        chatRecyclerView = findViewById(R.id.chatRecyclerView);
//        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
//        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
//        new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
//    }
//    private void setupRecyclerView() {
//
//        // Retrieve the phone number from SharedPreferences
//        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
//        String phoneNumber = sharedPreferences.getString("phoneNumber", "");
//
//        // Get the current user from Firebase Authentication
//        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
//
//        if (currentUser != null) {
//            userId = currentUser.getUid(); // Set userId to the UID of the logged-in user
//            Log.d("DEBUG", "Current User ID: " + userId);
//
//        } else {
//            // Handle the case where the user is not logged in
//        }
//
//        aes = new AES(this);
//
//        // Set up the RecyclerView with an adapter for chat messages
////        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
////        chatRecyclerView.setLayoutManager(layoutManager);
////        ChatItemAdapter adapter = new ChatItemAdapter(filteredChatItems, position -> {
////            String receiverNo = getCurrentReceiverNo(String.valueOf(filteredChatItems.get(position)));
////            startChatWithContact(filteredChatItems.get(position).getContactId(),
////                    filteredChatItems.get(position).getContactName(),
////                    receiverNo);
////        }, this);
////        chatRecyclerView.setAdapter(adapter);
//        // Set up the RecyclerView with an adapter for chat messages
//        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
//        chatRecyclerView.setLayoutManager(layoutManager);
//        ChatItemAdapter adapter = new ChatItemAdapter(filteredChatItems, position -> {
//            // Get the contactId of the clicked item
//            String contactId = filteredChatItems.get(position).getContactId();
//            // Call getCurrentReceiverNo with the contactId and provide a callback
//            getCurrentReceiverNo(contactId, new ReceiverNumberCallback() {
//                @Override
//                public void onReceiverNumberReceived(String receiverNo) {
//                    if (receiverNo != null) {
//                        // Start the chat activity with the received receiver number
//                        startChatWithContact(contactId,
//                                filteredChatItems.get(position).getContactName(),
//                                receiverNo);
//                    } else {
//                        // Handle the case where the receiver number is null
//                        Log.d("DEBUG", "Receiver number is null");
//                        // You may want to show an error message or handle this case differently
//                    }
//                }
//            });
//        }, this);
//        chatRecyclerView.setAdapter(adapter);
//
//    }
//    private void setListeners() {
//        swipeRefreshLayout.setOnRefreshListener(() -> {
//            // Trigger refresh action
//            refreshData();
//            updateChatItems();
//
//        });
//
//        // Search icon click listener
//        searchIcon.setOnClickListener(v -> {
//            // Show or hide the search bar based on its current visibility
//            int visibility = searchView.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE;
//            searchView.setVisibility(visibility);
//
//            // If the search bar becomes visible, focus and open the keyboard
//            if (visibility == View.VISIBLE) {
//                searchView.setIconified(false);
//                searchView.requestFocus();
//            }
//
//            // Clear the search query when hiding the search bar
//            if (visibility == View.INVISIBLE) {
//                searchView.setQuery("", false);  // Clear the search query
//                filterChatItems("");  // Filter with an empty query to show all items
//            }
//        });
//
//        // SearchView query listener
//        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
//            @Override
//            public boolean onQueryTextSubmit(String query) {
//                // Handle search query submission if needed
//                return false;
//            }
//
//            @Override
//            public boolean onQueryTextChange(String newText) {
//                // Filter the chatItems based on the search query as the user types
//                filterChatItems(newText);
//                return true;
//            }
//        });
//
//
//        // Option icon click listener
//        optionIcon.setOnClickListener(v -> showOptionsMenu(v));
//
//        // Plus icon click listener
//        plusIcon.setOnClickListener(v -> startContactsActivity());
//
//    }
//    private void startContactsActivity() {
//        Intent contactsIntent = new Intent(HomeActivity.this, ContactsActivity.class);
//        startActivityForResult(contactsIntent, REQUEST_CONTACT_SELECTION);
//    }
//    private void showOptionsMenu(View v) {
//        PopupMenu popupMenu = new PopupMenu(this, optionIcon);
//        MenuInflater inflater = popupMenu.getMenuInflater();
//        inflater.inflate(R.menu.menu_home, popupMenu.getMenu());
//
//        popupMenu.setOnMenuItemClickListener(item -> {
//            int itemId = item.getItemId();
//            if (itemId == R.id.menu_my_profile) {
//                startProfileActivity();
//                return true;
//            } else if (itemId == R.id.menu_logout) {
//                logoutAndNavigateToLogin();
//                return true;
//            } else {
//                return false;
//            }
//        });
//
//        popupMenu.show();
//    }
//    private void startProfileActivity() {
//        Intent profileIntent = new Intent(HomeActivity.this, ProfileActivity.class);
//        profileIntent.putExtra("phoneNumber", getPhoneNumberFromPreferences());
//        startActivity(profileIntent);
//    }
//    private String getPhoneNumberFromPreferences() {
//        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
//        return sharedPreferences.getString("phoneNumber", "");
//    }
//    private void logoutAndNavigateToLogin() {
//        FirebaseAuth.getInstance().signOut();
//
//        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//        startActivity(intent);
//        finish();
//    }
////    private void loadContactsList() {
////        // Retrieve the phone number from SharedPreferences
////        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
////        String phoneNumber = sharedPreferences.getString("phoneNumber", "");
////
////        // Fetch contacts asynchronously
////        new AsyncTask<Void, Void, List<Contact>>() {
////            @Override
////            protected List<Contact> doInBackground(Void... voids) {
////                // Replace this with your actual method to fetch contacts based on the receiver's phone number
////                return getContactsForPhoneNumber(phoneNumber);
////            }
////
////            @Override
////            protected void onPostExecute(List<Contact> contacts) {
////                super.onPostExecute(contacts);
////                // Update the contactsList and filterChatItems
////                contactsList.clear();
////                contactsList.addAll(contacts);
////                filterChatItems("");
////            }
////        }.execute();
////    }
//    private void loadContactsList() {
//        // Retrieve the phone number from SharedPreferences
//        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
//        String phoneNumber = sharedPreferences.getString("phoneNumber", "");
//
//        // Fetch contacts asynchronously
//        new AsyncTask<Void, Void, List<Contact>>() {
//            @Override
//            protected List<Contact> doInBackground(Void... voids) {
//                // Replace this with your actual method to fetch contacts based on the receiver's phone number
//                return getContactsForPhoneNumber(phoneNumber);
//            }
//
//            @Override
//            protected void onPostExecute(List<Contact> contacts) {
//                super.onPostExecute(contacts);
//                // Update the contactsList and filterChatItems
//                contactsList.clear();
//                contactsList.addAll(contacts);
//                // Log the contents of contactsList
//                Log.d("DEBUG", "Contacts List: " + contactsList);
//                filterChatItems("");
//            }
//        }.execute();
//    }
//    private List<Contact> getContactsForPhoneNumber(String phoneNumber) {
//        List<Contact> contacts = new ArrayList<>();
//
//        // Define the query parameters
//        String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
//                ContactsContract.CommonDataKinds.Phone.NUMBER};
//        String selection = ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?";
//        String[] selectionArgs = new String[]{phoneNumber};
//
//        // Query the contacts using ContentResolver
//        Cursor cursor = getContentResolver().query(
//                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
//                projection,
//                selection,
//                selectionArgs,
//                null
//        );
//
//        if (cursor != null) {
//            try {
//                int nameColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
//                int phoneNumberColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
//
//                while (cursor.moveToNext()) {
//                    // Check if the column indices are valid
//                    if (nameColumnIndex != -1 && phoneNumberColumnIndex != -1) {
//                        String contactName = cursor.getString(nameColumnIndex);
//                        String contactPhoneNumber = cursor.getString(phoneNumberColumnIndex);
//
//                        Contact contact = new Contact();
//                        contact.setName(contactName);
//                        contact.setPhoneNumber(contactPhoneNumber);
//                        contacts.add(contact);
//                    }
//                }
//            } finally {
//                cursor.close();
//            }
//        }
//
//        return contacts;
//    }
//    private void filterChatItems(String query) {
//        filteredChatItems.clear();
//
//        // Iterate through chatItems and add items that match the search query
//        for (ChatItem chatItem : chatItems) {
//            if (chatItem.getContactName().toLowerCase().contains(query.toLowerCase())) {
//                filteredChatItems.add(chatItem);
//            }
//        }
//
//        // Update the RecyclerView adapter with the filtered list
//        ChatItemAdapter chatItemAdapter = new ChatItemAdapter(filteredChatItems, position -> {
//            // Access receiverId from the enclosing scope
//            startChatWithContact(filteredChatItems.get(position).getContactId(),
//                    filteredChatItems.get(position).getContactName(),
//                    receiverNo);
//        }, HomeActivity.this);
//
//        chatRecyclerView.setAdapter(chatItemAdapter);
//    }
//    private void processMessages(DataSnapshot dataSnapshot) {
//        for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
//            // Retrieve message details from the snapshot
//            String messageId = chatSnapshot.getKey();
//            String senderId = chatSnapshot.child("sender_id").getValue(String.class);
//            String receiverId = chatSnapshot.child("receiver_id").getValue(String.class);
//            String receiverName = chatSnapshot.child("receiverName").getValue(String.class);
//            String receiverNo = chatSnapshot.child("receiver_no").getValue(String.class);
//            String messageText = chatSnapshot.child("messageText").getValue(String.class);
//            boolean sent = chatSnapshot.child("sent").getValue(Boolean.class);
//            String timestamp = chatSnapshot.child("timestamp").getValue(String.class);
//
//            // Log all message details
//            Log.d("PROCESS_MESSAGES", "Message ID: " + messageId);
//            Log.d("PROCESS_MESSAGES", "Sender ID: " + senderId);
//            Log.d("PROCESS_MESSAGES", "Receiver ID: " + receiverId);
//            Log.d("PROCESS_MESSAGES", "Receiver Name: " + receiverName);
//            Log.d("PROCESS_MESSAGES", "Receiver No: " + receiverNo);
//            Log.d("PROCESS_MESSAGES", "Message Text: " + messageText);
//            Log.d("PROCESS_MESSAGES", "Sent: " + sent);
//            Log.d("PROCESS_MESSAGES", "Timestamp: " + timestamp);
//
//            // Check if the message involves the current user as either sender or receiver
//            boolean sentByCurrentUser = senderId.equals(userId);
//            boolean receivedByCurrentUser = receiverId.equals(userId);
//
//            // Log current user ID and message relevance
//            Log.d("PROCESS_MESSAGES", "Current User ID: " + userId);
//            Log.d("PROCESS_MESSAGES", "Sent by Current User: " + sentByCurrentUser);
//            Log.d("PROCESS_MESSAGES", "Received by Current User: " + receivedByCurrentUser);
//
//            // Add messages involving the current user to the chatItems list
//            if (sentByCurrentUser || receivedByCurrentUser) {
//                String contactId = sentByCurrentUser ? receiverId : senderId;
//                ChatMessage message = createChatMessageFromSnapshot(chatSnapshot);
//                addOrUpdateChatItem(contactId, receiverName, message);
//            } else {
//                // This message is irrelevant only if it doesn't involve the current user
//                Log.d("PROCESS_MESSAGES", "Irrelevant message: " + messageText);
//            }
//        }
//    }
//    private void addOrUpdateChatItem(String contactId, String contactName, ChatMessage message) {
//        // Find or create chat item for the contact
//        ChatItem chatItem = findChatItemForContact(contactId);
//        if (chatItem == null) {
//            // Create a new chat item if it doesn't exist
//            chatItem = new ChatItem();
//            chatItem.setContactId(contactId);
//            chatItem.setContactName(contactName);
//            chatItems.add(chatItem);
//        }
//
//        // Update the chat item with the latest message
//        chatItem.addChatMessage(message);
//
//        Log.d("ADD_OR_UPDATE_ITEM", "Chat item updated: " + chatItem.getContactName() + " - Latest message: " + message.getMessageText());
//    }
//    private void updateChatItems() {
//        DatabaseReference messagesRef = FirebaseDatabase.getInstance().getReference().child("messages");
//
//        messagesRef.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                // Clear existing chat items
//                chatItems.clear();
//
//                // Process all messages
//                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
//                    processMessages(chatSnapshot);
//                }
//
//                // Update the RecyclerView adapter
//                updateRecyclerViewAdapter();
//
//                // Set the last message text for each chat item
//                setLastMessageTextForChatItems();
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError databaseError) {
//                // Handle cancellation of database operations if needed
//            }
//        });
//    }
////    private void updateRecyclerViewAdapter() {
////        // Sort the chatItems based on the timestamp of the latest message in each ChatItem
////        Collections.sort(chatItems, (item1, item2) -> {
////            ChatMessage latestMessage1 = item1.getLatestMessage();
////            ChatMessage latestMessage2 = item2.getLatestMessage();
////            if (latestMessage1 != null && latestMessage2 != null) {
////                return Long.compare(latestMessage2.getTimestampMillis(), latestMessage1.getTimestampMillis());
////            } else if (latestMessage1 != null) {
////                return -1; // item1 has a message, so it should come first
////            } else if (latestMessage2 != null) {
////                return 1; // item2 has a message, so it should come first
////            } else {
////                return 0; // Both items have no messages or null messages, no preference for order
////            }
////        });
////
////        // Update the RecyclerView adapter
////        runOnUiThread(() -> {
////            ChatItemAdapter adapter = new ChatItemAdapter(chatItems, position -> {
////                // Handle item click here
////                ChatItem clickedItem = chatItems.get(position);
//////                String receiverNo = getCurrentReceiverNo(clickedItem);
////                String receiverNo = getCurrentReceiverNo(receiverId);
////                startChatWithContact(clickedItem.getContactId(), clickedItem.getContactName(), receiverNo);
////            }, HomeActivity.this);
////            chatRecyclerView.setAdapter(adapter);
////        });
////    }
//
//    private void updateRecyclerViewAdapter() {
//        // Sort the chatItems based on the timestamp of the latest message in each ChatItem
//        Collections.sort(chatItems, (item1, item2) -> {
//            ChatMessage latestMessage1 = item1.getLatestMessage();
//            ChatMessage latestMessage2 = item2.getLatestMessage();
//            if (latestMessage1 != null && latestMessage2 != null) {
//                return Long.compare(latestMessage2.getTimestampMillis(), latestMessage1.getTimestampMillis());
//            } else if (latestMessage1 != null) {
//                return -1; // item1 has a message, so it should come first
//            } else if (latestMessage2 != null) {
//                return 1; // item2 has a message, so it should come first
//            } else {
//                return 0; // Both items have no messages or null messages, no preference for order
//            }
//        });
//
//        // Update the RecyclerView adapter
//        runOnUiThread(() -> {
//            ChatItemAdapter adapter = new ChatItemAdapter(chatItems, position -> {
//                // Handle item click here
//                ChatItem clickedItem = chatItems.get(position);
//                // Get the contactId of the clicked item
//                String contactId = clickedItem.getContactId();
//                // Call getCurrentReceiverNo with the contactId and provide a callback
//                getCurrentReceiverNo(contactId, new ReceiverNumberCallback() {
//                    @Override
//                    public void onReceiverNumberReceived(String receiverNo) {
//                        if (receiverNo != null) {
//                            // Start the chat activity with the received receiver number
//                            startChatWithContact(contactId, clickedItem.getContactName(), receiverNo);
//                        } else {
//                            // Handle the case where the receiver number is null
//                            Log.d("DEBUG", "Receiver number is null");
//                            // You may want to show an error message or handle this case differently
//                        }
//                    }
//                });
//            }, HomeActivity.this);
//            chatRecyclerView.setAdapter(adapter);
//        });
//    }
//
//    // Method to set the last message text for each chat item
//    private void setLastMessageTextForChatItems() {
//        for (ChatItem chatItem : chatItems) {
//            ChatMessage latestMessage = chatItem.getLatestMessage();
//            if (latestMessage != null) {
//                String lastMessageText = latestMessage.getMessageText();
//                chatItem.setLastMessageText(lastMessageText);
//            }
//        }
//    }
//    private ChatItem findChatItemForContact(String contactId) {
//        for (ChatItem chatItem : chatItems) {
//            if (chatItem.getContactId().equals(contactId)) {
//                return chatItem;
//            }
//        }
//        return null;
//    }
//    private void refreshData() {
//        // Perform data refresh operation, e.g., reload messages from Firebase
//        // Once the data is refreshed, update the RecyclerView adapter
//        // You can call your existing updateChatItems() method here or implement a similar logic
//        updateChatItems(); // Example call
//
//        // After refreshing data, complete the refreshing animation
//        swipeRefreshLayout.setRefreshing(false);
//    }
//    // Method to retrieve the receiverId based on the selected ChatItem
////    private String getCurrentReceiverNo(ChatItem chatItem) {
////        // Determine the receiverId based on the current user's phone number and the contactId
////        String currentUserPhoneNumber = getCurrentUserPhoneNumber();
////        return currentUserPhoneNumber.equals(chatItem.getContactId()) ? chatItem.getMessages().get(0).getReceiverId() : chatItem.getContactId();
////    }
//    // Method to retrieve the phone number for a given contactId
//    private void getCurrentReceiverNo(String contactId, ReceiverNumberCallback callback) {
//        DatabaseReference messagesRef = FirebaseDatabase.getInstance().getReference().child("messages");
//
//        messagesRef.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
//                    // Check if the contactId matches the receiver_id in any message
//                    String receiverId = chatSnapshot.child("receiver_id").getValue(String.class);
//                    if (receiverId != null && receiverId.equals(contactId)) {
//                        // If found, return the receiver's phone number
//                        String receiverNo = chatSnapshot.child("receiver_no").getValue(String.class);
//                        if (receiverNo != null) {
//                            Log.d("DEBUG", "Receiver number found: " + receiverNo);
//                            // Call the callback with the receiver's phone number
//                            callback.onReceiverNumberReceived(receiverNo);
//                            return; // Exit the loop once the receiver's number is found
//                        }
//                    }
//                }
//                // If the receiver's phone number is not found, call the callback with null
//                Log.d("DEBUG", "Receiver number not found for contactId: " + contactId);
//                callback.onReceiverNumberReceived(null);
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError databaseError) {
//                // Handle cancellation of database operations if needed
//                Log.d("DEBUG", "Error retrieving receiver number: " + databaseError.getMessage());
//                // Call the callback with null in case of error
//                callback.onReceiverNumberReceived(null);
//            }
//        });
//    }
//
//    // Define a callback interface to receive the receiver's phone number asynchronously
//    private interface ReceiverNumberCallback {
//        void onReceiverNumberReceived(String receiverNumber);
//    }
//
//    private String getCurrentUserPhoneNumber() {
//        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
//        return sharedPreferences.getString("phoneNumber", "");
//    }
//    private ChatMessage createChatMessageFromSnapshot(DataSnapshot snapshot) {
//        String senderId = snapshot.child("sender_id").getValue(String.class);
//        String receiverId = snapshot.child("receiver_id").getValue(String.class); // Correctly retrieve receiverId
//        String receiverNo = snapshot.child("receiver_no").getValue(String.class); // Correctly retrieve receiverId
//        String encryptedMessageText = snapshot.child("messageText").getValue(String.class);
//        String receiverName = snapshot.child("receiverName").getValue(String.class);
//        String timestampString = snapshot.child("timestamp").getValue(String.class);
//        long timestamp = 0;
//
//        if (timestampString != null && !timestampString.isEmpty()) {
//            try {
//                SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yy HH:mm:ss", Locale.getDefault());
//                Date date = dateFormat.parse(snapshot.child("date").getValue(String.class) + " " + timestampString);
//                timestamp = date.getTime();
//            } catch (ParseException e) {
//                e.printStackTrace();
//            }
//        }
//
//        boolean sent = snapshot.child("sent").getValue(Boolean.class);
//
//        // Decrypt the encrypted message text
//        String decryptedMessageText = aes.Decrypt(encryptedMessageText, com.example.cryptochat.HomeActivity.this); // Assuming 'aes' is an instance of your AES class and 'context' is the current context
//
//        ChatMessage message = new ChatMessage(senderId, receiverNo, decryptedMessageText, timestamp, sent, receiverName); // Pass receiverId to ChatMessage constructor
//        message.setEncryptedMessageText(encryptedMessageText);
//        message.setTimestampMillis(timestamp);
//
//        return message;
//    }
////    private void startChatWithContact(String contactId, String contactName, String receiverNo) {
////        Intent intent = new Intent(HomeActivity.this, ChatActivity.class);
////        receiverNo = getCurrentReceiverNo(contactId);
////
////        intent.putExtra(ChatActivity.FRIEND_ID, contactId); // Pass the contact's ID
////        intent.putExtra(ChatActivity.FRIEND_NAME, contactName); // Pass the contact's name
////        intent.putExtra(ChatActivity.SELECTED_FRIEND_PHONE_NUMBER, receiverNo); // Pass the receiver's ID
////        startActivityForResult(intent, REQUEST_CHAT_ACTIVITY); // Start ChatActivity for result
////    }
//
////    private void startChatWithContact(String contactId, String contactName, String receiverNo) {
////        Intent intent = new Intent(HomeActivity.this, ChatActivity.class);
////
////        // Pass the receiver's ID and name
////        intent.putExtra(ChatActivity.FRIEND_ID, contactId);
////        intent.putExtra(ChatActivity.FRIEND_NAME, contactName);
////
////        // Pass the receiver's phone number
////        intent.putExtra(ChatActivity.SELECTED_FRIEND_PHONE_NUMBER, receiverNo);
////
////        startActivityForResult(intent, REQUEST_CHAT_ACTIVITY);
////    }
//private void startChatWithContact(String contactId, String contactName, String receiverNo) {
//    Intent intent = new Intent(HomeActivity.this, ChatActivity.class);
//    intent.putExtra(ChatActivity.FRIEND_ID, contactId);
//    intent.putExtra(ChatActivity.FRIEND_NAME, contactName);
//    intent.putExtra(ChatActivity.SELECTED_FRIEND_PHONE_NUMBER, receiverNo);
//    startActivityForResult(intent, REQUEST_CHAT_ACTIVITY);
//}
//    private void getFullNameFromDatabase(String userId, final FullNameCallback callback) {
//        if (userId != null) {
//            DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference().child("users").child(userId);
//            usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
//                @Override
//                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                    if (dataSnapshot.exists()) {
//                        // Retrieve the full name from the dataSnapshot
//                        String fullName = dataSnapshot.child("fullName").getValue(String.class);
//
//                        // Pass the retrieved full name to the callback method
//                        callback.onFullNameReceived(fullName);
//                    } else {
//                        // Handle the case where the user data does not exist
//                        callback.onFullNameReceived(null);
//                    }
//                }
//
//                @Override
//                public void onCancelled(@NonNull DatabaseError databaseError) {
//                    // Handle potential errors
//                    callback.onFullNameReceived(null);
//                }
//            });
//        } else {
//            // Handle the case where userId is null
//            callback.onFullNameReceived(null);
//        }
//    }
//    // Define a callback interface to receive the full name asynchronously
//    private interface FullNameCallback {
//        void onFullNameReceived(String fullName);
//    }
//    private void updateChatItemWithLatestMessage(ChatMessage latestMessage) {
//        if (latestMessage == null) {
//            // Handle the case where latestMessage is null
//            return;
//        }
//
//        // Extract necessary information from the latestMessage
//        String friendName = latestMessage.getReceiverName(); // Assuming receiverName is the friend's name
//        String lastMessage = latestMessage.getMessageText();
//        long timestamp = latestMessage.getTimestampMillis();
//        String senderId = latestMessage.getSenderId();
//
//        // Find the corresponding ChatItem for the friend
//        ChatItem chatItem = findChatItemForContact(friendName);
//        if (chatItem == null) {
//            // If the ChatItem doesn't exist, create a new one
//            chatItem = new ChatItem();
//            chatItem.setContactName(friendName);
//            chatItem.setLastMessage(lastMessage);
//            chatItem.setTimestamp(timestamp);
//            chatItems.add(chatItem);
//        } else {
//            // If the ChatItem exists and the latest message was received
//            if (!latestMessage.isSentByCurrentUser(senderId) && timestamp > chatItem.getTimestamp()) {
//                // Update its last message and timestamp
//                chatItem.setLastMessage(lastMessage);
//                chatItem.setTimestamp(timestamp);
//            }
//        }
//
//        // Update the RecyclerView adapter
//        updateRecyclerViewAdapter();
//    }
//    @Override
//    public void onBackPressed() {
//        if (doubleBackToExitPressedOnce) {
//            super.onBackPressed();
//            return;
//        }
//
//        this.doubleBackToExitPressedOnce = true;
//        Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
//
//        new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
//    }
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        if (requestCode == REQUEST_CHAT_ACTIVITY && resultCode == RESULT_OK && data != null) {
//            // Extract the ChatMessage object from the intent received from ChatActivity
//            ChatMessage latestMessage = data.getParcelableExtra(ChatActivity.EXTRA_LAST_MESSAGE);
//
//            // Update the chat item with the latest message
//            updateChatItemWithLatestMessage(latestMessage);
//        }
//    }
//    @Override
//    protected void onResume() {
//        super.onResume();
//        updateChatItems();
//    }
//}


//package com.example.cryptochat;
//
//import android.Manifest;
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.content.pm.PackageManager;
//import android.database.Cursor;
//import android.os.AsyncTask;
//import android.os.Bundle;
//import android.os.Handler;
//import android.provider.ContactsContract;
//import android.util.Log;
//import android.view.MenuInflater;
//import android.view.View;
//import android.widget.ImageView;
//import android.widget.PopupMenu;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.appcompat.widget.SearchView;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//import androidx.recyclerview.widget.LinearLayoutManager;
//import androidx.recyclerview.widget.RecyclerView;
//import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
//
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.auth.FirebaseUser;
//import com.google.firebase.crashlytics.FirebaseCrashlytics;
//import com.google.firebase.database.DataSnapshot;
//import com.google.firebase.database.DatabaseError;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.ValueEventListener;
//
//import java.text.ParseException;
//import java.text.SimpleDateFormat;
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Locale;
//import java.util.Map;
//
//public class HomeActivity extends AppCompatActivity {
//
//    private RecyclerView chatRecyclerView;
//    private ImageView searchIcon, optionIcon, plusIcon;
//    private SwipeRefreshLayout swipeRefreshLayout;
//    private SearchView searchView;
//    private List<ChatItem> chatItems = new ArrayList<>();
//    private List<ChatItem> filteredChatItems = new ArrayList<>();
//    private String userId, senderId, receiverId, messageText,  receiverName, encryptedMessageText,  timestampString;
//    private AES aes;
//    private static final int REQUEST_CONTACT_SELECTION = 1;
//    private static final int REQUEST_CHAT_ACTIVITY = 2;
//    private static final int YOUR_REQUEST_CODE = 3;
//    private static final int REQUEST_CONTACTS_PERMISSION = 123;
//    private List<Contact> contactsList = new ArrayList<>();
//    private boolean doubleBackToExitPressedOnce = false;
//    private static final String PREFS_NAME = "MyPrefs";
//    public static final String EXTRA_SELECTED_FRIEND_NAME = "selectedFriendName";
//    public static final String EXTRA_LAST_MESSAGE = "lastMessage";
//    public static final String EXTRA_TIMESTAMP = "timestamp";
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_home);
//        // Initialize Firebase Crashlytics
//        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
//
//        initializeUI();
//        setListeners();
//
//        // Check if the permission is granted
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
//                != PackageManager.PERMISSION_GRANTED) {
//            // Permission is not granted, request it from the user
//            ActivityCompat.requestPermissions(this,
//                    new String[]{Manifest.permission.READ_CONTACTS},
//                    REQUEST_CONTACTS_PERMISSION);
//        } else {
//            // Permission is granted, proceed with accessing contacts
//            loadContactsList();
//        }
//        setupRecyclerView();
//        updateChatItems();
//    }
//    private void initializeUI() {
//        searchView = findViewById(R.id.searchView);
//        searchIcon = findViewById(R.id.searchIcon);
//        optionIcon = findViewById(R.id.optionIcon);
//        plusIcon = findViewById(R.id.plusIcon);
//        chatRecyclerView = findViewById(R.id.chatRecyclerView);
//        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
//        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
//        new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
//    }
//    private void setupRecyclerView() {
//
//        // Retrieve the phone number from SharedPreferences
//        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
//        String phoneNumber = sharedPreferences.getString("phoneNumber", "");
//
//        // Get the current user from Firebase Authentication
//        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
//
//        if (currentUser != null) {
//            userId = currentUser.getUid(); // Set userId to the UID of the logged-in user
//        } else {
//            // Handle the case where the user is not logged in
//        }
//
//        aes = new AES(this);
//
//        // Set up the RecyclerView with an adapter for chat messages
//        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
//        chatRecyclerView.setLayoutManager(layoutManager);
//        ChatItemAdapter adapter = new ChatItemAdapter(filteredChatItems, position -> {
//            receiverId = getCurrentReceiverId(filteredChatItems.get(position));
//            startChatWithContact(filteredChatItems.get(position).getContactId(),
//                    filteredChatItems.get(position).getContactName(),
//                    receiverId);
//        }, this);
//        chatRecyclerView.setAdapter(adapter);
//    }
//    private void setListeners() {
//        swipeRefreshLayout.setOnRefreshListener(() -> {
//            // Trigger refresh action
//            refreshData();
//            updateChatItems();
//
//        });
//
//        // Search icon click listener
//        searchIcon.setOnClickListener(v -> {
//            // Show or hide the search bar based on its current visibility
//            int visibility = searchView.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE;
//            searchView.setVisibility(visibility);
//
//            // If the search bar becomes visible, focus and open the keyboard
//            if (visibility == View.VISIBLE) {
//                searchView.setIconified(false);
//                searchView.requestFocus();
//            }
//
//            // Clear the search query when hiding the search bar
//            if (visibility == View.INVISIBLE) {
//                searchView.setQuery("", false);  // Clear the search query
//                filterChatItems("");  // Filter with an empty query to show all items
//            }
//        });
//
//        // SearchView query listener
//        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
//            @Override
//            public boolean onQueryTextSubmit(String query) {
//                // Handle search query submission if needed
//                return false;
//            }
//
//            @Override
//            public boolean onQueryTextChange(String newText) {
//                // Filter the chatItems based on the search query as the user types
//                filterChatItems(newText);
//                return true;
//            }
//        });
//
//
//        // Option icon click listener
//        optionIcon.setOnClickListener(v -> showOptionsMenu(v));
//
//        // Plus icon click listener
//        plusIcon.setOnClickListener(v -> startContactsActivity());
//
//    }
//    private void startContactsActivity() {
//        Intent contactsIntent = new Intent(HomeActivity.this, ContactsActivity.class);
//        startActivityForResult(contactsIntent, REQUEST_CONTACT_SELECTION);
//    }
//    private void showOptionsMenu(View v) {
//        PopupMenu popupMenu = new PopupMenu(this, optionIcon);
//        MenuInflater inflater = popupMenu.getMenuInflater();
//        inflater.inflate(R.menu.menu_home, popupMenu.getMenu());
//
//        popupMenu.setOnMenuItemClickListener(item -> {
//            int itemId = item.getItemId();
//            if (itemId == R.id.menu_my_profile) {
//                startProfileActivity();
//                return true;
//            } else if (itemId == R.id.menu_logout) {
//                logoutAndNavigateToLogin();
//                return true;
//            } else {
//                return false;
//            }
//        });
//
//        popupMenu.show();
//    }
//    private void startProfileActivity() {
//        Intent profileIntent = new Intent(HomeActivity.this, ProfileActivity.class);
//        profileIntent.putExtra("phoneNumber", getPhoneNumberFromPreferences());
//        startActivity(profileIntent);
//    }
//    private String getPhoneNumberFromPreferences() {
//        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
//        return sharedPreferences.getString("phoneNumber", "");
//    }
//    private void logoutAndNavigateToLogin() {
//        FirebaseAuth.getInstance().signOut();
//
//        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//        startActivity(intent);
//        finish();
//    }
//    private void loadContactsList() {
//        // Retrieve the phone number from SharedPreferences
//        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
//        String phoneNumber = sharedPreferences.getString("phoneNumber", "");
//
//        // Fetch contacts asynchronously
//        new AsyncTask<Void, Void, List<Contact>>() {
//            @Override
//            protected List<Contact> doInBackground(Void... voids) {
//                // Replace this with your actual method to fetch contacts based on the receiver's phone number
//                return getContactsForPhoneNumber(phoneNumber);
//            }
//
//            @Override
//            protected void onPostExecute(List<Contact> contacts) {
//                super.onPostExecute(contacts);
//                // Update the contactsList and filterChatItems
//                contactsList.clear();
//                contactsList.addAll(contacts);
//                filterChatItems("");
//            }
//        }.execute();
//    }
////    private List<Contact> fetchContacts() {
////        List<Contact> contacts = new ArrayList<>();
////
////        // Query the contacts using ContentResolver
////        Cursor cursor = getContentResolver().query(
////                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
////                null,
////                null,
////                null,
////                null
////        );
////
////        if (cursor != null) {
////            try {
////                while (cursor.moveToNext()) {
////                    String contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
////                    String contactPhoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
////
////                    Contact contact = new Contact();
////                    contact.setName(contactName);
////                    contact.setPhoneNumber(contactPhoneNumber);
////                    contacts.add(contact);
////                }
////            } finally {
////                cursor.close();
////            }
////        }
////
////        return contacts;
////    }
//    private List<Contact> getContactsForPhoneNumber(String phoneNumber) {
//        List<Contact> contacts = new ArrayList<>();
//
//        // Define the query parameters
//        String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
//                ContactsContract.CommonDataKinds.Phone.NUMBER};
//        String selection = ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?";
//        String[] selectionArgs = new String[]{phoneNumber};
//
//        // Query the contacts using ContentResolver
//        Cursor cursor = getContentResolver().query(
//                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
//                projection,
//                selection,
//                selectionArgs,
//                null
//        );
//
//        if (cursor != null) {
//            try {
//                int nameColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
//                int phoneNumberColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
//
//                while (cursor.moveToNext()) {
//                    // Check if the column indices are valid
//                    if (nameColumnIndex != -1 && phoneNumberColumnIndex != -1) {
//                        String contactName = cursor.getString(nameColumnIndex);
//                        String contactPhoneNumber = cursor.getString(phoneNumberColumnIndex);
//
//                        Contact contact = new Contact();
//                        contact.setName(contactName);
//                        contact.setPhoneNumber(contactPhoneNumber);
//                        contacts.add(contact);
//                    }
//                }
//            } finally {
//                cursor.close();
//            }
//        }
//
//        return contacts;
//    }
//    private void filterChatItems(String query) {
//        filteredChatItems.clear();
//
//        // Iterate through chatItems and add items that match the search query
//        for (ChatItem chatItem : chatItems) {
//            if (chatItem.getContactName().toLowerCase().contains(query.toLowerCase())) {
//                filteredChatItems.add(chatItem);
//            }
//        }
//
//        // Update the RecyclerView adapter with the filtered list
//        ChatItemAdapter chatItemAdapter = new ChatItemAdapter(filteredChatItems, position -> {
//            // Access receiverId from the enclosing scope
//            startChatWithContact(filteredChatItems.get(position).getContactId(),
//                    filteredChatItems.get(position).getContactName(),
//                    receiverId);
//        }, HomeActivity.this);
//
//
//        chatRecyclerView.setAdapter(chatItemAdapter);
//    }
//    private void updateChatItems() {
//        DatabaseReference messagesRef = FirebaseDatabase.getInstance().getReference().child("messages");
//
//        messagesRef.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                // Clear existing chat items
//                chatItems.clear();
//
//                // Process all messages
//                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
//                    processMessages(chatSnapshot);
//                }
//
//                // Update the RecyclerView adapter
//                updateRecyclerViewAdapter();
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError databaseError) {
//                // Handle cancellation of database operations if needed
//            }
//        });
//    }
//    private void processMessages(DataSnapshot dataSnapshot) {
//        for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
//            // Retrieve sender ID, receiver ID, and receiver name from the message snapshot
//            senderId = messageSnapshot.child("sender_id").getValue(String.class);
//            receiverId = messageSnapshot.child("receiver_id").getValue(String.class);
//            receiverName = messageSnapshot.child("receiverName").getValue(String.class);
//
//            // Log the sender and receiver IDs for debugging
//            Log.d("Message Processing", "Sender ID: " + senderId + ", Receiver ID: " + receiverId);
//
//            // Ensure senderId and receiverId are not null before proceeding
//            if (senderId != null && receiverId != null) {
//                // Check if the message was sent by the current user (userId)
//                if (senderId.equals(userId)) {
//                    // Process sender's messages
//                    addOrUpdateChatItem(receiverId, receiverName, createChatMessageFromSnapshot(messageSnapshot));
//                } else if (receiverId.equals(userId)) {
//                    // Process receiver's messages
//                    addOrUpdateChatItem(senderId, receiverName, createChatMessageFromSnapshot(messageSnapshot));
//                } else {
//                    // Log an error if neither sender nor receiver ID matches the current user's ID
//                    Log.e("Process Messages", "Neither sender nor receiver ID matches the current user's ID");
//                }
//            } else {
//                // Handle the case where senderId or receiverId is null
//                Log.e("Process Messages", "senderId or receiverId is null");
//            }
//        }
//    }
//
//    private long parseTimestamp(String timestamp) {
//        return 0;
//    }
//    private void addOrUpdateChatItem(String contactId, String contactName, ChatMessage message) {
//        // Find or create chat item for the contact
//        ChatItem chatItem = findChatItemForContact(contactId);
//        if (chatItem == null) {
//            // Create a new chat item if it doesn't exist
//            chatItem = new ChatItem();
//            chatItem.setContactId(contactId);
//            chatItem.setContactName(contactName);
//            chatItems.add(chatItem);
//        }
//
//        // Update the chat item with the latest message
//        chatItem.addChatMessage(message);
//        Log.d("Chat Item Processing", "Contact ID: " + contactId + ", Message: " + message.getMessageText());
//
//        // Update the RecyclerView adapter
//        updateRecyclerViewAdapter();
//    }
//    private ChatItem findChatItemForContact(String contactId) {
//        for (ChatItem chatItem : chatItems) {
//            if (chatItem.getContactId().equals(contactId)) {
//                return chatItem;
//            }
//        }
//        return null;
//    }
//    private void updateRecyclerViewAdapter() {
//        // Update the RecyclerView adapter with the filtered chatItems
//        ChatItemAdapter adapter = new ChatItemAdapter(filteredChatItems, position -> {
//            // Handle item click here
//            ChatItem clickedItem = filteredChatItems.get(position);
//            receiverId = getCurrentReceiverId(clickedItem);
//            startChatWithContact(clickedItem.getContactId(), clickedItem.getContactName(), receiverId);
//        }, HomeActivity.this);
//        chatRecyclerView.setAdapter(adapter);
//    }
//    private void populateLastMessages() {
//        for (int i = 0; i < chatItems.size(); i++) {
//            ChatItem chatItem = chatItems.get(i);
//            ChatMessage latestMessage = chatItem.getLatestMessage();
//            if (latestMessage != null) {
//                String lastMessage = latestMessage.getMessageText();
//
//                // Update the TextView with the last message
//                RecyclerView.LayoutManager layoutManager = chatRecyclerView.getLayoutManager();
//                if (layoutManager != null) {
//                    View itemView = layoutManager.findViewByPosition(i);
//                    if (itemView != null) {
//                        TextView lastMessageTextView = itemView.findViewById(R.id.lastMessageTextView);
//                        if (lastMessageTextView != null) {
//                            lastMessageTextView.setText(lastMessage);
//                        }
//
//                        // You can set the last message for each ChatItem similarly
//                    }
//                }
//            }
//        }
//    }
//    private void addOrUpdateChatItemForUser(String contactId, String contactName, String userId, ChatMessage message) {
//    // Find or create chat item for the user
//    ChatItem chatItem = findChatItemForUser(userId, contactId);
//    if (chatItem == null) {
//        chatItem = new ChatItem();
//        chatItem.setContactId(contactId);
//        chatItem.setContactName(contactName);
//        chatItems.add(chatItem);
//    }
//
//    // Update the latest message for the user
//    chatItem.addChatMessage(message);
//
//    // Update the RecyclerView adapter
//    updateRecyclerViewAdapter();
//}
//    private ChatItem findChatItemForUser(String userId, String contactId) {
//        for (ChatItem chatItem : chatItems) {
//            if (chatItem.getContactId().equals(contactId)) {
//                return chatItem;
//            }
//        }
//        return null;
//    }
//    private void updateChatItemAndAdapter(ChatItem chatItem, ChatMessage latestMessage) {
//        String senderNo = latestMessage.getSenderNo();
//        receiverId = latestMessage.getReceiverId();
//
//        // Determine the contact ID based on the sender and receiver
//        String currentUserPhoneNumber = getCurrentUserPhoneNumber();
//        String contactId = currentUserPhoneNumber.equals(senderNo) ? receiverId : senderNo;
//
//        // Check if contactId is null
//        if (contactId != null) {
//            boolean chatItemExists = false;
//
//            // Iterate through existing chatItems to find if a ChatItem with the same contactId exists
//            for (ChatItem existingChatItem : chatItems) {
//                if (existingChatItem.getContactId().equals(contactId)) {
//                    chatItemExists = true;
//                    // Update the existing ChatItem with the latest message
//                    existingChatItem.addChatMessage(latestMessage); // Assuming addChatMessage() method exists
//                    break;
//                }
//            }
//
//            // If the ChatItem doesn't exist, create a new one
//            if (!chatItemExists) {
//                chatItem.setContactId(contactId);
//
//                // Retrieve the contact's full name from the database
//                getFullNameFromDatabase(contactId, fullName -> {
//                    if (fullName != null) {
//                        chatItem.setContactName(fullName);
//                    } else {
//                        // If full name is not found, set it as "Unknown Contact"
//                        chatItem.setContactName("Unknown Contact");
//                    }
//
//                    // Add the latest message to the chat item
//                    List<ChatMessage> messages = new ArrayList<>();
//                    messages.add(latestMessage);
//                    chatItem.setMessages(messages);
//                    chatItems.add(chatItem);
//
//                    // Update the RecyclerView adapter
//                    updateRecyclerViewAdapter();
//                });
//            } else {
//                // If the ChatItem already exists, update the RecyclerView adapter
//                updateRecyclerViewAdapter();
//            }
//        } else {
//            Log.e("Error", "Contact ID is null");
//        }
//    }
//    private List<ChatItem> getUniqueChatItems(List<ChatItem> chatItems) {
//        Map<String, ChatItem> uniqueItemsMap = new HashMap<>();
//        for (ChatItem item : chatItems) {
//            if (!uniqueItemsMap.containsKey(item.getContactId())) {
//                uniqueItemsMap.put(item.getContactId(), item);
//            }
//        }
//        return new ArrayList<>(uniqueItemsMap.values());
//    }
//    private void refreshData() {
//        // Perform data refresh operation, e.g., reload messages from Firebase
//        // Once the data is refreshed, update the RecyclerView adapter
//        // You can call your existing updateChatItems() method here or implement a similar logic
//        updateChatItems(); // Example call
//
//        // After refreshing data, complete the refreshing animation
//        swipeRefreshLayout.setRefreshing(false);
//    }
//    // Method to retrieve the receiverId based on the selected ChatItem
//    private String getCurrentReceiverId(ChatItem chatItem) {
//        // Determine the receiverId based on the current user's phone number and the contactId
//        String currentUserPhoneNumber = getCurrentUserPhoneNumber();
//        return currentUserPhoneNumber.equals(chatItem.getContactId()) ? chatItem.getMessages().get(0).getReceiverId() : chatItem.getContactId();
//    }
//    private String getCurrentUserPhoneNumber() {
//        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
//        return sharedPreferences.getString("phoneNumber", "");
//    }
//    private boolean isChatRoomForCurrentUser(String chatRoomId) {
//        return chatRoomId.contains(userId);
//    }
//    // Define the updateUI method to update the UI with received data
//    private void updateUI(String selectedFriendName, String lastMessage, long timestamp) {
//        // Example: Update a TextView with the selected friend's name
//        TextView friendNameTextView = findViewById(R.id.contactNameTextView);
//        friendNameTextView.setText(selectedFriendName);
//
//        // Example: Update another TextView with the last message
//        TextView lastMessageTextView = findViewById(R.id.lastMessageTextView);
//        lastMessageTextView.setText(lastMessage);
//
//        // Example: Format the timestamp and update another TextView with it
//        TextView timestampTextView = findViewById(R.id.timestampTextView);
//        String formattedTimestamp = formatTimestamp(timestamp);
//        timestampTextView.setText(formattedTimestamp);
//
//        // You can add more UI updates as needed
//    }
//    // Example method to format timestamp (you can implement it according to your requirements)
//    private String formatTimestamp(long timestamp) {
//        // Implement your logic to format the timestamp here
//        // For example:
//        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
//        return sdf.format(new Date(timestamp));
//    }
//    private ChatMessage createChatMessageFromSnapshot(DataSnapshot snapshot) {
//        senderId = snapshot.child("sender_id").getValue(String.class);
//        receiverId = snapshot.child("receiver_id").getValue(String.class); // Correctly retrieve receiverId
//        messageText = snapshot.child("messageText").getValue(String.class);
//        receiverName = snapshot.child("receiverName").getValue(String.class);
//        encryptedMessageText = snapshot.child("messageText").getValue(String.class);
//        timestampString = snapshot.child("timestamp").getValue(String.class);
//        long timestamp = 0;
//
//        if (timestampString != null && !timestampString.isEmpty()) {
//            try {
//                SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yy HH:mm:ss", Locale.getDefault());
//                Date date = dateFormat.parse(snapshot.child("date").getValue(String.class) + " " + timestampString);
//                if (date != null) {
//                    timestamp = date.getTime();
//                }
//            } catch (ParseException e) {
//                e.printStackTrace();
//            }
//        }
//
//        Boolean sentBoolean = snapshot.child("sent").getValue(Boolean.class);
//        boolean sent = sentBoolean != null ? sentBoolean.booleanValue() : false;
//
//        ChatMessage message = new ChatMessage(senderId, receiverId, messageText, timestamp, sent, receiverName); // Pass receiverId to ChatMessage constructor
//        message.setEncryptedMessageText(encryptedMessageText);
//        message.setTimestampMillis(timestamp);
//
//        return message;
//    }
//    private void startChatWithContact(String contactId, String contactName, String receiverId) {
//        Intent intent = new Intent(HomeActivity.this, ChatActivity.class);
//        intent.putExtra(ChatActivity.FRIEND_ID, contactId); // Pass the contact's ID
//        intent.putExtra(ChatActivity.FRIEND_NAME, contactName); // Pass the contact's name
//        intent.putExtra(ChatActivity.SELECTED_FRIEND_PHONE_NUMBER, receiverId); // Pass the receiver's ID
//        startActivity(intent);
//    }
//    private void getFullNameFromDatabase(String userId, final FullNameCallback callback) {
//        if (userId != null) {
//            DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference().child("users").child(userId);
//            usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
//                @Override
//                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                    if (dataSnapshot.exists()) {
//                        // Retrieve the full name from the dataSnapshot
//                        String fullName = dataSnapshot.child("fullName").getValue(String.class);
//
//                        // Pass the retrieved full name to the callback method
//                        callback.onFullNameReceived(fullName);
//                    } else {
//                        // Handle the case where the user data does not exist
//                        callback.onFullNameReceived(null);
//                    }
//                }
//
//                @Override
//                public void onCancelled(@NonNull DatabaseError databaseError) {
//                    // Handle potential errors
//                    callback.onFullNameReceived(null);
//                }
//            });
//        } else {
//            // Handle the case where userId is null
//            callback.onFullNameReceived(null);
//        }
//    }
//    // Define a callback interface to receive the full name asynchronously
//    private interface FullNameCallback {
//        void onFullNameReceived(String fullName);
//    }
//    @Override
//    public void onBackPressed() {
//        if (doubleBackToExitPressedOnce) {
//            super.onBackPressed();
//            return;
//        }
//
//        this.doubleBackToExitPressedOnce = true;
//        Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
//
//        new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
//    }
//    @Override
//    protected void onResume() {
//        super.onResume();
//        updateChatItems();
//    }
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//
//        if (requestCode == YOUR_REQUEST_CODE && resultCode == RESULT_OK) {
//            // Retrieve data from the intent
//            String selectedFriendName = data.getStringExtra(EXTRA_SELECTED_FRIEND_NAME);
//            String lastMessage = data.getStringExtra(EXTRA_LAST_MESSAGE);
//            long timestamp = data.getLongExtra(EXTRA_TIMESTAMP, 0); // Default value 0 if EXTRA_TIMESTAMP is not found
//
//            // Now you can use this data as needed, for example, updating the UI
//            updateUI(selectedFriendName, lastMessage, timestamp);
//        }
//    }
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == REQUEST_CONTACTS_PERMISSION) {
//            // Check if the permission is granted
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                // Permission is granted, proceed with accessing contacts
//                loadContactsList();
//            } else {
//                // Permission is denied, handle the case accordingly (e.g., show a message or close the app)
//                finish(); // Close the activity or show a message
//            }
//        }
//    }
//
//}


//    private void updateChatItems() {
//        DatabaseReference messagesRef = FirebaseDatabase.getInstance().getReference().child("messages");
//
//        messagesRef.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                // Clear existing chat items
//                chatItems.clear();
//
//                // Process all messages
//                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
//                    processMessages(chatSnapshot);
//                }
//
//                populateLastMessages();
//
//                // Update the RecyclerView adapter
//                updateRecyclerViewAdapter();
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError databaseError) {
//                // Handle cancellation of database operations if needed
//            }
//        });
//    }
//        private void processMessages(DataSnapshot dataSnapshot) {
//        for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
//            senderId = messageSnapshot.child("sender_id").getValue(String.class);
//            receiverId = messageSnapshot.child("receiver_id").getValue(String.class);
//            Log.d("Message Processing", "Sender ID: " + senderId + ", Receiver ID: " + receiverId);
//            receiverName = messageSnapshot.child("receiverName").getValue(String.class);
//            ChatMessage message = createChatMessageFromSnapshot(messageSnapshot);
//
//            // Determine whether the message was sent by the current user or received by the current user
//            if (senderId.equals(userId)) {
//                // Process sender's messages
//                addOrUpdateChatItem(receiverId, receiverName, message);
//            } else if (receiverId.equals(userId)) {
//                // Process receiver's messages
//                addOrUpdateChatItem(senderId, receiverName, message);
//            }
//            else {
//                // Handle the case where senderId or receiverId is null
//                Log.e("Process Messages", "senderId or receiverId is null");
//            }
//        }
//    }
//    private void addOrUpdateChatItem(String contactId, String contactName, ChatMessage message) {
//        // Find or create chat item for the contact
//        ChatItem chatItem = findChatItemForContact(contactId);
//        if (chatItem == null) {
//            // Create a new chat item if it doesn't exist
//            chatItem = new ChatItem();
//            chatItem.setContactId(contactId);
//            chatItem.setContactName(contactName);
//            chatItems.add(chatItem);
//        }
//
//        // Update the chat item with the latest message
//        chatItem.addChatMessage(message);
//    }
//    private ChatItem findChatItemForContact(String contactId) {
//        for (ChatItem chatItem : chatItems) {
//            if (chatItem.getContactId().equals(contactId)) {
//                return chatItem;
//            }
//        }
//        return null;
//    }
//    private void updateRecyclerViewAdapter() {
//        // Sort the chatItems based on the timestamp of the latest message in each ChatItem
//        Collections.sort(chatItems, (item1, item2) -> {
//            ChatMessage latestMessage1 = item1.getLatestMessage();
//            ChatMessage latestMessage2 = item2.getLatestMessage();
//            if (latestMessage1 != null && latestMessage2 != null) {
//                return Long.compare(latestMessage2.getTimestampMillis(), latestMessage1.getTimestampMillis());
//            } else if (latestMessage1 != null) {
//                return -1; // item1 has a message, so it should come first
//            } else if (latestMessage2 != null) {
//                return 1; // item2 has a message, so it should come first
//            } else {
//                return 0; // Both items have no messages or null messages, no preference for order
//            }
//        });
//
//        // Update the RecyclerView adapter
//        runOnUiThread(() -> {
//            RecyclerView.LayoutManager layoutManager = chatRecyclerView.getLayoutManager();
//            if (layoutManager != null) {
//                for (int i = 0; i < chatItems.size(); i++) {
//                    ChatItem chatItem = chatItems.get(i);
//                    View itemView = layoutManager.findViewByPosition(i);
//                    if (itemView != null) {
//                        TextView lastMessageTextView = itemView.findViewById(R.id.lastMessageTextView);
//                        if (lastMessageTextView != null) {
//                            ChatMessage latestMessage = chatItem.getLatestMessage();
//                            if (latestMessage != null) {
//                                String lastMessage = latestMessage.getMessageText();
//                                lastMessageTextView.setText(lastMessage);
//                            }
//                        }
//                    }
//                }
//            }
//
//            ChatItemAdapter adapter = new ChatItemAdapter(chatItems, position -> {
//                // Handle item click here
//                ChatItem clickedItem = chatItems.get(position);
//                String receiverId = getCurrentReceiverId(clickedItem);
//                startChatWithContact(clickedItem.getContactId(), clickedItem.getContactName(), receiverId);
//            }, com.example.cryptochat.HomeActivity.this);
//            chatRecyclerView.setAdapter(adapter);
//        });
//    }
//    private void addOrUpdateChatItemForUser(String contactId, String contactName, String userId, ChatMessage message) {
//        // Find or create chat item for the user
//        ChatItem chatItem = findChatItemForUser(userId, contactId);
//        if (chatItem == null) {
//            chatItem = new ChatItem();
//            chatItem.setContactId(contactId);
//            chatItem.setContactName(contactName);
//            chatItems.add(chatItem);
//        }
//
//        // Update the latest message for the user
//        chatItem.addChatMessage(message);
//    }
//    private List<ChatItem> getUniqueChatItems(List<ChatItem> chatItems) {
//        Map<String, ChatItem> uniqueItemsMap = new HashMap<>();
//        for (ChatItem item : chatItems) {
//            if (!uniqueItemsMap.containsKey(item.getContactId())) {
//                uniqueItemsMap.put(item.getContactId(), item);
//            }
//        }
//        return new ArrayList<>(uniqueItemsMap.values());
//    }
//    private ChatMessage createChatMessageFromSnapshot(DataSnapshot snapshot) {
//        String senderId = snapshot.child("sender_id").getValue(String.class);
//        String receiverId = snapshot.child("receiver_id").getValue(String.class); // Correctly retrieve receiverId
//        String messageText = snapshot.child("messageText").getValue(String.class);
//        String receiverName = snapshot.child("receiverName").getValue(String.class);
//        String encryptedMessageText = snapshot.child("messageText").getValue(String.class);
//        String timestampString = snapshot.child("timestamp").getValue(String.class);
//        long timestamp = 0;
//
//        if (timestampString != null && !timestampString.isEmpty()) {
//            try {
//                SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yy HH:mm:ss", Locale.getDefault());
//                Date date = dateFormat.parse(snapshot.child("date").getValue(String.class) + " " + timestampString);
//                timestamp = date.getTime();
//            } catch (ParseException e) {
//                e.printStackTrace();
//            }
//        }
//
//        boolean sent = snapshot.child("sent").getValue(Boolean.class);
//
//        ChatMessage message = new ChatMessage(senderId, receiverId, messageText, timestamp, sent, receiverName); // Pass receiverId to ChatMessage constructor
//        message.setEncryptedMessageText(encryptedMessageText);
//        message.setTimestampMillis(timestamp);
//
//        return message;
//    }
// Assuming you retrieve receiverId from the selected contact's data