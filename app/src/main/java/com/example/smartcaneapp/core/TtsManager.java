package com.example.smartcaneapp.core;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TtsManager {

    private static TtsManager instance;
    private TextToSpeech tts;
    private boolean isReady = false;
    private Map<String, Long> lastSpokenTimes = new HashMap<>();
    private static final long COOLDOWN_MILLIS = 3000; // 3 seconds cooldown

    private TtsManager(Context context) {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                isReady = true;
            }
        });
    }

    public static synchronized TtsManager getInstance(Context context) {
        if (instance == null) {
            instance = new TtsManager(context.getApplicationContext());
        }
        return instance;
    }

    public void speak(String message) {
        if (!isReady || message == null || message.isEmpty()) return;

        long currentTime = System.currentTimeMillis();
        Long lastTime = lastSpokenTimes.get(message);

        if (lastTime == null || (currentTime - lastTime) > COOLDOWN_MILLIS) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);
            lastSpokenTimes.put(message, currentTime);
        }
    }

    public void speakImmediate(String message) {
        if (!isReady) return;
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    public void setLanguage(Locale locale) {
        if (tts != null) {
            tts.setLanguage(locale);
        }
    }

    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        instance = null;
    }
}
