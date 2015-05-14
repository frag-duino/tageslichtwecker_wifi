// Setting time
void handle_SETTIME(char* input) {
  hour = ((input[2] - 48) * 10) + ((input[3]) - 48);
  minute = ((input[5] - 48) * 10) + ((input[6]) - 48);
  second = ((input[8] - 48) * 10) + ((input[9]) - 48);
  // #ifdef DEBUG
  Serial.print(hour);
  Serial.print(":");
  Serial.print(minute);
  Serial.print(".");
  Serial.println(second);
  // #endif
  set_TimeDate();
  Serial.println(getState());
}

// Setting date
void handle_SETDATE(char* input) {
  day = ((input[2] - 48) * 10) + ((input[3]) - 48);
  month = ((input[5] - 48) * 10) + ((input[6]) - 48);
  year = ((input[8] - 48) * 10) + ((input[9]) - 48);
  weekday = ((input[11] - 48));
  //#ifdef DEBUG
  Serial.print(day);
  Serial.print(".");
  Serial.print(month);
  Serial.print(".20");
  Serial.print(year);
  Serial.print("-");
  Serial.println(weekday);
  //#endif
  set_TimeDate();
  Serial.println(getState());
}

// Setting wake time for weekdays
void handle_SET_ALARM_WEEKDAY(char* input) {
  wake_weekday_Hour = ((input[2] - 48) * 10) + ((input[3]) - 48);
  wake_weekday_Minute = ((input[5] - 48) * 10) + ((input[6]) - 48);
  wake_weekday_Second = ((input[8] - 48) * 10) + ((input[9]) - 48);
  validateWakeTime_WEEKDAY(); // Check if valid

  // Save it to the eeprom
  EEPROM.write(EEPROM_weekday_Hour, wake_weekday_Hour);
  EEPROM.write(EEPROM_weekday_Minute, wake_weekday_Minute);
  EEPROM.write(EEPROM_weekday_Second, wake_weekday_Second);
}

// Setting wake time for weekends
void handle_SET_ALARM_WEEKEND(char* input) {
  wake_weekend_Hour = ((input[2] - 48) * 10) + ((input[3]) - 48);
  wake_weekend_Minute = ((input[5] - 48) * 10) + ((input[6]) - 48);
  wake_weekend_Second = ((input[8] - 48) * 10) + ((input[9]) - 48);
  validateWakeTime_WEEKEND(); // Check if valid

  // Save it to the eeprom
  EEPROM.write(EEPROM_weekend_Hour, wake_weekend_Hour);
  EEPROM.write(EEPROM_weekend_Minute, wake_weekend_Minute);
  EEPROM.write(EEPROM_weekend_Second, wake_weekend_Second);
}

// All lights on
void handle_LIGHTS_ON() {
  for (int currentLED = 0; currentLED < 8; currentLED++)
    SoftPWMSet(PIN_LEDs[currentLED], 255);
  wake_state = WAKED_UP;
  delay(2000);
}

// Starts slowly activating the lights
void handle_WAKE_NOW() {
  // #ifdef DEBUG
  Serial.println("Starting to wake");
  // #endif
  wake_state = WAKING_UP;
  last_LED_update = currentMillis - TIME_DELTA_1;
}

// Deactivates lights
void handle_WAKE_STOP() {
#ifdef DEBUG
  Serial.println("Stopping wake");
#endif
  // Reset all 8 LEDs and blink
  SoftPWMSet(PIN_STATUS_LED, 255);
  for (int i = 0; i <= 7; i++) {
    SoftPWMSet(PIN_LEDs[i], 0);
    power[i] = 0;
    delay(100);
  }
  SoftPWMSet(PIN_STATUS_LED, 0);

  currentLED = 0;
  wake_state = WAITING;
}

// Self reset
void handle_RESET() {
#ifdef DEBUG
  Serial.println("Resetting");
#endif

  if (wake_state == WAKED_UP) // To set the light on after reset
    EEPROM.write(EEPROM_lightstate, 1);
    
  status_blink(5);

  pinMode(PIN_RESET, OUTPUT);
  digitalWrite(PIN_RESET, LOW);
}

// Prints the state the switch, LEDs, dates and times
String getState() {

  // TIME-DATE-WAKETIMEWEEKDAY-WAKETIMEWEEKEND-WAKESTATE-BUTTONSTATE
  stateString = "";

  // Time
  stateString += hour;
  stateString += ":";
  stateString += minute;
  stateString += ":";
  stateString += second;

  stateString += "-";

  // Date
  stateString += day;
  stateString += ":";
  stateString += month;
  stateString += ":";
  stateString += year;
  stateString += ":";
  stateString += weekday;

  stateString += "-";

  // WakeWeekday
  stateString += wake_weekday_Hour;
  stateString += ":";
  stateString += wake_weekday_Minute;
  stateString += ":";
  stateString += wake_weekday_Second;

  stateString += "-";

  // WakeWeekend
  stateString += wake_weekend_Hour;
  stateString += ":";
  stateString += wake_weekend_Minute;
  stateString += ":";
  stateString += wake_weekend_Second;

  stateString += "-";

  // WakeState
  stateString += wake_state;
  stateString += "-";

  // ButtonState
  stateString += switch_state;

  // New line
  stateString += "\n";

  return stateString;
}

