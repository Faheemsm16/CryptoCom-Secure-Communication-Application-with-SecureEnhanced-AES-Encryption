package com.example.cryptochat;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.concurrent.TimeUnit;

public class LoginActivity extends AppCompatActivity {

    private Button loginWithOTPButton, createAccountButton;
    private FirebaseAuth mAuth;
    private ProgressBar loadingIndicator;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "MyPrefs";
    private static final String PHONE_NUMBER_KEY = "phoneNumber";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        loginWithOTPButton = findViewById(R.id.loginWithOTPButton);
        createAccountButton = findViewById(R.id.createAccountButton);
        // Initialize loading indicator
        loadingIndicator = findViewById(R.id.loadingIndicator);

        // Check if the user is already logged in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // User is already logged in, go to HomeActivity
            startActivity(new Intent(LoginActivity.this, HomeActivity.class));
            finish(); // Close the LoginActivity
        }

        loginWithOTPButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkUserAndInitiateOTP();
            }
        });

        createAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, RegistrationActivity.class));
            }
        });
    }

    private void checkUserAndInitiateOTP() {
        String storedPhoneNumber = sharedPreferences.getString(PHONE_NUMBER_KEY, "");

        if (!TextUtils.isEmpty(storedPhoneNumber)) {
            // Show a dialog to confirm the stored phone number
            showConfirmationDialog(storedPhoneNumber);
        } else {
            // Prompt the user to enter a phone number
            showPhoneNumberDialog();
        }
    }

    private void showPhoneNumberDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Your Phone Number");

        // Set up the layout for the dialog
        View viewInflated = LayoutInflater.from(this).inflate(R.layout.phone_number_entry_dialog, null);
        final EditText inputPhoneNumber = viewInflated.findViewById(R.id.inputPhoneNumber);
        builder.setView(viewInflated);

        // Set up the buttons
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String enteredPhoneNumber = inputPhoneNumber.getText().toString().trim();
                if (!TextUtils.isEmpty(enteredPhoneNumber)) {
                    // Save the entered phone number in SharedPreferences
                    savePhoneNumberInSharedPreferences(enteredPhoneNumber);
                    // Initiate OTP verification with the entered phone number
                    initiateOTPVerification(enteredPhoneNumber);
                } else {
                    Toast.makeText(LoginActivity.this, "Please enter your phone number", Toast.LENGTH_SHORT).show();
                    showPhoneNumberDialog(); // Re-show the dialog if the phone number is not entered
                }
            }
        });

        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        // Show the dialog
        builder.show();
    }

    private void showConfirmationDialog(String storedPhoneNumber) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm Phone Number");

        // Set up the layout for the dialog
        View viewInflated = LayoutInflater.from(this).inflate(R.layout.confirmation_dialog, null);
        final TextView textViewPhoneNumber = viewInflated.findViewById(R.id.textViewPhoneNumber);
        textViewPhoneNumber.setText(storedPhoneNumber); // Set the text to the stored number
        builder.setView(viewInflated);

        // Set up the buttons
        builder.setPositiveButton("Continue", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Initiate OTP verification with the stored phone number
                initiateOTPVerification(storedPhoneNumber);
            }
        });

        builder.setNegativeButton("Change", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // User wants to change the phone number, prompt to enter a new one
                showPhoneNumberDialog();
            }
        });

        // Show the dialog
        builder.show();
    }
    private void savePhoneNumberInSharedPreferences(String phoneNumber) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PHONE_NUMBER_KEY, phoneNumber);
        editor.apply();
    }

    private void initiateOTPVerification(String phoneNumber) {
        // Display loading indicator
        showLoadingIndicator();
        // Save the entered phone number in SharedPreferences
        savePhoneNumberInSharedPreferences(phoneNumber);
        // Build PhoneAuthOptions with the entered phone number
        PhoneAuthOptions options = buildPhoneAuthOptions(phoneNumber);
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void showLoadingIndicator() {
        loadingIndicator.setVisibility(View.VISIBLE);
    }

    private void hideLoadingIndicator() {
        loadingIndicator.setVisibility(View.INVISIBLE);
    }

    private PhoneAuthOptions buildPhoneAuthOptions(String phoneNumber) {
        return PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(getVerificationCallbacks())
                .build();
    }

    private boolean isUserNotRegisteredError(String errorMessage) {
        // You need to implement logic to check if the error message indicates that the user is not registered
        // This can vary depending on your authentication setup or error messages from Firebase
        // For demonstration purposes, let's assume a hypothetical error message
        return errorMessage.contains("User not registered");
    }

    private void redirectToRegistration() {
        Toast.makeText(this, "You are not registered. Please create an account.", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(LoginActivity.this, RegistrationActivity.class));
    }

    private PhoneAuthProvider.OnVerificationStateChangedCallbacks getVerificationCallbacks() {
        return new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                signInWithPhoneAuthCredential(credential);
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                hideLoadingIndicator();
                Toast.makeText(LoginActivity.this, "Verification failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCodeSent(@NonNull String verificationId, PhoneAuthProvider.@NonNull ForceResendingToken token) {
                hideLoadingIndicator();
                navigateToOTPVerification(verificationId);
            }
        };
    }

    private void navigateToOTPVerification(String verificationId) {
        Intent intent = new Intent(LoginActivity.this, OTPVerificationActivity.class);
        intent.putExtra("verificationId", verificationId);
        startActivity(intent);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = task.getResult().getUser();
                        // Save user data or perform any other actions after successful login
                        Toast.makeText(LoginActivity.this, "Login successful", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                        finish(); // Close the LoginActivity
                    } else {
                        Toast.makeText(LoginActivity.this, "Authentication failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();

                        // Check if the failure is due to the user not being registered
                        if (isUserNotRegisteredError(task.getException().getMessage())) {
                            // Redirect to registration activity
                            redirectToRegistration();
                        }
                    }
                });
    }

    private void setUserOnlineStatus() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference().child("lastSeen").child(userId);
            usersRef.child("isOnline").setValue("Online")
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d("OtherActivity", "User status updated as online");
                        } else {
                            Log.e("OtherActivity", "Failed to update user status: " + task.getException().getMessage());
                        }
                    });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Set user's status as online
        setUserOnlineStatus();
    }
}