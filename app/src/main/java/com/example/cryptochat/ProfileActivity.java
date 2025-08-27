package com.example.cryptochat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    private TextView usernameTextView;
    private TextView phoneNumberTextView;
//    private Button changePasswordButton;
    private Button logoutButton;
//    private Button deleteAccountButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initializeUI();
        firebaseinitialization();
        setListeners();
    }

    private void initializeUI() {
        // Initialize UI elements
        usernameTextView = findViewById(R.id.usernameTextView);
        phoneNumberTextView = findViewById(R.id.phoneNumberTextView);
//        changePasswordButton = findViewById(R.id.changePasswordButton);
        logoutButton = findViewById(R.id.logoutButton);
//        deleteAccountButton = findViewById(R.id.deleteAccountButton);
    }

    private void firebaseinitialization() {
        // Get the current user from Firebase Authentication
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("users");

        if (user != null) {
            // Retrieve the user's phone number from SharedPreferences
            SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
            String phoneNumber = sharedPreferences.getString("phoneNumber", "");

            // Display phone number
            if (!TextUtils.isEmpty(phoneNumber)) {
                phoneNumberTextView.setText("Phone Number: " + phoneNumber);
                phoneNumberTextView.setVisibility(View.VISIBLE);
            } else {
                phoneNumberTextView.setVisibility(View.GONE);
            }

            // Retrieve and display username from Firebase Realtime Database
            databaseReference.child(phoneNumber).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        String username = dataSnapshot.child("fullName").getValue(String.class);
                        if (!TextUtils.isEmpty(username)) {
                            usernameTextView.setText("Name: " + username);
                            usernameTextView.setVisibility(View.VISIBLE);
                        } else {
                            usernameTextView.setVisibility(View.GONE);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Handle the error
                }
            });
        }
    }

    private void setListeners() {
//        changePasswordButton.setOnClickListener(v -> initiatePasswordReset());
        logoutButton.setOnClickListener(v -> signOutUser());
//        deleteAccountButton.setOnClickListener(v -> showDeleteAccountDialog());
    }

    // Helper method to display email-based profile info
//    private void displayEmailProfile(FirebaseUser users) {
//        String username = getIntent().getStringExtra("username");
//
//        // Show name if available, otherwise hide it
//        if (!TextUtils.isEmpty(username)) {
//            usernameTextView.setText("Name: " + username);
//            usernameTextView.setVisibility(View.VISIBLE);
//        } else {
//            usernameTextView.setVisibility(View.GONE);
//        }
//    }

    // Helper method to initiate password reset
//    private void initiatePasswordReset() {
//        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
//
//        if (user != null) {
//            String email = user.getEmail();
//
//            if (!TextUtils.isEmpty(email)) {
//                FirebaseAuth.getInstance().sendPasswordResetEmail(email)
//                        .addOnCompleteListener(task -> {
//                            if (task.isSuccessful()) {
//                                showSuccessMessage("Password reset email sent. Check your email to change the password.");
//                            } else {
//                                showErrorMessage("Failed to send password reset email: " + task.getException().getMessage());
//                                Log.e("PasswordReset", "Error: " + task.getException().getMessage(), task.getException());
//                            }
//                        });
//            } else {
//                showErrorMessage("User email is null or empty. Unable to initiate password reset.");
//            }
//        } else {
//            showErrorMessage("Please sign in to change your password");
//        }
//    }

    // Helper method to sign out the user
    private void signOutUser() {
        FirebaseAuth.getInstance().signOut();

        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    // Helper method to show a confirmation dialog for account deletion
//    private void showDeleteAccountDialog() {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle("Delete Account");
//        builder.setMessage("Are you sure you want to delete your account? This action cannot be undone.");
//
//        builder.setPositiveButton("Delete", (dialog, which) -> deleteAccount());
//
//        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
//
//        AlertDialog dialog = builder.create();
//        dialog.show();
//    }

    // Helper method to delete the account
//    private void deleteAccount() {
//        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
//
//        if (user != null) {
//            // Directly delete the user account
//            user.delete()
//                    .addOnCompleteListener(deleteTask -> {
//                        if (deleteTask.isSuccessful()) {
//                            Toast.makeText(ProfileActivity.this, "Account deleted successfully", Toast.LENGTH_SHORT).show();
//                            FirebaseAuth.getInstance().signOut();
//                            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
//                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//                            startActivity(intent);
//                        } else {
//                            Toast.makeText(ProfileActivity.this, "Account deletion failed", Toast.LENGTH_SHORT).show();
//                        }
//                    })
//                    .addOnFailureListener(e -> {
//                        // Check the log for details about the failure
//                        Log.e("DeleteAccount", "Account deletion failed", e);
//                        Toast.makeText(ProfileActivity.this, "Account deletion failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//                    });
//        }
//    }


//    // Helper method to show a success message to the user
//    private void showSuccessMessage(String message) {
//        Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_LONG).show();
//    }
//
//    // Helper method to show an error message to the user
//    private void showErrorMessage(String message) {
//        Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show();
//    }

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