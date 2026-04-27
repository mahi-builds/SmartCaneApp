package com.example.smartcaneapp.core;

public class SensorDataHandler {

    public static class SensorData {
        public int distance = 100;
        public int ir = 1;      // 1 = safe, 0 = hazard
        public int water = 1;   // 1 = safe, 0 = hazard
        public String raw = "";

        public boolean isSafe() {
            return distance > 20 && ir == 1 && water == 1;
        }

        public String getStatusMessage() {
            if (water == 0) return "Water Detected";
            if (ir == 0) return "Pothole Ahead";
            if (distance < 20) return "Obstacle Very Close";
            if (distance < 50) return "Obstacle Ahead";
            return "Safe";
        }
    }

    private StringBuilder buffer = new StringBuilder();

    public interface SensorListener {
        void onDataParsed(SensorData data);
    }

    private SensorListener listener;

    public void setListener(SensorListener listener) {
        this.listener = listener;
    }

    public void processRawData(String raw) {
        if (raw == null) return;
        
        // Remove any extra spaces or hidden characters like \n or \r
        String cleanData = raw.trim();
        if (cleanData.isEmpty()) return;

        try {
            // Check if the data is a valid number
            float distance = Float.parseFloat(cleanData);
            
            SensorData data = new SensorData();
            // If distance is 0, it usually means nothing was detected (sensor timed out)
            // We'll treat 0 as 999 (safe/clear path) to avoid false "0cm" alerts
            data.distance = (distance <= 0) ? 999 : (int) distance;
            data.ir = 1;    // Default to safe
            data.water = 1; // Default to safe
            data.raw = cleanData;
            
            if (listener != null) {
                listener.onDataParsed(data);
            }
        } catch (NumberFormatException e) {
            // Not a numeric value? Handle potential status strings
            if (cleanData.equalsIgnoreCase("no_object") || cleanData.equals("0")) {
                SensorData data = new SensorData();
                data.distance = 999; 
                data.raw = cleanData;
                if (listener != null) listener.onDataParsed(data);
            }
        }
    }
}
