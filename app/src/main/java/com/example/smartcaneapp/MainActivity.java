package com.example.smartcaneapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket socket;
    InputStream inputStream;

    TextView tvDistance, tvStatus;
    Button btnConnect;

    TextToSpeech tts;

    String lastSpoken = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ✅ Permission AFTER super.onCreate
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    1);
        }

        tvDistance = findViewById(R.id.tvDistance);
        tvStatus = findViewById(R.id.tvStatus);
        btnConnect = findViewById(R.id.btnConnect);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });

        btnConnect.setOnClickListener(v -> connectBluetooth());
    }

    private void connectBluetooth() {
        try {

            if (bluetoothAdapter == null) {
                tvStatus.setText("Bluetooth not supported");
                return;
            }

            if (!bluetoothAdapter.isEnabled()) {
                tvStatus.setText("Turn ON Bluetooth");
                return;
            }

            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals("SmartCane")) {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                                    != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }

                    socket = device.createRfcommSocketToServiceRecord(
                            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                    );

                    socket.connect();
                    inputStream = socket.getInputStream();

                    tvStatus.setText("Connected ✅");

                    readData();
                    return;
                }
            }

            tvStatus.setText("SmartCane not found");

        } catch (Exception e) {
            e.printStackTrace();
            tvStatus.setText("Connection Failed ❌");
        }
    }

    private void readData() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = inputStream.read(buffer);

                    if (bytes <= 0) continue;

                    String data = new String(buffer, 0, bytes);

                    runOnUiThread(() -> processData(data));

                } catch (Exception e) {
                    e.printStackTrace();
                    break; // stop thread if disconnected
                }
            }
        }).start();
    }

    private void processData(String data) {
        try {

            String[] parts = data.split(",");

            int distance = 0;
            int ir = 1;
            int water = 1;

            for (String part : parts) {

                if (!part.contains(":")) continue;

                String[] keyValue = part.split(":");
                if (keyValue.length < 2) continue;

                String key = keyValue[0].trim();
                String value = keyValue[1].trim();

                try {
                    if (key.equals("DIST")) {
                        distance = Integer.parseInt(value);
                    } else if (key.equals("IR")) {
                        ir = Integer.parseInt(value);
                    } else if (key.equals("WATER")) {
                        water = Integer.parseInt(value);
                    }
                } catch (Exception ignored) {}
            }

            // UI
            tvDistance.setText("Distance: " + distance + " cm");

            if (water == 0) {
                tvStatus.setText("⚠️ Water Detected");
            } else if (ir == 0) {
                tvStatus.setText("⚠️ Pothole Ahead");
            } else {
                tvStatus.setText("Safe");
            }

            // Voice
            String message = "";

            if (distance < 20) {
                message = "Obstacle very close";
            } else if (distance < 50) {
                message = "Obstacle ahead";
            } else if (water == 0) {
                message = "Water detected";
            } else if (ir == 0) {
                message = "Pothole ahead";
            }

            if (!message.equals("") && !message.equals(lastSpoken)) {
                tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);
                lastSpoken = message;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}