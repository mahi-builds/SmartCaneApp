package com.example.smartcaneapp.core;

public class SensorDataHandler {

    public static class SensorData {
        public int distance = 100;
        public int ir = 1;      // 1 = safe, 0 = hazard
        public int water = 1;   // 1 = safe, 0 = hazard
        public int button = 0;  // 1 = pressed, 0 = not pressed
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
    private SensorData currentData = new SensorData();

    public interface SensorListener {
        void onDataParsed(SensorData data);
    }

    private SensorListener listener;

    public void setListener(SensorListener listener) {
        this.listener = listener;
    }

    public void processRawData(String raw) {
        if (raw == null) return;
        
        buffer.append(raw);
        int newlineIndex;
        while ((newlineIndex = buffer.indexOf("\n")) != -1) {
            String line = buffer.substring(0, newlineIndex).trim();
            buffer.delete(0, newlineIndex + 1);
            
            if (!line.isEmpty()) {
                parseLine(line);
            }
        }
    }

    private void parseLine(String line) {
        currentData.raw = line;
        currentData.button = 0; // Reset button trigger for each line

        if (line.startsWith("DIST:")) {
            try {
                float dist = Float.parseFloat(line.substring(5).trim());
                currentData.distance = (dist <= 0) ? 999 : (int) dist;
            } catch (Exception e) {
                currentData.distance = 999;
            }
        } else if (line.startsWith("IR:")) {
            String status = line.substring(3).trim();
            currentData.ir = status.equals("DETECTED") ? 0 : 1;
        } else if (line.startsWith("WATER:")) {
            String status = line.substring(6).trim();
            if (status.equals("DETECTED")) {
                currentData.water = 0;
            } else {
                try {
                    int waterVal = Integer.parseInt(status);
                    currentData.water = (waterVal > 500) ? 0 : 1;
                } catch (Exception e) {}
            }
        } else if (line.equals("EMERGENCY:SOS")) {
            currentData.button = 1;
        }

        if (listener != null) {
            SensorData copy = new SensorData();
            copy.distance = currentData.distance;
            copy.ir = currentData.ir;
            copy.water = currentData.water;
            copy.button = currentData.button;
            copy.raw = currentData.raw;
            listener.onDataParsed(copy);
        }
    }
}
