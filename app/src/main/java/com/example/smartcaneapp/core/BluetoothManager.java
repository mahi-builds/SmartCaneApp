package com.example.smartcaneapp.core;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BluetoothManager {

    private static final String DEVICE_NAME = "SmartCane";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static BluetoothManager instance;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private InputStream inputStream;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private boolean isRunning = false;

    private BluetoothListener listener;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface BluetoothListener {
        void onDataReceived(String data);
        void onStatusChanged(String status);
        void onError(String error);
    }

    private BluetoothManager() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public static synchronized BluetoothManager getInstance() {
        if (instance == null) {
            instance = new BluetoothManager();
        }
        return instance;
    }

    public void setListener(BluetoothListener listener) {
        this.listener = listener;
    }

    public void connect(Context context) {
        if (bluetoothAdapter == null) {
            notifyError("Bluetooth not supported");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            notifyError("Bluetooth is OFF");
            return;
        }

        executorService.execute(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        notifyError("Permission missing");
                        return;
                    }
                }

                Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
                BluetoothDevice targetDevice = null;

                for (BluetoothDevice device : pairedDevices) {
                    if (DEVICE_NAME.equals(device.getName())) {
                        targetDevice = device;
                        break;
                    }
                }

                if (targetDevice == null) {
                    notifyError("SmartCane not found in paired devices");
                    return;
                }

                notifyStatus("Connecting...");
                socket = targetDevice.createRfcommSocketToServiceRecord(MY_UUID);
                socket.connect();
                inputStream = socket.getInputStream();
                isRunning = true;
                notifyStatus("Connected ✅");

                startReading();

            } catch (IOException e) {
                notifyError("Connection Failed: " + e.getMessage());
                closeConnection();
            }
        });
    }

    private void startReading() {
        byte[] buffer = new byte[1024];
        int bytes;

        while (isRunning) {
            try {
                bytes = inputStream.read(buffer);
                if (bytes > 0) {
                    String data = new String(buffer, 0, bytes);
                    mainHandler.post(() -> {
                        if (listener != null) listener.onDataReceived(data);
                    });
                }
            } catch (IOException e) {
                if (isRunning) {
                    notifyError("Disconnected ❌");
                    closeConnection();
                }
                break;
            }
        }
    }

    public void disconnect() {
        isRunning = false;
        closeConnection();
    }

    private void closeConnection() {
        try {
            if (inputStream != null) inputStream.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        notifyStatus("Disconnected");
    }

    private void notifyStatus(String status) {
        mainHandler.post(() -> {
            if (listener != null) listener.onStatusChanged(status);
        });
    }

    private void notifyError(String error) {
        mainHandler.post(() -> {
            if (listener != null) listener.onError(error);
        });
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected();
    }
}
