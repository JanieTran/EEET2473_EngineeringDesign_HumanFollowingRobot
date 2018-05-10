String msg;

void setup() {
  Serial.begin(9600);
}

void loop() {
  if (Serial.available()) {
    msg = Serial.readString();
    Serial.println(msg);
  }
}
