#include "BluetoothSerial.h"

BluetoothSerial SerialBT;

// ─── PIN DEFINITIONS ───────────────────────────────
#define TRIG_PIN        4
#define ECHO_PIN        18
#define IR_PIN          27
#define WATER_PIN       35   // analog pin for water sensor
#define BUZZER_PIN      23
#define BUTTON_PIN      15

// ─── THRESHOLDS ────────────────────────────────────
#define OBSTACLE_DIST   100  // cm - buzzer triggers under 100cm
#define WATER_THRESHOLD 500  // 0-4095, adjust after testing

// ─── TIMING ────────────────────────────────────────
unsigned long lastUltraTime     = 0;
unsigned long lastIRTime        = 0;
unsigned long lastWaterTime     = 0;
unsigned long lastBTTime        = 0;
unsigned long buzzerOnTime      = 0;
bool          buzzerActive      = false;
bool          lastButtonState   = HIGH;

void setup() {
  Serial.begin(115200);
  SerialBT.begin("SmartCane");   // Bluetooth name your friend's app should pair with

  pinMode(TRIG_PIN,   OUTPUT);
  pinMode(ECHO_PIN,   INPUT);
  pinMode(IR_PIN,     INPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(BUTTON_PIN, INPUT_PULLUP);

  digitalWrite(BUZZER_PIN, LOW);

  Serial.println("Smart Cane Ready!");
  SerialBT.println("Smart Cane Ready!");
}

// ─── ULTRASONIC READ ───────────────────────────────
float getDistance() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, 30000);
  if (duration == 0) return -1;
  return duration * 0.034 / 2;
}

// ─── BUZZER PATTERN ────────────────────────────────
void triggerBuzzer(int beeps, int onMs, int offMs) {
  for (int i = 0; i < beeps; i++) {
    digitalWrite(BUZZER_PIN, HIGH);
    delay(onMs);
    digitalWrite(BUZZER_PIN, LOW);
    delay(offMs);
  }
}

void loop() {
  unsigned long now = millis();

  // ════════════════════════════════════════
  // 1. ULTRASONIC SENSOR (every 200ms)
  // ════════════════════════════════════════
  if (now - lastUltraTime >= 200) {
    lastUltraTime = now;
    float distance = getDistance();

    if (distance == -1) {
      Serial.println("Ultrasonic: No echo");
    } else {
      Serial.print("Distance: ");
      Serial.print(distance);
      Serial.println(" cm");

      // Send to app
      SerialBT.print("DIST:");
      SerialBT.println(distance);

      // Buzzer logic based on distance
      if (distance < 40) {
        // Very close - rapid beeps
        triggerBuzzer(3, 100, 80);
      } else if (distance < OBSTACLE_DIST) {
        // Getting close - slow beeps
        triggerBuzzer(1, 200, 100);
      }
    }
  }

  // ════════════════════════════════════════
  // 2. IR SENSOR (every 200ms)
  // ════════════════════════════════════════
  if (now - lastIRTime >= 200) {
    lastIRTime = now;
    int irValue = digitalRead(IR_PIN);

    if (irValue == LOW) {
      Serial.println("IR: Object DETECTED");
      SerialBT.println("IR:DETECTED");
      triggerBuzzer(2, 150, 100);
    } else {
      Serial.println("IR: Clear");
      SerialBT.println("IR:CLEAR");
    }
  }

  // ════════════════════════════════════════
  // 3. WATER SENSOR (every 500ms)
  // ════════════════════════════════════════
  if (now - lastWaterTime >= 500) {
    lastWaterTime = now;
    int waterVal = analogRead(WATER_PIN);

    Serial.print("Water: ");
    Serial.println(waterVal);
    SerialBT.print("WATER:");
    SerialBT.println(waterVal);

    if (waterVal > WATER_THRESHOLD) {
      Serial.println("Water: DETECTED!");
      SerialBT.println("WATER:DETECTED");
      triggerBuzzer(4, 100, 50);  // rapid alarm
    }
  }

  // ════════════════════════════════════════
  // 4. EMERGENCY BUTTON
  // ════════════════════════════════════════
  bool buttonState = digitalRead(BUTTON_PIN);

  if (buttonState == LOW && lastButtonState == HIGH) {
    delay(50); // debounce
    if (digitalRead(BUTTON_PIN) == LOW) {
      Serial.println("EMERGENCY BUTTON PRESSED!");
      SerialBT.println("EMERGENCY:SOS");   // App catches this and sends SMS

      
    }
  }
  lastButtonState = buttonState;
}