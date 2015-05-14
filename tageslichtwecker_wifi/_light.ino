//#define DEBUG_LIGHT;

void loop_light() {
  // State Machine (3 states):
  if (wake_state == WAITING) { // [1] WAITING

    // Check if light mode
    if (buttonPressed) { // Check if snooze-button is pushed
#ifdef DEBUG_LIGHT
      Serial.println("[0] Waiting: Set lights on");
#endif
      handle_LIGHTS_ON();
    }

    // Check if it is weekday or weekend
    if (weekday == 6 || weekday == 7) // Saturday, Sunday
      today_is_weekday = false;
    else
      today_is_weekday = true;

    // Check if time is up
    if (
      switch_state == HIGH &&
      (
        (today_is_weekday && hour == wake_weekday_Hour && minute == wake_weekday_Minute && (second == wake_weekday_Second || second == wake_weekday_Second + 1 || second == wake_weekday_Second - 1))
        ||
        (!today_is_weekday && hour == wake_weekend_Hour && minute == wake_weekend_Minute && (second == wake_weekend_Second || second == wake_weekend_Second + 1 || second == wake_weekend_Second - 1))
      )
    )
      handle_WAKE_NOW();
  }
  else if (wake_state == WAKING_UP) { // [2] Waking Up
    if (buttonPressed) { // Check if snooze-button is pushed
#ifdef DEBUG_LIGHT
      Serial.println("[2] Waking Up --> [1] Waiting");
#endif
      handle_WAKE_STOP();
      return;
    }

    if ((power[currentLED] < 15 && last_LED_update + TIME_DELTA_1 < currentMillis) || (power[currentLED] >= 15 && last_LED_update + TIME_DELTA_2 < currentMillis)) {
#ifdef DEBUG_LIGHT
      Serial.print("[2]\tCurrent LED=");
      Serial.print(currentLED);
      Serial.print("\tCurrent Power=");
#endif

      if (power[currentLED] < 15) // Slow-Mode
        power[currentLED] += 1;
      else if (power[currentLED] >= 15) // Fast-Mode
        power[currentLED] += 5;

      SoftPWMSet(PIN_LEDs[currentLED],  power[currentLED]);

      Serial.println(power[currentLED]);

      // Check if all lights are on
      if (currentLED == 7 && power[currentLED] >= 255) {
        wake_state = WAKED_UP;
#ifdef DEBUG_LIGHT
        Serial.println("[2] Waking Up --> [3] Waked Up");
#endif
      }

      last_LED_update = currentMillis;
      currentLED = (currentLED + 1) % 8 ; // Select next LED
    }
  }
  else if (wake_state == WAKED_UP) { // [3] Waked Up
    if (buttonPressed) { // Check if snooze-button is pushed
#ifdef DEBUG_LIGHT
      Serial.println("[3] Waked Up --> [1] Waiting");
#endif
      handle_WAKE_STOP();
      return;
    }
  }

  // Set Alarm LED
  if (switch_state == HIGH && moduleIsOn)
    SoftPWMSet(PIN_STATUS_LED, BRIGHTNESS_LED);
  else   SoftPWMSet(PIN_STATUS_LED, 0);
}

void status_blink(int count) {
  for (int i = 0; i < count; i++) {
    SoftPWMSet(PIN_STATUS_LED, 255);
    delay(200);
    SoftPWMSet(PIN_STATUS_LED, 0);
    delay(200);
  }
}
