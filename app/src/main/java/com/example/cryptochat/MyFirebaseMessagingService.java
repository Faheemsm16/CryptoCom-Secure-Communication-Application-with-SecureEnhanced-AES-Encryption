package com.example.cryptochat;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.graphics.Color;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private AES aes;
    private DatabaseReference mDatabaseReference;
    private ChatActivity chatActivity;
    private final Lock displayNotificationLock = new ReentrantLock();

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        if (remoteMessage.getData().size() > 0) {
            // Handle data payload
            // You can access message data using remoteMessage.getData()
            String encryptedMessage = remoteMessage.getData().get("encryptedMessage");

            // Decrypt the message
            String decryptedMessage = aes.Decrypt(encryptedMessage, MyFirebaseMessagingService.this);

            // Display a notification with the decrypted message
            sendNotification(decryptedMessage);
        }
    }

    private void sendNotification(final String decryptedMessage) {
        displayNotificationLock.lock();
        try {
            // Create a notification channel (for Android Oreo and above)
            createNotificationChannel();

            // Build the notification
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, "channel_id")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("New Message")
                    .setContentText(decryptedMessage) // Use the decrypted message in the notification
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.notify(0, notificationBuilder.build());
        } catch (SecurityException e) {
            e.printStackTrace();
            // Handle the SecurityException here
        } finally {
            displayNotificationLock.unlock();
        }
    }

    public void setDatabaseReference(DatabaseReference databaseReference) {
        mDatabaseReference = databaseReference;
    }

    public void setChatActivity(ChatActivity chatActivity) {
        this.chatActivity = chatActivity;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Channel Name";
            String description = "Channel Description";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("channel_id", name, importance);
            channel.setDescription(description);
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private String getLatestMessage() {
        final String[] latestMessage = {""}; // Using an array to effectively make the variable final

        // Fetch the latest message from the database
        mDatabaseReference.limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@org.checkerframework.checker.nullness.qual.NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Message message = snapshot.getValue(Message.class);
                    if (message != null) {
                        // Decrypt the latest message
                        String decryptedMessage = aes.Decrypt(message.getMessageText(), MyFirebaseMessagingService.this);
                        latestMessage[0] = decryptedMessage;
                    } else {
                        // Handle the case where the message is null
                        // Log.e("Message", "Received a null message");
                    }
                }
            }

            @Override
            public void onCancelled(@org.checkerframework.checker.nullness.qual.NonNull DatabaseError error) {
                // Handle onCancelled if needed
            }
        });

        // Wait for the asynchronous fetch to complete
        // Use a more Android-friendly way of waiting, such as Handler or AsyncTask
        // Sleeping on the main thread is not recommended
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return latestMessage[0];
    }
}
