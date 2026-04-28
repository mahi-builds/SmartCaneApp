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
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.Locale;

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
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;

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
        String savedName = prefs.getString("emergency_name", "");
        String savedNumber = prefs.getString("emergency_number", "");
        etName.setText(savedName);
        etNumber.setText(savedNumber);

        if (!savedName.isEmpty()) {
            ttsManager.speak("Emergency SOS open. Current registered contact is " + savedName + ". Tap the huge button to send SOS, or use the voice command button.");
        } else {
            ttsManager.speak("Emergency SOS open. No contact registered. Please enter a contact manually or use voice command to call an ambulance.");
        }

        initSpeechRecognizer();
        
        findViewById(R.id.btnVoiceCommand).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                speechRecognizer.startListening(speechRecognizerIntent);
            } else {
                ttsManager.speak("Microphone permission required");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 2);
            }
        });

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

    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                ttsManager.speakImmediate("Listening");
            }
            @Override
            public void onBeginningOfSpeech() {}
            @Override
            public void onRmsChanged(float rmsdB) {}
            @Override
            public void onBufferReceived(byte[] buffer) {}
            @Override
            public void onEndOfSpeech() {}
            @Override
            public void onError(int error) {
                ttsManager.speak("Did not catch that");
            }
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (data != null && !data.isEmpty()) {
                    processVoiceCommand(data.get(0).toLowerCase());
                }
            }
            @Override
            public void onPartialResults(Bundle partialResults) {}
            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void processVoiceCommand(String command) {
        if (command.contains("send sos") || command.contains("help") || command.contains("emergency")) {
            ttsManager.speak("Sending SOS now");
            sendSosTrigger();
        } else if (command.contains("read contact") || command.contains("who is saved") || command.contains("who")) {
            String name = prefs.getString("emergency_name", "");
            if (name.isEmpty()) {
                ttsManager.speak("No contact saved.");
            } else {
                ttsManager.speak("Registered contact is " + name);
            }
        } else if (command.contains("call ambulance") || command.contains("ambulance")) {
            ttsManager.speak("Calling ambulance");
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:112")); // Default generic emergency number, should be localized
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(callIntent);
            } else {
                ttsManager.speak("Phone permission required to call");
            }
        } else {
            ttsManager.speak("Command not recognized. You can say send SOS, read contact, or call ambulance.");
        }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}
