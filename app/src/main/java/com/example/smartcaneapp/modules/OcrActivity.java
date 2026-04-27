package com.example.smartcaneapp.modules;
import android.content.SharedPreferences;

import android.content.Context;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.smartcaneapp.R;
import com.example.smartcaneapp.core.TtsManager;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OcrActivity extends AppCompatActivity {
    private PreviewView previewView;
    private TextView tvOcrResult;
    private ExecutorService cameraExecutor;
    private TextRecognizer textRecognizer;
    private TtsManager ttsManager;
    private String appLang;
    private SharedPreferences prefs;
    private Translator translator;
    private boolean isTranslatorReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr);

        previewView = findViewById(R.id.previewView);
        tvOcrResult = findViewById(R.id.tvOcrResult);
        
        ttsManager = TtsManager.getInstance(this);
        
        prefs = getSharedPreferences("SmartCanePrefs", MODE_PRIVATE);
        appLang = prefs.getString("language", "en");

        // Devanagari recognizer supports both Latin and Devanagari scripts
        textRecognizer = TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build());
        
        if (appLang.equals("hi")) {
            ttsManager.setLanguage(new java.util.Locale("hi", "IN"));
            initTranslator();
        } else {
            ttsManager.setLanguage(java.util.Locale.US);
        }
        
        cameraExecutor = Executors.newSingleThreadExecutor();

        startCamera();

        findViewById(R.id.btnCapture).setOnClickListener(v -> {
            String currentText = tvOcrResult.getText().toString();
            if (!currentText.isEmpty() && !currentText.equals(getString(R.string.ocr_hint))) {
                if (appLang.equals("hi") && !isTranslatorReady) {
                    ttsManager.speakImmediate("कृपया प्रतीक्षा करें, अनुवाद लोड हो रहा है"); // "Please wait, translation loading"
                } else {
                    ttsManager.speakImmediate(currentText);
                }
            }
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("OcrActivity", "Camera start failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, image -> {
            processImageProxy(image, imageAnalysis);
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    private long lastAnalysisTime = 0;

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImageProxy(androidx.camera.core.ImageProxy imageProxy, ImageAnalysis imageAnalysis) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAnalysisTime < 1500) { // Analysis every 1.5s
            imageProxy.close();
            return;
        }
        lastAnalysisTime = currentTime;

        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            
            textRecognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        StringBuilder sb = new StringBuilder();
                        java.util.List<com.google.mlkit.vision.text.Text.TextBlock> blocks = new java.util.ArrayList<>(visionText.getTextBlocks());
                        
                        // Sort blocks by size (area) to prioritize large text
                        blocks.sort((b1, b2) -> {
                            android.graphics.Rect rect1 = b1.getBoundingBox();
                            android.graphics.Rect rect2 = b2.getBoundingBox();
                            if (rect1 == null || rect2 == null) return 0;
                            int area1 = rect1.width() * rect1.height();
                            int area2 = rect2.width() * rect2.height();
                            return Integer.compare(area2, area1);
                        });

                        int count = 0;
                        for (com.google.mlkit.vision.text.Text.TextBlock block : blocks) {
                            if (count >= 10) break; // Read up to 10 blocks for more detail
                            String text = block.getText();
                            if (text.length() > 3) {
                                sb.append(text).append(" "); // Append with space for continuous reading
                                count++;
                            }
                        }
                        String resultText = sb.toString().trim();
                        if (!resultText.isEmpty()) {
                            if (appLang.equals("hi") && isTranslatorReady) {
                                translateAndDisplay(resultText);
                            } else {
                                if (!resultText.equals(tvOcrResult.getText().toString())) {
                                    tvOcrResult.setText(resultText);
                                    triggerHapticFeedback();
                                    // Auto-speak if it's a major new detection
                                    if (resultText.length() < 30) ttsManager.speak(resultText); 
                                }
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.e("OcrActivity", "OCR failed", e))
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
        }
    }


    private void initTranslator() {
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.HINDI)
                .build();
        translator = Translation.getClient(options);
        
        translator.downloadModelIfNeeded()
                .addOnSuccessListener(unused -> {
                    isTranslatorReady = true;
                    Log.d("OcrActivity", "Translation model ready");
                })
                .addOnFailureListener(e -> Log.e("OcrActivity", "Translation model fail", e));
    }

    private void translateAndDisplay(String text) {
        translator.translate(text)
                .addOnSuccessListener(translatedText -> {
                    if (!translatedText.equals(tvOcrResult.getText().toString())) {
                        tvOcrResult.setText(translatedText);
                        triggerHapticFeedback();
                        // Auto-speak short translations (like "STOP" -> "रुको")
                        if (translatedText.length() < 30) ttsManager.speak(translatedText);
                    }
                })
                .addOnFailureListener(e -> Log.e("OcrActivity", "Translation failed", e));
    }

    private void triggerHapticFeedback() {
        if (prefs.getBoolean("haptic_enabled", true)) {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ttsManager != null) ttsManager.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ttsManager != null) ttsManager.stop();
        if (translator != null) translator.close();
        cameraExecutor.shutdown();
        textRecognizer.close();
    }
}
