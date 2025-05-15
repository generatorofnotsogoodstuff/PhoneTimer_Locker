package com.boredharisk.phonetimerlocker; // Make sure this matches your package name

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class TimerLockService extends Service {

    public static final String EXTRA_TIME_MILLIS = "extra_time_millis";
    // public static final String EXTRA_PACKAGE_NAME = "extra_package_name"; // If needed

    private static final String CHANNEL_ID = "TimerLockServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private CountDownTimer countDownTimer;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName deviceAdminComponent;
    private long timeLeftInMillis;

    @Override
    public void onCreate() {
        super.onCreate();
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdminComponent = new ComponentName(this, MyDeviceAdminReceiver.class);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf(); // Should not happen if started correctly
            return START_NOT_STICKY;
        }

        long timeMillis = intent.getLongExtra(EXTRA_TIME_MILLIS, 0);
        // String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME); // If you pass it

        if (timeMillis <= 0) {
            Toast.makeText(this, "Invalid time for timer.", Toast.LENGTH_SHORT).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        timeLeftInMillis = timeMillis;
        startForeground(NOTIFICATION_ID, createNotification("Timer starting..."));

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) { // Update every second
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateNotification(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                lockDevice();
                stopSelf();
            }
        }.start();

        Toast.makeText(this, "Timer service started.", Toast.LENGTH_SHORT).show();
        return START_STICKY;
    }

    private void updateNotification(long millisUntilFinished) {
        long seconds = (millisUntilFinished / 1000) % 60;
        long minutes = (millisUntilFinished / (1000 * 60)) % 60;
        long hours = (millisUntilFinished / (1000 * 60 * 60)) % 24;

        String timeText;
        if (hours > 0) {
            timeText = String.format("%02d:%02d:%02d left until lock", hours, minutes, seconds);
        } else {
            timeText = String.format("%02d:%02d left until lock", minutes, seconds);
        }

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, createNotification(timeText));
    }


    private Notification createNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class); // Tap notification to open app
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Action to stop the timer
        Intent stopSelfIntent = new Intent(this, TimerLockService.class);
        stopSelfIntent.setAction("STOP_ACTION"); // Custom action
        PendingIntent pStopSelf = PendingIntent.getService(this, 0,
                stopSelfIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);


        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("App Timer Active")
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher) // Replace with your app icon
                .setContentIntent(pendingIntent)
                .setOngoing(true) // Makes it non-dismissable by swipe
                .addAction(R.drawable.ic_stop_timer, "Stop Timer", pStopSelf); // Example icon

        return builder.build();
    }

    private void lockDevice() {
        if (devicePolicyManager.isAdminActive(deviceAdminComponent)) {
            devicePolicyManager.lockNow();
        } else {
            Toast.makeText(this, "Cannot lock: Device Admin not active.", Toast.LENGTH_LONG).show();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Timer Lock Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT // maybe LOW to be less intrusive?
            );
            serviceChannel.setDescription("Channel for App Timer Lock Service");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        Toast.makeText(this, "Timer service stopped.", Toast.LENGTH_SHORT).show();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}