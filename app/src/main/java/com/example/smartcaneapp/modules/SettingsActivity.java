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

    private MaterialButtonToggleGroup toggleLanguage;
    private SwitchMaterial switchVibration;
    private SeekBar seekVolume;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        toggleLanguage = findViewById(R.id.toggleLanguage);
        switchVibration = findViewById(R.id.switchVibration);
        seekVolume = findViewById(R.id.seekVolume);
        Button btnSave = findViewById(R.id.btnSaveSettings);

        prefs = getSharedPreferences("SmartCanePrefs", Context.MODE_PRIVATE);

        // Load current settings
        boolean isHindi = prefs.getBoolean("lang_hindi", false);
        if (isHindi) {
            toggleLanguage.check(R.id.btnHindi);
        } else {
            toggleLanguage.check(R.id.btnEnglish);
        }

        switchVibration.setChecked(prefs.getBoolean("haptic_enabled", true));
        seekVolume.setProgress(prefs.getInt("volume", 80));

        btnSave.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveSettings();
        });
    }

    private void saveSettings() {
        boolean isHindi = toggleLanguage.getCheckedButtonId() == R.id.btnHindi;
        
        prefs.edit()
                .putBoolean("lang_hindi", isHindi)
                .putString("app_lang", isHindi ? "hi" : "en")
                .putBoolean("haptic_enabled", switchVibration.isChecked())
                .putInt("volume", seekVolume.getProgress())
                .apply();

        setAppLocale(isHindi ? "hi" : "en");
        finish();
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
}
