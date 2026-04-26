package com.example.smartcaneapp.modules;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
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
import com.example.smartcaneapp.core.VisionManager;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VisionActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView tvDetectionStatus;
    private ExecutorService cameraExecutor;
    private TtsManager ttsManager;
    private VisionManager visionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vision);

        previewView = findViewById(R.id.previewView);
        tvDetectionStatus = findViewById(R.id.tvDetectionStatus);
        
        ttsManager = TtsManager.getInstance(this);
        ttsManager.speakImmediate("Vision mode active. Checking models...");
        
        visionManager = new VisionManager(this);
        ttsManager.speakImmediate("Models loaded. Scanning.");
        cameraExecutor = Executors.newSingleThreadExecutor();

        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("VisionActivity", "Camera start failed", e);
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
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, image -> {
            processImage(image);
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    private long lastAnalysisTime = 0;

    private String lastSpokenHazard = "";
    private long lastSpokenTime = 0;

    private void triggerHapticAlert(String hazardKey) {
        android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;

        if (hazardKey.equals("hazard_door")) {
            Toast.makeText(this, "DOOR DETECTED!", Toast.LENGTH_SHORT).show();
            vibrator.vibrate(500); // Longer pulse
        } else if (hazardKey.equals("hazard_stairs")) {
            Toast.makeText(this, "STAIRS DETECTED!", Toast.LENGTH_SHORT).show();
            long[] pattern = {0, 300, 100, 300}; // Longer pulses
            vibrator.vibrate(pattern, -1);
        } else if (hazardKey.equals("hazard_pothole")) {
            Toast.makeText(this, "POTHOLE DETECTED!", Toast.LENGTH_SHORT).show();
            vibrator.vibrate(800); // Very long pulse
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImage(androidx.camera.core.ImageProxy image) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAnalysisTime < 500) { 
            image.close();
            return;
        }
        lastAnalysisTime = currentTime;

        try {
            Bitmap bitmap = previewView.getBitmap(); 
            if (bitmap != null) {
                String resultKey = visionManager.runInference(bitmap);
                String debugInfo = String.format("P: %.2f S: %.2f D: %.2f", 
                                   visionManager.getRawScores()[0], 
                                   visionManager.getRawScores()[1], 
                                   visionManager.getRawScores()[2]);

                runOnUiThread(() -> {
                    if (!resultKey.equals("safe")) {
                        int resId = getResources().getIdentifier(resultKey, "string", getPackageName());
                        String localizedResult = resId != 0 ? getString(resId) : resultKey;
                        tvDetectionStatus.setText(localizedResult + "\n" + debugInfo);
                        tvDetectionStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_gold));
                        tvDetectionStatus.setTextColor(ContextCompat.getColor(this, R.color.black));
                        
                        if (!resultKey.equals(lastSpokenHazard) || (currentTime - lastSpokenTime > 5000)) {
                            ttsManager.speakImmediate(localizedResult);
                            triggerHapticAlert(resultKey); 
                            lastSpokenHazard = resultKey;
                            lastSpokenTime = currentTime;
                        }
                    } else {
                        tvDetectionStatus.setText(getString(R.string.no_hazards) + "\n" + debugInfo);
                        tvDetectionStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.surface));
                        tvDetectionStatus.setTextColor(ContextCompat.getColor(this, R.color.white));
                        lastSpokenHazard = "";
                    }
                });
            } else {
                Log.w("VisionActivity", "Bitmap is null from previewView");
            }
        } catch (Exception e) {
            Log.e("VisionActivity", "Analysis error", e);
        }
        image.close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (visionManager != null) {
            visionManager.close();
        }
    }
}
