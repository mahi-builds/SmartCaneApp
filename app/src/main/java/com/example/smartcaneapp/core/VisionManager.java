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
    
    private int potholeW, potholeH;
    private int stairsW, stairsH;
    private int doorsW, doorsH;
    
    private boolean isPotholeNCHW, isStairsNCHW, isDoorsNCHW;

    private float[] rawScores = new float[3];

    public VisionManager(Context context) {
        try {
            potholeInterpreter = new Interpreter(loadModelFile(context, "best_pothole_float32.tflite"));
            stairsInterpreter = new Interpreter(loadModelFile(context, "best_stairs_float32.tflite"));
            doorsInterpreter = new Interpreter(loadModelFile(context, "best_doors_float32.tflite"));
            
            // Initialize dimensions and format for each model
            int[] pShape = potholeInterpreter.getInputTensor(0).shape();
            isPotholeNCHW = (pShape[1] == 3);
            potholeH = isPotholeNCHW ? pShape[2] : pShape[1];
            potholeW = isPotholeNCHW ? pShape[3] : pShape[2];
            
            int[] sShape = stairsInterpreter.getInputTensor(0).shape();
            isStairsNCHW = (sShape[1] == 3);
            stairsH = isStairsNCHW ? sShape[2] : sShape[1];
            stairsW = isStairsNCHW ? sShape[3] : sShape[2];
            
            int[] dShape = doorsInterpreter.getInputTensor(0).shape();
            isDoorsNCHW = (dShape[1] == 3);
            doorsH = isDoorsNCHW ? dShape[2] : dShape[1];
            doorsW = isDoorsNCHW ? dShape[3] : dShape[2];

            logModelInfo("Pothole", potholeInterpreter);
            logModelInfo("Stairs", stairsInterpreter);
            logModelInfo("Doors", doorsInterpreter);
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading models", e);
        }
    }

    private void logModelInfo(String name, Interpreter interpreter) {
        int[] inShape = interpreter.getInputTensor(0).shape();
        int[] outShape = interpreter.getOutputTensor(0).shape();
        Log.d(TAG, String.format("Model %s: Input%s, Output%s", name, 
            java.util.Arrays.toString(inShape), java.util.Arrays.toString(outShape)));
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

    public synchronized String runInference(Bitmap bitmap) {
        if (potholeInterpreter == null || stairsInterpreter == null || doorsInterpreter == null) 
            return "Model not ready";

        // 1. Pothole Inference
        ByteBuffer pBuffer = preprocess(bitmap, potholeW, potholeH, isPotholeNCHW);
        float potholeScore = runModelInference(potholeInterpreter, pBuffer);

        // 2. Stairs Inference
        ByteBuffer sBuffer = preprocess(bitmap, stairsW, stairsH, isStairsNCHW);
        float stairsScore = runModelInference(stairsInterpreter, sBuffer);

        // 3. Doors Inference
        ByteBuffer dBuffer = preprocess(bitmap, doorsW, doorsH, isDoorsNCHW);
        float doorsScore = runModelInference(doorsInterpreter, dBuffer);

        rawScores[0] = potholeScore;
        rawScores[1] = stairsScore;
        rawScores[2] = doorsScore;

        float maxScore = 0;
        String bestHazard = "safe";
        
        // Individual thresholds to prevent false positives
        float pThreshold = 0.45f;
        float sThreshold = 0.65f; // Lowered from 80% to be more responsive
        float dThreshold = 0.35f; // Low to help with close-up detection

        if (potholeScore >= pThreshold && potholeScore > maxScore) {
            maxScore = potholeScore;
            bestHazard = "hazard_pothole";
        }
        if (stairsScore >= sThreshold && stairsScore * 1.20f > maxScore) {
            maxScore = stairsScore;
            bestHazard = "hazard_stairs";
        }
        if (doorsScore >= dThreshold && doorsScore * 1.20f > maxScore) {
            maxScore = doorsScore;
            bestHazard = "hazard_door";
        }

        return bestHazard;
    }

    private float runModelInference(Interpreter interpreter, ByteBuffer inputBuffer) {
        int[] outShape = interpreter.getOutputTensor(0).shape();
        
        if (outShape.length == 3) {
            // YOLOv8 Detection Shape: [1, 4 + classes, boxes]
            float[][][] output = new float[1][outShape[1]][outShape[2]];
            interpreter.run(inputBuffer, output);
            return extractMaxConfidence(output);
        } else {
            // Classification Shape: [1, classes]
            float[][] output = new float[1][outShape[1]];
            interpreter.run(inputBuffer, output);
            return (output[0].length > 1) ? Math.max(output[0][0], output[0][1]) : output[0][0];
        }
    }

    private float extractMaxConfidence(float[][][] output) {
        int dim1 = output[0].length;
        int dim2 = output[0][0].length;
        
        float maxScore = 0f;
        
        // Robustly find the dimension that contains class scores (usually the smaller one, e.g., 5 or 84)
        if (dim1 < dim2 && dim1 > 4) {
            // Format: [1, 4+C, N]
            int numClasses = dim1 - 4;
            for (int b = 0; b < dim2; b++) {
                for (int c = 0; c < numClasses; c++) {
                    float score = output[0][4 + c][b];
                    if (score > maxScore) maxScore = score;
                }
            }
        } else if (dim2 < dim1 && dim2 > 4) {
            // Format: [1, N, 4+C]
            int numClasses = dim2 - 4;
            for (int b = 0; b < dim1; b++) {
                for (int c = 0; c < numClasses; c++) {
                    float score = output[0][b][4 + c];
                    if (score > maxScore) maxScore = score;
                }
            }
        } else {
            // Fallback: Check everything (brute force for unknown exports)
            for (int i = 0; i < dim1; i++) {
                for (int j = 0; j < dim2; j++) {
                    float val = output[0][i][j];
                    if (val > maxScore && val <= 1.0f) maxScore = val;
                }
            }
        }
        return maxScore;
    }

    private ByteBuffer preprocess(Bitmap bitmap, int width, int height, boolean isNCHW) {
        // Letterboxing: Maintain aspect ratio by adding black padding
        Bitmap background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(background);
        canvas.drawColor(android.graphics.Color.BLACK);

        float scale = Math.min((float) width / bitmap.getWidth(), (float) height / bitmap.getHeight());
        int newW = Math.round(scale * bitmap.getWidth());
        int newH = Math.round(scale * bitmap.getHeight());
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, newW, newH, true);

        int left = (width - newW) / 2;
        int top = (height - newH) / 2;
        canvas.drawBitmap(resized, left, top, null);

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * width * height * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        
        int[] intValues = new int[width * height];
        background.getPixels(intValues, 0, width, 0, 0, width, height);
        
        if (isNCHW) {
            for (int val : intValues) byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
            for (int val : intValues) byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
            for (int val : intValues) byteBuffer.putFloat((val & 0xFF) / 255.0f);
        } else {
            for (int val : intValues) {
                byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
                byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
                byteBuffer.putFloat((val & 0xFF) / 255.0f);
            }
        }
        return byteBuffer;
    }

    public float[] getRawScores() {
        return rawScores;
    }

    public String getRawScoreDebug() {
        return String.format("Pothole %.0f. Stairs %.0f. Door %.0f percent.", 
            rawScores[0] * 100, rawScores[1] * 100, rawScores[2] * 100);
    }

    public String getDiagnosticSummary() {
        if (potholeInterpreter == null || stairsInterpreter == null || doorsInterpreter == null) {
            return "Vision system error. Models not loaded.";
        }
        return "Vision system ready. Scanning for hazards.";
    }

    public synchronized void close() {
        if (potholeInterpreter != null) {
            potholeInterpreter.close();
            potholeInterpreter = null;
        }
        if (stairsInterpreter != null) {
            stairsInterpreter.close();
            stairsInterpreter = null;
        }
        if (doorsInterpreter != null) {
            doorsInterpreter.close();
            doorsInterpreter = null;
        }
    }
}
