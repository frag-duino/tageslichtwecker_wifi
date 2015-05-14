package fragduino.de.tageslichtweckerwifi;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

/**
 * Created by Marcel on 08.03.2015.
 */
public class ClientThread implements Runnable {
    private int conf_timeout; // ms
    private Socket socket;
    private MainActivity context;

    // Field types
    public static String FIELD_COMMAND = "COMMAND";
    public static String FIELD_HOUR = "HOUR";
    public static String FIELD_MINUTE = "MINUTE";
    public static String FIELD_SECOND = "SECOND";
    public static String FIELD_DAY = "DAY";
    public static String FIELD_MONTH = "MONTH";
    public static String FIELD_YEAR = "YEAR";
    public static String FIELD_WEEKDAY = "WEEKDAY";

    public static char COMMAND_GETSTATE = 'S';
    public static char COMMAND_SETTIME = 'T'; // T 15:51:55 (hh:mm:ss)
    public static char COMMAND_SETDATE = 'D'; // D 15.02.15-1 (DD-MM-YY-W) --> Monday=1
    public static char COMMAND_LIGHTS_ON = 'L'; // All lights to maximum
    // public static char COMMAND_WAKE_NOW = 'W'; // Wake now
    public static char COMMAND_WAKE_STOP = 'Z'; // Snooze
    public static char COMMAND_SET_ALARM_WEEKDAY = 'Y'; // Y 15:51:55 (hh:mm:ss)
    public static char COMMAND_SET_ALARM_WEEKEND = 'E'; // E 15:51:55 (hh:mm:ss)
    public static char COMMAND_REFRESH_TIME = 'X'; // Refresh time from DS1307
    public static char COMMAND_RESET = 'R'; // Reset Atmega
    // public static char COMMAND_TCP_QUIT = 'Q'; // Quit TCP connection

    // Static
    public static int ALARM_MASTER = 1;
    public static int ALARM_SLAVE = 2;

    // Wake states
    public final static int STATE_WAITING = 0;
    // public final static int STATE_WAKING_UP = 1;
    // public final static int STATE_WAKED_UP = 2;

    // Variables
    private char input_command;
    private Bundle input_parameters;
    private boolean is_master;
    String monitor;

    public ClientThread(MainActivity context, int master_slave, Bundle parameters) {
        this.context = context;
        if (master_slave == ALARM_MASTER) {
            is_master = true;
            monitor = context.monitor_master;
        } else {
            is_master = false;
            monitor = context.monitor_slave;
        }

        this.input_command = parameters.getChar(FIELD_COMMAND);
        this.input_parameters = parameters;
    }

    @Override
    public void run() {
        synchronized (monitor) {
            if (is_master) {
                if (context.IS_BUSY_MASTER) {
                    Log.e(MainActivity.LOGTAG, "Ending, Master-Thread is busy");
                    return;
                }
                context.IS_BUSY_MASTER = true;
            } else {
                if (context.IS_BUSY_SLAVE) {
                    Log.e(MainActivity.LOGTAG, "Ending, Slave-Thread is busy");
                    return;
                }
                context.IS_BUSY_SLAVE = true;
            }

            Log.d(MainActivity.LOGTAG, "ClientThread: Running " + input_command);
            try {
                if (connect()) {
                    if (input_command == COMMAND_GETSTATE || input_command == COMMAND_LIGHTS_ON || input_command == COMMAND_WAKE_STOP)
                        sendCommandWithoutParameter();
                    if (input_command == COMMAND_SETTIME)
                        setTime();
                    if (input_command == COMMAND_SETDATE)
                        setDate();
                    if (input_command == COMMAND_SET_ALARM_WEEKDAY || input_command == COMMAND_SET_ALARM_WEEKEND)
                        setAlarm();
                    disconnect();
                } else {
                    Log.d(MainActivity.LOGTAG, "Not connected, timeout");
                    showConnectionStatus(false, "Not connected, timeout");
                }
            } catch (Exception e) {
                Log.e(MainActivity.LOGTAG, e.toString());
                showConnectionStatus(false, e.toString());
            }
        }

        if (is_master)
            context.IS_BUSY_MASTER = false;
        else
            context.IS_BUSY_SLAVE = false;
    }

