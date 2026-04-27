package com.example.smartcaneapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Vibrator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import java.util.Locale;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.smartcaneapp.core.BluetoothManager;
import com.example.smartcaneapp.core.SensorDataHandler;
import com.example.smartcaneapp.core.TtsManager;
import com.example.smartcaneapp.modules.EmergencyActivity;
import com.example.smartcaneapp.modules.OcrActivity;
import com.example.smartcaneapp.modules.SettingsActivity;
import com.example.smartcaneapp.modules.VisionActivity;
import com.example.smartcaneapp.modules.ProfileActivity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements BluetoothManager.BluetoothListener {

    private TextView tvDistance, tvStatus, tvHeader;
    private Button btnConnect;

    private BluetoothManager bluetoothManager;
    private TtsManager ttsManager;
    private SensorDataHandler sensorDataHandler;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                // Simplified completion
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        applyLocale();

        // Initialize Core Managers
        bluetoothManager = BluetoothManager.getInstance();
        bluetoothManager.setListener(this);
        ttsManager = TtsManager.getInstance(this);
        sensorDataHandler = new SensorDataHandler();

        // UI Setup
        tvDistance = findViewById(R.id.tvDistance);
        tvStatus = findViewById(R.id.tvStatus);
        tvHeader = findViewById(R.id.tvHeader);
        btnConnect = findViewById(R.id.btnConnect);
        tvHeader.setOnLongClickListener(v -> {
            ttsManager.speak("Welcome to Aura. You are on the main screen. " +
                    "Top left is Vision for hazard detection. " +
                    "Top right is Reader for text to speech. " +
                    "Bottom left is SOS for emergency. " +
                    "Bottom right is Profile for your medical details. " +
                    "Double tap any button to activate.");
            return true;
        });

        initVoiceSOS();
        checkPermissions();
        
        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            vibratePattern(new long[]{0, 100});
            startActivity(new Intent(this, SettingsActivity.class));
        });

        // Navigation
        findViewById(R.id.btnVision).setOnClickListener(v -> {
            vibratePattern(new long[]{0, 100}); // 1 short
            startActivity(new Intent(this, VisionActivity.class));
        });
        findViewById(R.id.btnOcr).setOnClickListener(v -> {
            vibratePattern(new long[]{0, 100, 100, 100}); // 2 short
            startActivity(new Intent(this, OcrActivity.class));
        });
        findViewById(R.id.btnSos).setOnClickListener(v -> {
            vibratePattern(new long[]{0, 500}); // 1 long
            startActivity(new Intent(this, EmergencyActivity.class));
        });
        findViewById(R.id.btnProfile).setOnClickListener(v -> {
            vibratePattern(new long[]{0, 100, 100, 100, 100, 100}); // 3 short
            startActivity(new Intent(this, ProfileActivity.class));
        });


        sensorDataHandler.setListener(data -> {
            tvDistance.setText(data.distance + " cm");
            String status = data.getStatusMessage();
            tvStatus.setText("Status: " + status);
            
            if (!status.equals("Safe")) {
                ttsManager.speak(status);
            }
        });

        btnConnect.setOnClickListener(v -> {
            if (bluetoothManager.isConnected()) {
                bluetoothManager.disconnect();
            } else {
                bluetoothManager.connect(this);
            }
        });
    }

    private void setAppLocale(String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        DisplayMetrics dm = resources.getDisplayMetrics();
        config.setLocale(locale);
        resources.updateConfiguration(config, dm);
    }

    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        permissions.add(Manifest.permission.CAMERA);
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);

        List<String> missingPermissions = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(p);
            }
        }

        if (!missingPermissions.isEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toArray(new String[0]));
        } else {
            startListening();
        }
    }

    private void vibratePattern(long[] pattern) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            v.vibrate(pattern, -1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyLocale();
    }

    private void applyLocale() {
        // Updated to remove Hindi support
        ((TextView)findViewById(R.id.tvHeader)).setText(R.string.app_name);
        ((TextView)findViewById(R.id.tvStatus)).setText(R.string.status_ready);
    }

    private void initVoiceSOS() {
        // Continuous listening removed to stop "dip dip" sound.
        // Will use Volume Buttons for SOS instead.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // Trigger SOS on volume button press
            vibratePattern(new long[]{0, 1000}); // Long vibration confirmation
            ttsManager.speak("Volume button emergency trigger. Opening SOS.");
            startActivity(new Intent(this, EmergencyActivity.class));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void startListening() {
        // Removed
    }

    @Override
    public void onDataReceived(String data) {
        sensorDataHandler.processRawData(data);
    }

    @Override
    public void onStatusChanged(String status) {
        tvStatus.setText("Status: " + status);
        btnConnect.setText(bluetoothManager.isConnected() ? "Disconnect" : "Connect");
    }

    @Override
    public void onError(String error) {
        tvStatus.setText("Error: " + error);
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't shutdown singleton here if we want background persistence later, 
        // but for now let's cleanup to avoid leaks
        ttsManager.shutdown();
    }
}