
// Load wake time from EEPROM
void load_wakeTime() {
  wake_weekday_Hour = EEPROM.read(EEPROM_weekday_Hour);
  wake_weekday_Minute = EEPROM.read(EEPROM_weekday_Minute);
  wake_weekday_Second = EEPROM.read(EEPROM_weekday_Second);
  wake_weekend_Hour = EEPROM.read(EEPROM_weekend_Hour);
  wake_weekend_Minute = EEPROM.read(EEPROM_weekend_Minute);
  wake_weekend_Second = EEPROM.read(EEPROM_weekend_Second);

  validateWakeTime_WEEKDAY();
  validateWakeTime_WEEKEND();
}

boolean validateWakeTime_WEEKDAY() {
  if (wake_weekday_Hour >= 0 && wake_weekday_Hour < 24 && wake_weekday_Minute >= 0 && wake_weekday_Minute < 60 && wake_weekday_Second >= 0 && wake_weekday_Second < 60 )
    return true;
  wake_weekday_Hour = 12;
  wake_weekday_Minute = wake_weekday_Second = 0;
  return false;
}

boolean validateWakeTime_WEEKEND() {
  if (wake_weekend_Hour >= 0 && wake_weekend_Hour < 24 && wake_weekend_Minute >= 0 && wake_weekend_Minute < 60 && wake_weekend_Second >= 0 && wake_weekend_Second < 60 )
    return true;
  wake_weekend_Hour = 12;
  wake_weekend_Minute = wake_weekend_Second = 0;
  return false;
}

boolean validateTime() {
  if (second >= 0 && second < 60 && minute >= 0 && minute < 60 && hour >= 0 && hour < 60)
    return true;
  second = minute = hour = 0;
  return false;
}

boolean validateDate() {
  if (weekday >= 1 && weekday <= 7 && day >= 0 && day <= 31 && month > 0 && month <= 12 && year >= 0 && year < 100) {
    Serial.println("VALID!");
    return true;
  }
  Serial.println("NOT VALID!");
  weekday = day = month = year = 1;
  return false;
}

// Sets current time and date on the module
void set_TimeDate() {
  validateTime();
  validateDate();
  Wire.beginTransmission(0x68);
  Wire.write(0x00);
  Wire.write(decToBcd(second));
  Wire.write(decToBcd(minute));
  Wire.write(decToBcd(hour));
  Wire.write(decToBcd(weekday));
  Wire.write(decToBcd(day));
  Wire.write(decToBcd(month));
  Wire.write(decToBcd(year));
  Wire.write(0x00);
  Wire.endTransmission();
  delay(100);
}

// Reads date and time from the DS1307 module
void refresh_DateTime() {
  // Initialize
  Wire.beginTransmission(0x68);
  Wire.write(0x00);
  Wire.endTransmission();
  Wire.requestFrom(0x68, 7);
  delay(100);

  second = bcdToDec(Wire.read());
  minute = bcdToDec(Wire.read());
  hour = bcdToDec(Wire.read() & 0b111111);
  validateTime();
  weekday = bcdToDec(Wire.read());
  // weekday--; // comes back from 1-7 ?!
  day = bcdToDec(Wire.read());
  month = bcdToDec(Wire.read());
  year = bcdToDec(Wire.read());
  validateDate();

  //#ifdef DEBUG
  Serial.print("Refreshed from DS1307: ");
  Serial.print(hour);
  Serial.print(':');
  Serial.print(minute);
  Serial.print(':');
  Serial.println(second);
  Serial.print(day);
  Serial.print('.');
  Serial.print(month);
  Serial.print('.');
  Serial.print(year);
  Serial.print('-');
  Serial.println(weekday);
  //#endif
}

// Helper
byte decToBcd(byte val) {
  return ((val / 10 * 16) + (val % 10));
}
byte bcdToDec(byte val) {
  return ((val / 16 * 10) + (val % 16));
}

// Time loop
void loop_time() {

  currentMillis = millis(); // Read current runtime

  if (last_second_incremental + 1000 < currentMillis) {
    second++;
    Serial.println(capSense_time);
    cap.reset_CS_AutoCal(); // Recalibrate every second
    if (second >= 60) {
      second = 0;
      minute++;
      if (minute >= 60) {
        minute = 0;
        hour++;
        if (hour >= 24)
          refresh_DateTime();
      }
    }
    last_second_incremental = currentMillis + (currentMillis - last_second_incremental - 1000);
  }

  // Get time from DS1307 every x milliSeconds
  if (last_time_update + TIME_UPDATEINTERVAL < currentMillis) {
    refresh_DateTime();
    last_time_update = currentMillis;
  }
}



