/*

 Tageslichtwecker 2.0 mit ESP8266, DS1307 und ULN2803

Power:
- No module, light off: 15mA
- Module on. light off: 255 mA

Commands:
S: GETSTATE
T: SETTIME = T 15:51:55 (hh:mm:ss)
D: SETDATE = D 15.02.15-1 (DD-MM-YY-W) --> Monday=1,...;Sunday=7
L: LIGHTS_ON = All lights to maximum
W: WAKE_NOW = Wake now
Z: WAKE_STOP = Snooze
Y: SET_ALARM_WEEKDAY = Y 15:51:55 (hh:mm:ss)
E: SET_ALARM_WEEKEND = E 15:51:55 (hh:mm:ss)
R: RESET = Reset Atmega
X: Refresh Time from DS1307
Q: TCP_QUIT = Quit TCP connection

*/

// Includes
#include "Wire.h"
#include <SoftPWM.h>
#include <SoftwareSerial.h>
#include <CapSense.h>
#include <EEPROM.h>
#include "ESP8266.h"
#include <EEPROM.h>

// PINs
const int PIN_SWITCH = 5; // Alarm on/off switch
const int PIN_STATUS_LED = A3;
const int PIN_RESET = 3;
const int PIN_MODULESTATE = 6;
const int PIN_CAP_SENDER = 8;
const int PIN_CAP_RECEIVER = 7;
const int PIN_WIFI_RX = 4;
const int PIN_WIFI_TX = 2;

int PIN_LEDs[] = {
  9, 10, 11, 12, 13, A0, A1, A2
};

// Config
// #define DEBUG // Serial debug on. Warning: 328 not enough ram!
const int CAPSENSE_THRESHOLD = 50;
const int TIME_DELTA_1 = 7000; // Time between power-iterations of LED < 15
const int TIME_DELTA_2 = 3000; // Time between power-iterations of LED > 15
const unsigned long TIME_UPDATEINTERVAL = 3600000; // Update-Cycle to get the precise time from the chip (1 hour)
#define WIFISSID "wifi"
#define WIFIPASSWORD "blablablafoo"

// Protocol
const int WAITING = 0;
const int WAKING_UP = 1;
const int WAKED_UP = 2;
const char COMMAND_GETSTATE = 'S'; //
const char COMMAND_SETTIME = 'T'; // T 15:51:55 (hh:mm:ss)
const char COMMAND_SETDATE = 'D'; // D 15.02.15-1 (DD-MM-YY-W) --> Monday=1
const char COMMAND_LIGHTS_ON = 'L'; // All lights to maximum
const char COMMAND_WAKE_NOW = 'W'; // Wake now
const char COMMAND_WAKE_STOP = 'Z'; // Snooze
const char COMMAND_SET_ALARM_WEEKDAY = 'Y'; // Y 15:51:55 (hh:mm:ss)
const char COMMAND_SET_ALARM_WEEKEND = 'E'; // E 15:51:55 (hh:mm:ss)
const char COMMAND_REFRESH_TIME = 'X'; // Refresh time from DS1307
const char COMMAND_RESET = 'R'; // Reset Atmega
const char COMMAND_TCP_QUIT = 'Q'; // Quit TCP connection


// Constants
const int EEPROM_weekday_Hour = 0; // Adress of hour to wake
const int EEPROM_weekday_Minute = 1; // Adress of minute to wake
const int EEPROM_weekday_Second = 2; // Adress of second to wake
const int EEPROM_weekend_Hour = 3; // Adress of hour to wake
const int EEPROM_weekend_Minute = 4; // Adress of minute to wake
const int EEPROM_weekend_Second = 5; // Adress of second to wake
const int EEPROM_lightstate = 6; // Adress of second to wake
const int DS1307_ADRESS = 0x68;

// Objects
SoftwareSerial esp8266Serial = SoftwareSerial(PIN_WIFI_RX, PIN_WIFI_TX);
ESP8266 wifi = ESP8266(esp8266Serial);
CapSense cap = CapSense(PIN_CAP_SENDER, PIN_CAP_RECEIVER);

// Variables
int BRIGHTNESS_LED = 1; // Brightness of status LED
boolean buttonPressed = false; // State of capacitive sensor
boolean moduleIsOn = false; // State of wifi module
int second, minute, hour, day, weekday, month, year, wake_weekday_Hour, wake_weekday_Minute, wake_weekday_Second, wake_weekend_Hour, wake_weekend_Minute, wake_weekend_Second;
boolean today_is_weekday = true;
int switch_state = 0;
int wake_state = WAITING;
int currentLED = 0;
unsigned long currentMillis = 0;
unsigned long last_time_update = 0; // Last time, the time has been updated from the chip
unsigned long last_LED_update = 0; // Last time, the LEDs has been updated
unsigned long last_second_incremental = 0; // Last time, the time has been updated
int power[] = {
  0, 0, 0, 0, 0, 0, 0, 0
};
String stateString = "123123123123123123123123123";
extern int chlID; // Wifi: client id(0-4)
long capSense_time = 0;

