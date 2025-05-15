package com.boredharisk.phonetimerlocker; // Make sure this matches your package name

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private Spinner appSpinner;
    private it.beppi.knoblibrary.Knob timerKnob;
    private TextView timeDisplayTextViewCircular;
    private Button launchButton;

    private List<AppInfo> installedApps;
    private AppInfo selectedApp = null;
    private int selectedTimeMinutes = 30;

    private DevicePolicyManager devicePolicyManager;
    private ComponentName deviceAdminComponent;

    private static final int MAX_TIME_MINUTES = 60; // max minutes set
    private static final int MIN_TIME_MINUTES = 1; // Minimum time for the timer

    // Activity result launcher for Device Admin activation
    private final ActivityResultLauncher<Intent> activateDeviceAdminLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Toast.makeText(this, "Device Admin activated!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Device Admin activation failed or cancelled.", Toast.LENGTH_LONG).show();
                }
                updateLaunchButtonState(); // Re-check state after returning
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appSpinner = findViewById(R.id.app_spinner);
        timerKnob = findViewById(R.id.timer_knob);
        timeDisplayTextViewCircular = findViewById(R.id.time_display_textview_circular);
        launchButton = findViewById(R.id.launch_button);

        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdminComponent = new ComponentName(this, MyDeviceAdminReceiver.class);

        loadInstalledApps();
        setupTimerKnob();
        setupLaunchButton();

        // Initial check for notification permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                Toast.makeText(this, "Please enable notifications for this app for timer status.", Toast.LENGTH_LONG).show();

                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());

            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update button state in case Device Admin was enabled/disabled while app was paused
        updateLaunchButtonState();
    }

    private void loadInstalledApps() {
        PackageManager pm = getPackageManager();
        installedApps = new ArrayList<>();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        ArrayList<String> appNames = new ArrayList<>();
        appNames.add("Select an app..."); // Prompt

        for (ApplicationInfo packageInfo : packages) {
            // Filter out system apps, or apps without a launch intent
            if (pm.getLaunchIntentForPackage(packageInfo.packageName) != null &&
                    !isSystemPackage(packageInfo)) {
                String appName = pm.getApplicationLabel(packageInfo).toString();
                installedApps.add(new AppInfo(appName, packageInfo.packageName));
            }
        }
        Collections.sort(installedApps, (o1, o2) -> o1.appName.compareToIgnoreCase(o2.appName));

        for(AppInfo appInfo : installedApps) {
            appNames.add(appInfo.appName);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, appNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        appSpinner.setAdapter(adapter);

        appSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    selectedApp = installedApps.get(position - 1); // Adjust index
                } else {
                    selectedApp = null;
                }
                updateLaunchButtonState();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedApp = null;
                updateLaunchButtonState();
            }
        });
    }

    private boolean isSystemPackage(ApplicationInfo ai) {
        return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }


    private void setupTimerKnob() {
        // initial state get
        selectedTimeMinutes = timerKnob.getState();

        // IS is within bounds
        if (selectedTimeMinutes > MAX_TIME_MINUTES) {
            selectedTimeMinutes = MAX_TIME_MINUTES;
            timerKnob.setState(selectedTimeMinutes);
        }
        if (selectedTimeMinutes < MIN_TIME_MINUTES) {
            selectedTimeMinutes = MIN_TIME_MINUTES;
            timerKnob.setState(selectedTimeMinutes);
        }
        updateCircularTimeDisplay(selectedTimeMinutes);

        timerKnob.setOnStateChanged(newState -> {

            selectedTimeMinutes = newState;

            if (selectedTimeMinutes < MIN_TIME_MINUTES) {
                selectedTimeMinutes = MIN_TIME_MINUTES;

                // timerKnob.setState(MIN_TIME_MINUTES); // occasionally causes loop when snapping; easier to disable
            }

            updateCircularTimeDisplay(selectedTimeMinutes);
            updateLaunchButtonState();
        });
    }

    private void updateCircularTimeDisplay(int minutes) {
        timeDisplayTextViewCircular.setText(String.format(Locale.getDefault(), "%d min", minutes));
    }

    private void updateLaunchButtonState() {
        boolean isAdminActive = devicePolicyManager.isAdminActive(deviceAdminComponent);
        if (selectedApp != null && selectedTimeMinutes >= MIN_TIME_MINUTES) {
            if (isAdminActive) {
                launchButton.setEnabled(true);
                launchButton.setText("Launch " + selectedApp.appName + " & Start Timer");
            } else {
                launchButton.setEnabled(true);
                launchButton.setText("Enable Device Admin to Proceed");
            }
        } else {
            launchButton.setEnabled(false);
            launchButton.setText("Select App and Set Time");
        }
    }

    private void setupLaunchButton() {
        launchButton.setOnClickListener(v -> {
            if (!devicePolicyManager.isAdminActive(deviceAdminComponent)) {
                requestDeviceAdmin();
                return;
            }

            if (selectedApp != null && selectedTimeMinutes >= MIN_TIME_MINUTES) {
                Intent serviceIntent = new Intent(this, TimerLockService.class);
                serviceIntent.putExtra(TimerLockService.EXTRA_TIME_MILLIS, (long) selectedTimeMinutes * 60 * 1000);


                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }

                // Launch the selected app
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(selectedApp.packageName);
                if (launchIntent != null) {
                    startActivity(launchIntent);
                    Toast.makeText(this, "Timer started for " + selectedTimeMinutes + " min. App will lock after.", Toast.LENGTH_LONG).show();
                    // finish(); //
                } else {
                    Toast.makeText(this, "Could not launch " + selectedApp.appName, Toast.LENGTH_SHORT).show();
                }
            }
        });
        // Initial state update
        updateLaunchButtonState();
    }


    private void requestDeviceAdmin() {
        if (!devicePolicyManager.isAdminActive(deviceAdminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "This app needs Device Administrator permission to lock your screen after the timer ends.");
            activateDeviceAdminLauncher.launch(intent);
        } else {
            Toast.makeText(this, "Device Admin is already active.", Toast.LENGTH_SHORT).show();
        }
    }

    // Helper class to store app info
    private static class AppInfo {
        String appName;
        String packageName;

        AppInfo(String appName, String packageName) {
            this.appName = appName;
            this.packageName = packageName;
        }

        @Override
        public String toString() {
            return appName; // spinner app name
        }
    }
}