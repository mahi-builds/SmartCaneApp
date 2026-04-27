package com.example.smartcaneapp.modules;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartcaneapp.R;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {

    private SwitchMaterial switchVibration;
    private SeekBar seekVolume;
    private MaterialButtonToggleGroup toggleLanguage;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        switchVibration = findViewById(R.id.switchVibration);
        seekVolume = findViewById(R.id.seekVolume);
        toggleLanguage = findViewById(R.id.toggleLanguage);
        Button btnSave = findViewById(R.id.btnSaveSettings);

        prefs = getSharedPreferences("SmartCanePrefs", Context.MODE_PRIVATE);

        // Load saved language
        String lang = prefs.getString("language", "en");
        if (lang.equals("hi")) {
            toggleLanguage.check(R.id.btnLangHi);
        } else {
            toggleLanguage.check(R.id.btnLangEn);
        }

        switchVibration.setChecked(prefs.getBoolean("haptic_enabled", true));
        seekVolume.setProgress(prefs.getInt("volume", 80));

        btnSave.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveSettings();
        });
    }

    private void saveSettings() {
        String selectedLang = (toggleLanguage.getCheckedButtonId() == R.id.btnLangHi) ? "hi" : "en";
        
        prefs.edit()
                .putBoolean("haptic_enabled", switchVibration.isChecked())
                .putInt("volume", seekVolume.getProgress())
                .putString("language", selectedLang)
                .apply();

        finish();
    }
}
