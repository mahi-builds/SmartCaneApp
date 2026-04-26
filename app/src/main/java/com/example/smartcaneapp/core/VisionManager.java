package com.example.smartcaneapp.core;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class VisionManager {

    private static final String TAG = "VisionManager";
    
    private Interpreter potholeInterpreter;
    private Interpreter stairsInterpreter;
    private Interpreter doorsInterpreter;
    private float[] rawScores = new float[3];

    private int inputImageWidth = 224; 
    private int inputImageHeight = 224;

    public VisionManager(Context context) {
        try {
            potholeInterpreter = new Interpreter(loadModelFile(context, "best_pothole_float32.tflite"));
            stairsInterpreter = new Interpreter(loadModelFile(context, "best_stairs_float32.tflite"));
            doorsInterpreter = new Interpreter(loadModelFile(context, "best_doors_float32.tflite"));
            
            // Get input shape from the first ready model
            int[] inputShape = potholeInterpreter.getInputTensor(0).shape();
            inputImageHeight = inputShape[1];
            inputImageWidth = inputShape[2];
            
            Log.d(TAG, "All models loaded. Input shape: " + inputImageWidth + "x" + inputImageHeight);
        } catch (Exception e) {
            Log.e(TAG, "Error loading models", e);
        }
    }

    private MappedByteBuffer loadModelFile(Context context, String modelName) throws IOException {
        try (AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelName);
             FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor())) {
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }

    public String runInference(Bitmap bitmap) {
        if (potholeInterpreter == null || stairsInterpreter == null || doorsInterpreter == null) 
            return "Model not ready";

        // Manual Preprocessing
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputImageWidth, inputImageHeight, true);
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(resizedBitmap);

        // Run Pothole detection
        float[][] potholeOutput = new float[1][potholeInterpreter.getOutputTensor(0).shape()[1]]; 
        potholeInterpreter.run(inputBuffer, potholeOutput);
        float potholeScore = getConfidence(potholeOutput);

        // Run Stairs detection
        inputBuffer.rewind(); 
        float[][] stairsOutput = new float[1][stairsInterpreter.getOutputTensor(0).shape()[1]];
        stairsInterpreter.run(inputBuffer, stairsOutput);
        float stairsScore = getConfidence(stairsOutput);

        // Run Doors detection
        inputBuffer.rewind();
        float[][] doorsOutput = new float[1][doorsInterpreter.getOutputTensor(0).shape()[1]];
        doorsInterpreter.run(inputBuffer, doorsOutput);
        float doorsScore = getConfidence(doorsOutput);

        rawScores[0] = potholeScore;
        rawScores[1] = stairsScore;
        rawScores[2] = doorsScore;

        // Find the best detection
        float maxScore = 0;
        String bestHazard = "safe";
        float threshold = 0.40f; // 40% confidence threshold

        if (potholeScore > maxScore) {
            maxScore = potholeScore;
            bestHazard = "hazard_pothole";
        }
        if (stairsScore > maxScore) {
            maxScore = stairsScore;
            bestHazard = "hazard_stairs";
        }
        if (doorsScore > maxScore) {
            maxScore = doorsScore;
            bestHazard = "hazard_door";
        }

        return (maxScore >= threshold) ? bestHazard : "safe";
    }

    public float[] getRawScores() {
        return rawScores;
    }

    private float getConfidence(float[][] output) {
        if (output[0].length > 1) {
            // Index 0 is often 'Background'. Index 1 is the 'Hazard'.
            // We only care about the hazard score.
            return output[0][1];
        }
        return output[0][0];
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        // float32 (4 bytes) * width * height * 3 (RGB)
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * inputImageWidth * inputImageHeight * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[inputImageWidth * inputImageHeight];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        
        // Use [0, 1] Normalization (Standard for YOLO and most modern mobile models)
        for (int h = 0; h < inputImageHeight; h++) {
            for (int w = 0; w < inputImageWidth; w++) {
                final int val = intValues[h * inputImageWidth + w];
                byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f); // Red
                byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);  // Green
                byteBuffer.putFloat((val & 0xFF) / 255.0f);         // Blue
            }
        }
        return byteBuffer;
    }

    public void close() {
        if (potholeInterpreter != null) potholeInterpreter.close();
        if (stairsInterpreter != null) stairsInterpreter.close();
        if (doorsInterpreter != null) doorsInterpreter.close();
    }
}
