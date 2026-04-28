package com.example.smartcaneapp.modules;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
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

    private int frameCount = 0;
    private String lastSpokenHazard = "";
    private long lastSpokenTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vision);

        previewView = findViewById(R.id.previewView);
        tvDetectionStatus = findViewById(R.id.tvDetectionStatus);

        previewView.setOnLongClickListener(v -> {
            if (visionManager != null) {
                ttsManager.speakImmediate(visionManager.getRawScoreDebug());
            }
            return true;
        });
        
        ttsManager = TtsManager.getInstance(this);
        ttsManager.speakImmediate("Vision mode active. Checking models...");
        
        visionManager = new VisionManager(this);
        ttsManager.speakImmediate(visionManager.getDiagnosticSummary());
        
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
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888) // Direct RGB
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            try {
                // Convert ImageProxy to Bitmap efficiently
                Bitmap bitmap = Bitmap.createBitmap(imageProxy.getWidth(), imageProxy.getHeight(), Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(imageProxy.getPlanes()[0].getBuffer());
                
                // Handle rotation
                int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
                if (rotationDegrees != 0) {
                    android.graphics.Matrix matrix = new android.graphics.Matrix();
                    matrix.postRotate(rotationDegrees);
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                }

                runAiInference(bitmap);
            } catch (Exception e) {
                Log.e("VisionActivity", "Analysis error", e);
            } finally {
                imageProxy.close();
            }
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    private int stabilityCounter = 0;
    private String lastDetectedHazard = "";

    private void runAiInference(Bitmap bitmap) {
        try {
            String resultKey = visionManager.runInference(bitmap);
            frameCount++;
            
            String debugInfo = String.format("[%d] P:%.0f%% S:%.0f%% D:%.0f%%", 
                                frameCount,
                                visionManager.getRawScores()[0] * 100, 
                                visionManager.getRawScores()[1] * 100, 
                                visionManager.getRawScores()[2] * 100);

            runOnUiThread(() -> {
                if (!resultKey.equals("safe") && !resultKey.equals("Model not ready")) {
                    int resId = getResources().getIdentifier(resultKey, "string", getPackageName());
                    String localizedResult = resId != 0 ? getString(resId) : resultKey;
                    
                    tvDetectionStatus.setText(localizedResult + "\n" + debugInfo);
                    tvDetectionStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_gold));
                    tvDetectionStatus.setTextColor(ContextCompat.getColor(this, R.color.black));
                    
                    long currentTime = System.currentTimeMillis();
                    // Prevent repetitive speech: only speak if it's a new hazard or 3 seconds have passed
                    if (!resultKey.equals(lastSpokenHazard) || (currentTime - lastSpokenTime > 3000)) {
                        ttsManager.speakImmediate(localizedResult);
                        triggerHapticAlert(resultKey); 
                        lastSpokenHazard = resultKey;
                        lastSpokenTime = currentTime;
                    }
                } else {
                    tvDetectionStatus.setText(getString(R.string.no_hazards) + "\n" + debugInfo);
                    tvDetectionStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.surface));
                    tvDetectionStatus.setTextColor(ContextCompat.getColor(this, R.color.white));
                    
                    if (!lastSpokenHazard.isEmpty()) {
                        ttsManager.speakImmediate(getString(R.string.no_hazards));
                        lastSpokenHazard = "";
                    }
                }
            });
        } catch (Exception e) {
            Log.e("VisionActivity", "Inference error", e);
        }
    }

    private void triggerHapticAlert(String hazardKey) {
        android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;

        if (hazardKey.equals("hazard_door")) {
            vibrator.vibrate(500); 
        } else if (hazardKey.equals("hazard_stairs")) {
            long[] pattern = {0, 300, 100, 300}; 
            vibrator.vibrate(pattern, -1);
        } else if (hazardKey.equals("hazard_pothole")) {
            vibrator.vibrate(800); 
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (visionManager != null) {
            visionManager.close();
        }
        cameraExecutor.shutdown();
    }
}