    private void showConnectionStatus(final boolean isConnected, final String errorMessage) {
        context.runOnUiThread(new Runnable() {
            public void run() {
                context.setConnectionfield(is_master, isConnected, errorMessage);
                context.poller.resetPolling();
            }
        });
    }

    private boolean connect() throws Exception {
        SharedPreferences prefs;
        String conf_ip;
        int conf_port;

        if (is_master) {
            Log.d(MainActivity.LOGTAG, "Connecting to master");
            prefs = PreferenceManager
                    .getDefaultSharedPreferences(context);
            conf_ip = prefs.getString(context.getResources().getString(R.string.label_ip_master), context.getResources().getString(R.string.config_default_ip_master));
            conf_port = Integer.parseInt(prefs.getString(context.getResources().getString(R.string.label_port_master), "" + context.getResources().getInteger(R.integer.config_default_port)));
        } else { // Slave
            Log.d(MainActivity.LOGTAG, "Connecting to slave");
            prefs = PreferenceManager
                    .getDefaultSharedPreferences(context);
            conf_ip = prefs.getString(context.getResources().getString(R.string.label_ip_slave), context.getResources().getString(R.string.config_default_ip_slave));
            conf_port = Integer.parseInt(prefs.getString(context.getResources().getString(R.string.label_port_slave), "" + context.getResources().getInteger(R.integer.config_default_port)));
        }
        int conf_timeout = 1000 * Integer.parseInt(prefs.getString(context.getResources().getString(R.string.label_timeout), "" + context.getResources().getInteger(R.integer.config_default_timeout)));

        Log.d(MainActivity.LOGTAG, "Connecting to " + conf_ip + ":" + conf_port + " - " + input_parameters.getChar(FIELD_COMMAND));
        SocketAddress address = new InetSocketAddress(InetAddress.getByName(conf_ip), conf_port);

        try {
            socket = new Socket();
            socket.connect(address, conf_timeout);
            Log.d(MainActivity.LOGTAG, "Socket ist connected: " + socket.isConnected());
            return true;
        } catch (SocketTimeoutException e) {
            Log.d(MainActivity.LOGTAG, "Socket ist not connected, timeout after " + conf_timeout);
            return false;
        }
    }

