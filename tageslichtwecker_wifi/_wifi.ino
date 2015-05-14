void setup_wifi() {
  if (digitalRead(PIN_MODULESTATE) == HIGH) { // Only when module is connected
#ifdef DEBUG
    Serial.println("WIFI Module is on");
#endif
    delay(1000); // Waiting for Wifi to boot
    esp8266Serial.begin(9600);
    wifi.begin();
    wifi.setTimeout(1500);
    char version[16] = {
    };
    Serial.print("getVersion: ");
    Serial.print(getStatus(wifi.getVersion(version, 16)));
    Serial.print(" : ");
    Serial.println(version);
    // setWifiMode
    Serial.print("WifiMode: ");
    Serial.println(getStatus(wifi.setMode(ESP8266_WIFI_STATION)));
    // joinAP
    Serial.print("Join Wifi: ");
    Serial.println(getStatus(wifi.joinAP(WIFISSID, WIFIPASSWORD)));
    Serial.print("Multi connections: ");
    Serial.println(getStatus(wifi.setMultipleConnections(true)));
    // getIP
    IPAddress ip;
    Serial.print("getIP STA: ");
    Serial.print(getStatus(wifi.getIP(ESP8266_WIFI_STATION, ip)));
    Serial.print(" : ");
    Serial.println(ip);
    Serial.print("getIP AP: ");
    Serial.print(getStatus(wifi.getIP(ESP8266_WIFI_ACCESSPOINT, ip)));
    Serial.print(" : ");
    Serial.println(ip);
    // createServer
    Serial.print("Listening: ");
    Serial.println(getStatus(wifi.createServer(8080)));

    // deleteServer
    // Serial.print("deleteServer: ");
    // Serial.println(getStatus(wifi.deleteServer()));
  } else {
#ifdef DEBUG
    Serial.println("WIFI Module is off");
#endif
  }
}

String getStatus(bool status)
{
  if (status)
    return "OK";

  return "KO";
}

String getStatus(ESP8266CommandStatus status)
{
  switch (status) {
    case ESP8266_COMMAND_INVALID:
      return "INVALID";
      break;

    case ESP8266_COMMAND_TIMEOUT:
      return "TIMEOUT";
      break;

    case ESP8266_COMMAND_OK:
      return "OK";
      break;

    case ESP8266_COMMAND_NO_CHANGE:
      return "NO CHANGE";
      break;

    case ESP8266_COMMAND_ERROR:
      return "ERROR";
      break;

    case ESP8266_COMMAND_NO_LINK:
      return "NO LINK";
      break;

    case ESP8266_COMMAND_TOO_LONG:
      return "TOO LONG";
      break;

    case ESP8266_COMMAND_FAIL:
      return "FAIL";
      break;

    default:
      return "UNKNOWN COMMAND STATUS";
      break;
  }
}

String getRole(ESP8266Role role)
{
  switch (role) {
    case ESP8266_ROLE_CLIENT:
      return "CLIENT";
      break;

    case ESP8266_ROLE_SERVER:
      return "SERVER";
      break;

    default:
      return "UNKNOWN ROLE";
      break;
  }
}

String getProtocol(ESP8266Protocol protocol)
{
  switch (protocol) {
    case ESP8266_PROTOCOL_TCP:
      return "TCP";
      break;

    case ESP8266_PROTOCOL_UDP:
      return "UDP";
      break;

    default:
      return "UNKNOWN PROTOCOL";
      break;
  }
}