void setup()
{
  // Initialize in- and outputs
  pinMode(PIN_STATUS_LED, OUTPUT);
  pinMode(PIN_SWITCH, INPUT);
  digitalWrite(PIN_SWITCH, HIGH); // Pull-up
  pinMode(PIN_RESET, INPUT);
  pinMode(PIN_MODULESTATE, INPUT);
  if (digitalRead(PIN_MODULESTATE) == HIGH)
    moduleIsOn = true;
  SoftPWMBegin();
  SoftPWMSetFadeTime(PIN_STATUS_LED, 4000, 500);
  SoftPWMSet(PIN_STATUS_LED, BRIGHTNESS_LED); // Status LED
  for (int i = 0; i <= 7; i++) {
    SoftPWMSet(PIN_LEDs[i], 0);
    SoftPWMSetFadeTime(i, 3000, 1000);
  }

  // Capsense
  cap.set_CS_AutocaL_Millis(20);

  // Get light state and reset
  if (EEPROM.read(EEPROM_lightstate) == 1)
    handle_LIGHTS_ON();
  EEPROM.write(EEPROM_lightstate, 0);


  Serial.begin(9600);
  setup_wifi(); // Initialize Wifi ESP8266

  // Read current time and date
  Wire.begin(); // for DS1307
  refresh_DateTime();
  load_wakeTime();
  Serial.println(getState());
  digitalWrite(PIN_STATUS_LED, LOW);
}


void loop()
{
  // Read inputs (switch, cap and module)
  capSense_time = cap.capSense(15);
  if (capSense_time > CAPSENSE_THRESHOLD) {
    if (!buttonPressed) {
      Serial.print("Button pressed: ");
      Serial.println(capSense_time);
    }
    buttonPressed = true;
  }
  else {
    if (buttonPressed) {
      Serial.print("Button released: ");
      Serial.println(capSense_time);
    }
    buttonPressed = false;
  }
  switch_state = digitalRead(PIN_SWITCH);
  if (!moduleIsOn && digitalRead(PIN_MODULESTATE) == HIGH) // Module is activated
    handle_RESET();
  if (moduleIsOn && digitalRead(PIN_MODULESTATE) == LOW) { // Module is shut down
    moduleIsOn = false;
    status_blink(3);
  }

  // read data from wifi module
  unsigned int id;
  int length;
  int totalRead;
  char buf[100] = {
  };
  if ((length = wifi.available()) > 0) {
    id = wifi.getId();
    totalRead = wifi.read(buf, 99);

    if (length > 0) {
#ifdef DEBUG
      Serial.print("Received ");
      Serial.print(totalRead);
      Serial.print("/");
      Serial.print(length);
      Serial.print(" bytes from client ");
      Serial.print(id);
      Serial.print(": ");
      Serial.println((char*)buf);
#endif

      if (buf[0] == COMMAND_SETTIME)
        handle_SETTIME(buf);
      else if (buf[0] == COMMAND_SETDATE)
        handle_SETDATE(buf);
      else if (buf[0] == COMMAND_SET_ALARM_WEEKDAY)
        handle_SET_ALARM_WEEKDAY(buf);
      else if (buf[0] == COMMAND_SET_ALARM_WEEKEND)
        handle_SET_ALARM_WEEKEND(buf);
      else if (buf[0] == COMMAND_LIGHTS_ON)
        handle_LIGHTS_ON();
      else if (buf[0] == COMMAND_GETSTATE)
        // refresh_DateTime();
        ;
      else if (buf[0] == COMMAND_WAKE_NOW)
        handle_WAKE_NOW();
      else if (buf[0] == COMMAND_WAKE_STOP)
        handle_WAKE_STOP();
      else if (buf[0] == COMMAND_REFRESH_TIME)
        refresh_DateTime();
      else if (buf[0] == COMMAND_RESET)
        handle_RESET();
      else if (buf[0] == COMMAND_TCP_QUIT)
        wifi.close(id);
      else {
        Serial.print("Unkown: ");
        Serial.println(buf[0]);
      }
    }

#ifdef DEBUG
    Serial.print("Sending \"");
    Serial.print(getState());
    Serial.print("\": ");
#endif
    Serial.println(getStatus(wifi.write(getState())));

  }

  loop_time();
  loop_light();
}