    public void sendCommandWithoutParameter() throws Exception {
        Log.d(MainActivity.LOGTAG, "ClientThread: Sending simple command " + input_command);
        PrintWriter out = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream())),
                true);
        out.println(input_command);
        readAnswer();
    }

    public void setTime() throws Exception {
        PrintWriter out = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream())),
                true);
        String command = COMMAND_SETTIME + " "
                + input_parameters.getString(FIELD_HOUR) + ":"
                + input_parameters.getString(FIELD_MINUTE) + ":"
                + input_parameters.getString(FIELD_SECOND);
        out.println(command);
        Log.d(MainActivity.LOGTAG, "Command: " + command);
        readAnswer();
    }

    public void setDate() throws Exception {
        PrintWriter out = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream())),
                true);
        String command = COMMAND_SETDATE + " "
                + input_parameters.getString(FIELD_DAY) + "."
                + input_parameters.getString(FIELD_MONTH) + "."
                + input_parameters.getString(FIELD_YEAR) + "-"
                + input_parameters.getInt(FIELD_WEEKDAY);
        out.println(command);
        Log.d(MainActivity.LOGTAG, "Command: " + command);
        readAnswer();
    }

    public void setAlarm() throws Exception { // Weekend or Weekday
        PrintWriter out = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream())),
                true);
        String command = input_command + " "
                + input_parameters.getString(FIELD_HOUR) + ":"
                + input_parameters.getString(FIELD_MINUTE) + ":"
                + input_parameters.getString(FIELD_SECOND);
        out.println(command);
        Log.d(MainActivity.LOGTAG, "Command: " + command);
        readAnswer();
    }

    private void readAnswer() throws Exception {
        Log.d(MainActivity.LOGTAG, "ClientThread: Reading answer. Master: " + is_master);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        final String answer = in.readLine();

        // TIME-DATE-WAKETIMEWEEKDAY-WAKETIMEWEEKEND-WAKESTATE-BUTTONSTATE
        // Example: 19:38:11-8:3:15:7-5:50:22-8:46:2-0-1
        context.runOnUiThread(new Runnable() {
            public void run() {
                String values[] = answer.split("-");
                Log.d(MainActivity.LOGTAG, "ClientThread: Answer: " + answer);

                // Show complete answer string
                context.setStatefield(is_master, answer);
                boolean temp_switch_state = false;

                // Initialize integers
                int temp_hour, temp_minute, temp_second, temp_day, temp_month, temp_year, temp_weekday, temp_wake_weekday_hour, temp_wake_weekday_minute, temp_wake_weekend_hour, temp_wake_weekend_minute, temp_wake_state;
                temp_hour = temp_minute = temp_second = temp_day = temp_month = temp_year = temp_weekday = temp_wake_weekday_hour = temp_wake_weekday_minute = temp_wake_weekend_hour = temp_wake_weekend_minute = temp_wake_state = 0;

                try {
                    // Set current time...
                    String received_time[] = values[0].split(":");
                    temp_hour = Integer.parseInt(received_time[0]);
                    temp_minute = Integer.parseInt(received_time[1]);
                    temp_second = Integer.parseInt(received_time[2]);

                    // ...and date
                    String received_date[] = values[1].split(":");
                    temp_day = Integer.parseInt(received_date[0]);
                    temp_month = Integer.parseInt(received_date[1]);
                    temp_year = Integer.parseInt(received_date[2]);
                    temp_weekday = Integer.parseInt(received_date[3]);

                    // Set waketime weekday
                    String received_waketime_weekday[] = values[2]
                            .split(":");
                    temp_wake_weekday_hour = Integer.parseInt(received_waketime_weekday[0]);
                    temp_wake_weekday_minute = Integer.parseInt(received_waketime_weekday[1]);

                    // Set waketime weekend
                    String received_waketime_weekend[] = values[3]
                            .split(":");
                    temp_wake_weekend_hour = Integer.parseInt(received_waketime_weekend[0]);
                    temp_wake_weekend_minute = Integer.parseInt(received_waketime_weekend[1]);

                    // Set wake state (0,1,2)
                    temp_wake_state = 0;
                    temp_wake_state = Integer.parseInt(values[4]);

                    // Set switch state (0 or 1)
                    temp_switch_state = true;
                    if (values[5].equals("0"))
                        temp_switch_state = false;

                    context.startTimer(); // Finally start the timer
                    showConnectionStatus(true, "Response correct");
                } catch (Exception e) {
                    Log.e(MainActivity.LOGTAG,
                            "connectThread: Error parsing: "
                                    + answer + ": " + e.toString());
                    showConnectionStatus(false, "Error parsing" + e.toString());
                }

                if (is_master) { // Master
                    context.hour = temp_hour;
                    context.minute = temp_minute;
                    context.second = temp_second;
                    context.day = temp_day;
                    context.month = temp_month;
                    context.year = temp_year;
                    context.weekday = temp_weekday;
                    context.wake_weekday_hour = temp_wake_weekday_hour;
                    context.wake_weekday_minute = temp_wake_weekday_minute;
                    context.wake_weekend_hour = temp_wake_weekend_hour;
                    context.wake_weekend_minute = temp_wake_weekend_minute;
                } else // Slave
                {
                    context.hour2 = temp_hour;
                    context.minute2 = temp_minute;
                    context.second2 = temp_second;
                    context.day2 = temp_day;
                    context.month2 = temp_month;
                    context.year2 = temp_year;
                    context.weekday2 = temp_weekday;
                    context.wake_weekday_hour2 = temp_wake_weekday_hour;
                    context.wake_weekday_minute2 = temp_wake_weekday_minute;
                    context.wake_weekend_hour2 = temp_wake_weekend_hour;
                    context.wake_weekend_minute2 = temp_wake_weekend_minute;
                }
                context.setSwitchState(is_master, temp_switch_state);
                context.setWakeState(is_master, temp_wake_state);
            }
        });
    }

    private void disconnect() throws Exception {
        Log.d(MainActivity.LOGTAG, "Disconnecting " + input_command);
        socket.close();
    }
}