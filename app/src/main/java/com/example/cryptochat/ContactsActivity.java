package com.example.cryptochat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ContactsActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_READ_CONTACTS = 100;
    private boolean permissionRequested = false;
    private EditText searchEditText;
    private RecyclerView contactsRecyclerView;
    private ContactsAdapter contactsAdapter;
    private List<Contact> contactList = new ArrayList<>();
    private DatabaseReference contactsReference;
    private static final String PREFS_NAME = "MyPrefs";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);
        // Initialize Firebase Crashlytics
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);

        // Initialize UI elements
        searchEditText = findViewById(R.id.searchEditText);
        contactsRecyclerView = findViewById(R.id.contactsRecyclerView);
        Button addContactButton = findViewById(R.id.addContactButton);

        // Initialize Firebase
        FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
        // Create a DatabaseReference for the "contacts" node
        contactsReference = firebaseDatabase.getReference().child("contacts");

        // Check if permission to read contacts is granted
        if (!permissionRequested || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission not granted or not requested before, request it
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    PERMISSIONS_REQUEST_READ_CONTACTS);
            // Set the flag to true to indicate that permission has been requested
            permissionRequested = true;
        } else {
            // Permission already granted, fetch and display contacts
            fetchAndDisplayContacts();
        }

        // Set a click listener for the "Add Contact" button
        addContactButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start the contact addition process
                Intent addContactIntent = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
                startActivity(addContactIntent);
            }
        });
    }

    private void fetchAndDisplayContacts() {
        // Create a list of contacts by fetching them from the device's contacts
        contactList = getContacts();

        // Sort the contact list in ascending order
        Collections.sort(contactList, new Comparator<Contact>() {
            @Override
            public int compare(Contact contact1, Contact contact2) {
                String name1 = contact1.getName();
                String name2 = contact2.getName();

                if (name1 == null && name2 == null) {
                    return 0; // Both names are null, consider them equal
                } else if (name1 == null) {
                    return -1; // Name1 is null, consider it smaller
                } else if (name2 == null) {
                    return 1; // Name2 is null, consider it smaller
                } else {
                    return name1.compareToIgnoreCase(name2);
                }
            }
        });

        // Set up the RecyclerView with the ContactsAdapter
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        contactsRecyclerView.setLayoutManager(layoutManager);

        // Initialize the ContactsAdapter
        contactsAdapter = new ContactsAdapter(this, contactList, contactsReference);

        contactsAdapter.setOnItemClickListener(new ContactsAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                // Handle item click here
                Contact clickedContact = contactList.get(position);
                String selectedContactId = clickedContact.getId();
                String selectedContactName = clickedContact.getName();
//                String selectedContactPhoneNumber = clickedContact.getPhoneNumber();

//                String selectedContactNumber = clickedContact.getPhoneNumber();

                // Fetch the registered name associated with the selected contact's phone number
//                fetchRegisteredName(selectedContactPhoneNumber, selectedContactId);

                // Create an intent to pass data back to the calling activity
                Intent resultIntent = new Intent();
                resultIntent.putExtra("selectedContactId", selectedContactId);
                resultIntent.putExtra("selectedContactName", selectedContactName);
//                resultIntent.putExtra("selectedContactNumber", selectedContactNumber);


                // Set the result and finish the activity
                setResult(RESULT_OK, resultIntent);

                startChatWithContact(clickedContact);
            }
        });

        // Set the adapter for the RecyclerView
        contactsRecyclerView.setAdapter(contactsAdapter);

        // Add a TextWatcher to the searchEditText
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Filter the contact list based on the entered text
                List<Contact> filteredContacts = filterContacts(contactList, s.toString());
                // Update the RecyclerView based on the filtered contacts
                contactsAdapter.updateContactList(filteredContacts);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

//    private void fetchRegisteredName(String phoneNumber, String contactId) {
//        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
//        usersRef.orderByChild("phoneNumber").equalTo(phoneNumber).addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                if (snapshot.exists()) {
//                    // Get the first matching user (assuming phone numbers are unique)
//                    DataSnapshot userSnapshot = snapshot.getChildren().iterator().next();
//                    String registeredName = userSnapshot.child("fullName").getValue(String.class);
//
//                    // Pass the registered name along with other data to ChatActivity
//                    Intent chatIntent = new Intent(ContactsActivity.this, ChatActivity.class);
//                    chatIntent.putExtra(ChatActivity.FRIEND_ID, contactId);
//                    chatIntent.putExtra(ChatActivity.FRIEND_NAME, registeredName);
//
//                    // Pass current user's phone number to differentiate
//                    chatIntent.putExtra(ChatActivity.CURRENT_USER_PHONE_NUMBER, getCurrentUserPhoneNumber());
//
//                    startActivity(chatIntent);
//                } else {
//                    // Handle the case where the user hasn't registered with the provided phone number
//                    Toast.makeText(ContactsActivity.this, "User hasn't registered", Toast.LENGTH_SHORT).show();
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                // Handle onCancelled if needed
//                Log.e("FirebaseError", "Error fetching registered name: " + error.getMessage());
//            }
//        });
//    }

//    private String getCurrentUserPhoneNumber() {
//        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
//        return sharedPreferences.getString("phoneNumber", "");
//    }


    // Helper method to filter contacts by name
    private List<Contact> filterContacts(List<Contact> contacts, String searchText) {
        List<Contact> filteredContacts = new ArrayList<>();
        for (Contact contact : contacts) {
            String contactName = contact.getName();
            if (contactName != null && contactName.toLowerCase().contains(searchText.toLowerCase())) {
                filteredContacts.add(contact);
            }
        }
        return filteredContacts;
    }

    private void startChatWithContact(Contact selectedContact) {
        Intent chatIntent = new Intent(ContactsActivity.this, ChatActivity.class);
        chatIntent.putExtra(ChatActivity.FRIEND_ID, selectedContact.getId());
        chatIntent.putExtra(ChatActivity.FRIEND_NAME, selectedContact.getName());
        startActivity(chatIntent);
    }

    private List<Contact> getContacts() {
        List<Contact> contactList = new ArrayList<>();

        // Query the device's contacts using the ContactsContract API
        Cursor cursor = getContentResolver().query(
                android.provider.ContactsContract.Contacts.CONTENT_URI,
                null, null, null, null);

        if (cursor != null) {
            int displayNameIndex = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME);
            if (displayNameIndex != -1) {
                while (cursor.moveToNext()) {
                    String contactName = cursor.getString(displayNameIndex);
                    if (contactName == null || contactName.isEmpty()) {
                        // If the contact name is null or empty, use "Unknown Contact" as the default name
                        contactName = "Unknown Contact";
                    }
                    contactList.add(new Contact(contactName));
                }
            }
            cursor.close(); // Close the cursor in all cases
        }

        return contactList;
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_READ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, fetch and display contacts
                Log.d("ContactsActivity", "Permission to access contacts granted.");
                fetchAndDisplayContacts();
            } else {
                // Permission denied, display a message to the user
                Log.e("ContactsActivity", "Permission to access contacts denied.");
                Toast.makeText(this, "Permission to access contacts denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Set user's status as "Online" when in foreground
        updateUserStatus(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Set user's status with current date and time when in background
        updateUserStatus(false);
    }
}
