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
        buffer.append(raw);
        
        // Simple heuristic: if we have a full "packet", process it.
        // Assuming data comes in a single burst or eventually contains all keys.
        String current = buffer.toString();
        
        // Check if we have symbols of a complete packet (start/end markers would be better, 
        // but using current logic for compatibility)
        if (current.contains("DIST") && current.contains("WATER")) {
            SensorData data = parse(current);
            if (listener != null) {
                listener.onDataParsed(data);
            }
            // Clear buffer if it gets too large or after successful parse
            if (buffer.length() > 200) {
                buffer.setLength(0);
            }
        }
    }

    private SensorData parse(String data) {
        SensorData result = new SensorData();
        result.raw = data;
        
        String[] parts = data.split(",");
        for (String part : parts) {
            try {
                if (part.contains(":")) {
                    String[] kv = part.split(":");
                    if (kv.length < 2) continue;
                    
                    String key = kv[0].trim();
                    String value = kv[1].trim();
                    
                    if (key.contains("DIST")) result.distance = Integer.parseInt(value);
                    else if (key.contains("IR")) result.ir = Integer.parseInt(value);
                    else if (key.contains("WATER")) result.water = Integer.parseInt(value);
                }
            } catch (Exception ignored) {}
        }
        return result;
    }
}
