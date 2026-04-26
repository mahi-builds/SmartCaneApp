package com.example.smartcaneapp.modules;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.view.HapticFeedbackConstants;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.smartcaneapp.R;
import com.example.smartcaneapp.core.TtsManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

public class EmergencyActivity extends AppCompatActivity {

    private EditText etName, etNumber;
    private Button btnSave, btnSos;
    private SharedPreferences prefs;
    private FusedLocationProviderClient fusedLocationClient;
    private TtsManager ttsManager;

    private final ActivityResultLauncher<Intent> pickContactLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Uri contactUri = result.getData().getData();
            String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME};
            try (Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    etNumber.setText(cursor.getString(0));
                    etName.setText(cursor.getString(1));
                }
            }
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency);

        etName = findViewById(R.id.etContactName);
        etNumber = findViewById(R.id.etContactNumber);
        btnSave = findViewById(R.id.btnSaveContact);
        btnSos = findViewById(R.id.btnSendSos);
        Button btnPick = findViewById(R.id.btnPickContact);

        prefs = getSharedPreferences("SmartCanePrefs", Context.MODE_PRIVATE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        ttsManager = TtsManager.getInstance(this);

        // Load existing contact
        etName.setText(prefs.getString("emergency_name", ""));
        etNumber.setText(prefs.getString("emergency_number", ""));

        btnPick.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            pickContactLauncher.launch(intent);
        });

        btnSave.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveContact();
        });
        
        btnSos.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            sendSosTrigger();
        });
    }

    private void saveContact() {
        String name = etName.getText().toString().trim();
        String number = etNumber.getText().toString().trim();

        if (name.isEmpty() || number.isEmpty()) {
            Toast.makeText(this, "Please fill all details", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.edit()
                .putString("emergency_name", name)
                .putString("emergency_number", number)
                .apply();

        Toast.makeText(this, "Contact Saved ✅", Toast.LENGTH_SHORT).show();
        ttsManager.speak("Emergency contact " + name + " saved successfully");
    }

    private void sendSosTrigger() {
        String number = prefs.getString("emergency_number", "");
        if (number.isEmpty()) {
            Toast.makeText(this, "Save an emergency contact first", Toast.LENGTH_LONG).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            String userName = prefs.getString("user_name", "User");
            String userBlood = prefs.getString("user_blood", "Unknown");
            String userMedical = prefs.getString("user_medical", "None");

            String message = getString(R.string.sos_message_format, userName, userBlood, userMedical);
            
            if (location != null) {
                message += "Location: https://www.google.com/maps/search/?api=1&query=" + location.getLatitude() + "," + location.getLongitude();
            } else {
                message += "Location unavailable.";
            }

            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(number, null, message, null, null);
                Toast.makeText(this, "SOS Sent! 🚨", Toast.LENGTH_LONG).show();
                
                // Also trigger a call
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse("tel:" + number));
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    startActivity(callIntent);
                }
                
            } catch (Exception e) {
                Toast.makeText(this, "Failed to send SOS: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
