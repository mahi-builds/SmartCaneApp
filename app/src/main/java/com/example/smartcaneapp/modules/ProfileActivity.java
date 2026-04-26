package com.example.smartcaneapp.modules;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartcaneapp.R;

public class ProfileActivity extends AppCompatActivity {

    private EditText etName, etAge, etBloodGroup, etMedical;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        etName = findViewById(R.id.etName);
        etAge = findViewById(R.id.etAge);
        etBloodGroup = findViewById(R.id.etBloodGroup);
        etMedical = findViewById(R.id.etMedical);
        Button btnSave = findViewById(R.id.btnSaveProfile);

        prefs = getSharedPreferences("SmartCanePrefs", Context.MODE_PRIVATE);

        // Load data
        etName.setText(prefs.getString("user_name", ""));
        etAge.setText(prefs.getString("user_age", ""));
        etBloodGroup.setText(prefs.getString("user_blood", ""));
        etMedical.setText(prefs.getString("user_medical", ""));

        btnSave.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveProfile();
        });
    }

    private void saveProfile() {
        prefs.edit()
                .putString("user_name", etName.getText().toString())
                .putString("user_age", etAge.getText().toString())
                .putString("user_blood", etBloodGroup.getText().toString())
                .putString("user_medical", etMedical.getText().toString())
                .apply();
        Toast.makeText(this, "Profile Updated!", Toast.LENGTH_SHORT).show();
        finish();
    }
}
