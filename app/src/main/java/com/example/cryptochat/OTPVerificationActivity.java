package com.example.cryptochat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.concurrent.TimeUnit;

public class OTPVerificationActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "MyPrefs";
    private static final String PHONE_NUMBER_KEY = "phoneNumber";
    private static final long COUNTDOWN_INTERVAL = 1000;
    private static final long DEFAULT_TIMEOUT = 60L;
    private static final long DISABLE_BUTTON_DURATION = 45;
    private static final long INITIAL_TIME_MILLIS = 45000;

    private EditText inputOtp;
    private Button verifyOtpButton;
    private TextView resendOtpTextView;
    private FirebaseAuth mAuth;
    private String verificationId;
    private ProgressBar loadingIndicator;
    private TextView timerTextView;
    private CountDownTimer countDownTimer;
    private long timeLeftInMillis;
    private boolean isResendButtonClickable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        mAuth = FirebaseAuth.getInstance();

        inputOtp = findViewById(R.id.inputOtp);
        verifyOtpButton = findViewById(R.id.verifyOtpButton);
        resendOtpTextView = findViewById(R.id.resendOtpTextView);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        timerTextView = findViewById(R.id.timerTextView);

        // Set the resend button initially disabled
        disableResendButtonForDuration(INITIAL_TIME_MILLIS);

        // Retrieve verificationId from intent
        Intent intent = getIntent();
        if (intent != null) {
            verificationId = intent.getStringExtra("verificationId");
        }

        verifyOtpButton.setOnClickListener(v -> verifyOtp());

        resendOtpTextView.setOnClickListener(v -> {
            SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String phoneNumber = sharedPreferences.getString(PHONE_NUMBER_KEY, "");

            if (!TextUtils.isEmpty(phoneNumber) && isResendButtonClickable) {
                resendOTP(phoneNumber);
            } else {
                Toast.makeText(OTPVerificationActivity.this, "Please wait before retrying.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void verifyOtp() {
        String otp = inputOtp.getText().toString().trim();

        if (!TextUtils.isEmpty(otp)) {
            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
            signInWithPhoneAuthCredential(credential);
        } else {
            Toast.makeText(this, "Please enter the verification code", Toast.LENGTH_SHORT).show();
        }
    }

    private void resendOTP(String phoneNumber) {
        showLoadingIndicator();
        PhoneAuthOptions options = buildPhoneAuthOptions(phoneNumber);
        PhoneAuthProvider.verifyPhoneNumber(options);

        Toast.makeText(OTPVerificationActivity.this, "Resending OTP...", Toast.LENGTH_SHORT).show();
        startCountdownTimer();
        disableResendButtonForDuration(DISABLE_BUTTON_DURATION * COUNTDOWN_INTERVAL);
    }

    private void startCountdownTimer() {
        timeLeftInMillis = INITIAL_TIME_MILLIS;
        countDownTimer = new CountDownTimer(timeLeftInMillis, COUNTDOWN_INTERVAL) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateTimerText();
            }

            @Override
            public void onFinish() {
                updateTimerText();
            }
        }.start();
    }

    private void updateTimerText() {
        long seconds = timeLeftInMillis / 1000;
        timerTextView.setText(String.format("Resend in %ds", seconds));
        timerTextView.setVisibility(seconds == 0 ? View.INVISIBLE : View.VISIBLE);
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
                .setTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(getVerificationCallbacks())
                .build();
    }

    private PhoneAuthProvider.OnVerificationStateChangedCallbacks getVerificationCallbacks() {
        return new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                hideLoadingIndicator();
                signInWithPhoneAuthCredential(credential);
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                hideLoadingIndicator();
                Toast.makeText(OTPVerificationActivity.this, "Verification failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCodeSent(@NonNull String verificationId, PhoneAuthProvider.@NonNull ForceResendingToken token) {
                hideLoadingIndicator();
                disableResendButtonForDuration(DISABLE_BUTTON_DURATION * COUNTDOWN_INTERVAL);
            }

            @Override
            public void onCodeAutoRetrievalTimeOut(@NonNull String verificationId) {
                hideLoadingIndicator();
                Toast.makeText(OTPVerificationActivity.this, "OTP auto-retrieval timed out. Please retry.", Toast.LENGTH_SHORT).show();
            }
        };
    }

    private void disableResendButtonForDuration(long milliseconds) {
        isResendButtonClickable = false;

        new CountDownTimer(milliseconds, COUNTDOWN_INTERVAL) {
            public void onTick(long millisUntilFinished) {
                updateTimerText(millisUntilFinished / COUNTDOWN_INTERVAL);
            }

            public void onFinish() {
                isResendButtonClickable = true;
                timerTextView.setVisibility(View.INVISIBLE);
            }
        }.start();
    }

    private void updateTimerText(long secondsLeft) {
        timerTextView.setText(String.format("Resend in %ds", secondsLeft));
        timerTextView.setVisibility(View.VISIBLE);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(OTPVerificationActivity.this, "Verification successful", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(OTPVerificationActivity.this, HomeActivity.class));
                        finish();
                    } else {
                        Toast.makeText(OTPVerificationActivity.this, "Verification failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
