package com.example.cryptochat;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.concurrent.TimeUnit;

public class RegistrationActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "MyPrefs";
    private static final String PHONE_NUMBER_KEY = "phoneNumber";
    private static final String EMAIL_KEY = "email";
    private static final String FULL_NAME_KEY = "fullName";
    private TextInputLayout fullNameLayout, emailLayout, phoneNumberLayout;
    private EditText fullNameEditText, emailEditText, phoneNumberEditText;
    private Button registerButton;
    private TextView resendOtpButton;  // New button for resend OTP
    private TextView verificationNoteTextView;

    private FirebaseAuth mAuth;
    private ProgressBar loadingIndicator;
    private DatabaseReference databaseReference;
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks;
    private static final long COUNTDOWN_INTERVAL = 1000;
    private static final long DISABLE_BUTTON_DURATION = 45;
    private boolean isResendButtonClickable = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        mAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("users");

        initializeViews();
        initializeCallbacks();
    }

    private void initializeViews() {
        fullNameLayout = findViewById(R.id.fullNameTextInputLayout);
        emailLayout = findViewById(R.id.emailTextInputLayout);
        phoneNumberLayout = findViewById(R.id.phoneNumberTextInputLayout);
        fullNameEditText = findViewById(R.id.fullNameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText);
        registerButton = findViewById(R.id.registerButton);
        resendOtpButton = findViewById(R.id.resendCodeTextView);
        verificationNoteTextView = findViewById(R.id.verificationNoteTextView);

        // Initialize loading indicator
        loadingIndicator = findViewById(R.id.loadingIndicator);

        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerUser();
            }
        });}

    private void initializeCallbacks() {
        callbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                // This callback will be invoked in two situations:
                // 1. Instant verification. In some cases, the phone number can be instantly verified without needing to send or enter a verification code.
                // 2. Auto-retrieval. On some devices, Google Play services can automatically detect the incoming verification SMS and perform verification without user action.

                // Sign in with the auto-retrieved credential
                signInWithPhoneAuthCredential(credential);

                // Hide loading indicator
                hideLoadingIndicator();
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                // This callback is invoked for invalid requests, such as an invalid phone number format.
                Toast.makeText(RegistrationActivity.this, "Verification failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                // Hide loading indicator
                hideLoadingIndicator();
            }

            @Override
            public void onCodeSent(@NonNull String verificationId, PhoneAuthProvider.@NonNull ForceResendingToken token) {
                String phoneNumber = phoneNumberEditText.getText().toString().trim();
                // Show OTP entry dialog
                showOtpEntryDialog(phoneNumber, verificationId, token);
            }
        };
    }

    private void registerUser() {
        String fullName = fullNameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String phoneNumber = phoneNumberEditText.getText().toString().trim();

        // Reset error states for all fields
        resetErrorStates();

        if (TextUtils.isEmpty(fullName)) {
            showError(fullNameLayout, "Full name must be filled");
            return;
        }

        if (TextUtils.isEmpty(email)) {
            showError(emailLayout, "Email must be filled");
            return;
        } else if (!isValidEmail(email)) {
            showError(emailLayout, "Invalid email format");
            return;
        }

        if (TextUtils.isEmpty(phoneNumber)) {
            showError(phoneNumberLayout, "Phone number must be filled");
            return;
        } else if (!isValidPhoneNumber(phoneNumber)) {
            showError(phoneNumberLayout, "Enter with Country Code (+91)");
            return;
        }

        // Check if the phone number already exists
        checkPhoneNumberExistence(phoneNumber, fullName, email);
    }


    private void resendOTP(String phoneNumber) {
        showLoadingIndicator();
        PhoneAuthOptions options = buildPhoneAuthOptions(phoneNumber);
        PhoneAuthProvider.verifyPhoneNumber(options);

        Toast.makeText(RegistrationActivity.this, "Resending OTP...", Toast.LENGTH_SHORT).show();
        startCountdownTimer();
        disableResendButtonForDuration(DISABLE_BUTTON_DURATION * COUNTDOWN_INTERVAL);
    }

    private void startCountdownTimer() {
        new CountDownTimer(DISABLE_BUTTON_DURATION * COUNTDOWN_INTERVAL, COUNTDOWN_INTERVAL) {
            public void onTick(long millisUntilFinished) {
                long secondsRemaining = millisUntilFinished / COUNTDOWN_INTERVAL;
                String timerText = "Resend Code in " + secondsRemaining + " seconds";
                resendOtpButton.setText(timerText);
            }

            public void onFinish() {
                resendOtpButton.setText("Resend Code");
                isResendButtonClickable = true;  // Enable the button
            }
        }.start();
    }

    private void disableResendButtonForDuration(long duration) {
        isResendButtonClickable = false;  // Disable the button during the countdown
        new CountDownTimer(duration, COUNTDOWN_INTERVAL) {
            public void onTick(long millisUntilFinished) {
                // No action needed during the countdown
            }

            public void onFinish() {
                isResendButtonClickable = true;  // Enable the button after the countdown
            }
        }.start();
    }

    private PhoneAuthOptions buildPhoneAuthOptions(String phoneNumber) {
        return PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .build();
    }

    private void checkPhoneNumberExistence(String phoneNumber, String fullName, String email) {
        // Query the database to check if the phone number already exists
        databaseReference.child(phoneNumber).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Phone number already exists, show error message
                    showError(phoneNumberLayout, "An account already exists with this phone number");
                    // Hide loading indicator
                    hideLoadingIndicator();
                } else {
                    // Phone number doesn't exist, proceed with phone verification
                    // Show loading indicator
                    showLoadingIndicator();
                    initiatePhoneVerification(phoneNumber);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle any errors in database query
                Toast.makeText(RegistrationActivity.this, "Error checking phone number existence", Toast.LENGTH_SHORT).show();
                // Hide loading indicator
                hideLoadingIndicator();
            }
        });
    }

    private void showError(TextInputLayout layout, String message) {
        // Display error messages within TextInputLayouts
        layout.setError(message);
        layout.setErrorTextColor(ColorStateList.valueOf(getResources().getColor(R.color.errorColor))); // Change error text color
    }

    private void resetErrorStates() {
        fullNameLayout.setError(null);
        emailLayout.setError(null);
        phoneNumberLayout.setError(null);

        // Reset error text color to the default
        fullNameLayout.setErrorTextColor(ColorStateList.valueOf(getResources().getColor(R.color.defaultErrorColor)));
        emailLayout.setErrorTextColor(ColorStateList.valueOf(getResources().getColor(R.color.defaultErrorColor)));
        phoneNumberLayout.setErrorTextColor(ColorStateList.valueOf(getResources().getColor(R.color.defaultErrorColor)));
    }


    private void showLoadingIndicator() {
        loadingIndicator.setVisibility(View.VISIBLE);
    }

    private void hideLoadingIndicator() {
        loadingIndicator.setVisibility(View.INVISIBLE);
    }

    private void initiatePhoneVerification(String phoneNumber) {
        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(mAuth)
                        .setPhoneNumber(phoneNumber)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(callbacks)
                        .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }


    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = task.getResult().getUser();
                        saveUserDataInSharedPreferences(phoneNumberEditText.getText().toString(), emailEditText.getText().toString(), fullNameEditText.getText().toString());
                        saveUserDataInDatabase(user.getUid(), fullNameEditText.getText().toString(), emailEditText.getText().toString(), phoneNumberEditText.getText().toString());
                        startNextActivity(LoginActivity.class);
                    } else {
                        showToast("Authentication failed: " + task.getException().getMessage());
                    }
                });
    }

    private void saveUserDataInSharedPreferences(String phoneNumber, String email, String fullName) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PHONE_NUMBER_KEY, phoneNumber);
        editor.putString(EMAIL_KEY, email);
        editor.putString(FULL_NAME_KEY, fullName);
        editor.apply();
    }

    private void saveUserDataInDatabase(String userId, String fullName, String email, String phoneNumber) {
        databaseReference.child(phoneNumber).child("userId").setValue(userId);
        databaseReference.child(phoneNumber).child("fullName").setValue(fullName);
        databaseReference.child(phoneNumber).child("email").setValue(email);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void startNextActivity(Class<?> activityClass) {
        startActivity(new Intent(RegistrationActivity.this, activityClass));
    }

    private boolean isValidEmail(String email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber.matches("^\\+[0-9]+$");
    }

    private void showOtpEntryDialog(String phoneNumber, String verificationId, PhoneAuthProvider.ForceResendingToken token) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Verification Code");

        // Set up the layout for the dialog
        View viewInflated = LayoutInflater.from(this).inflate(R.layout.otp_entry_dialog, null);
        final EditText inputOtp = viewInflated.findViewById(R.id.inputOtp);
        Button verifyButton = viewInflated.findViewById(R.id.verifyButton);

        // Add OnClickListener for the "Verify" button
        verifyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Handle the verification logic here
                String otp = inputOtp.getText().toString().trim();
                if (!TextUtils.isEmpty(otp)) {
                    // Call a method to verify the entered OTP
                    verifyOtp(verificationId, otp);
                } else {
                    Toast.makeText(RegistrationActivity.this, "Please enter the verification code", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Set up the buttons
        // Here, we don't set any buttons

        // Show the dialog
        builder.setView(viewInflated);
        builder.setCancelable(false);

        // Set the dismiss listener to handle cases where the dialog is dismissed
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                // Handle dismissal, e.g., stop listening for SMS
                // You may want to remove the callbacks
                callbacks = null; // Set callbacks to null to stop listening
            }
        });

        builder.show();
    }
    private void verifyOtp(String verificationId, String otp) {
        // Use the verificationId and OTP to create PhoneAuthCredential
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);

        // Sign in with the entered credential
        signInWithPhoneAuthCredential(credential);
    }

}