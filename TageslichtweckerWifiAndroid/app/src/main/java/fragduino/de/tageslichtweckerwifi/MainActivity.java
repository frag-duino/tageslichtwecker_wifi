package fragduino.de.tageslichtweckerwifi;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.ToggleButton;

import java.util.Calendar;


public class MainActivity extends ActionBarActivity {
    public static String LOGTAG = "FRAGDUINO";
    public static String DAYS[] = {"NODAY", "Monday", "Tuesday", "Wednesday",
            "Thursday", "Friday", "Saturday", "Sunday"};

    // Variables
    private TextView text_currenttime, text_currentdate,
            text_waketime_weekday, text_waketime_weekend, text_state, text_alarmidentifier, text_connection, text_wakestate, text_currenttime2, text_currentdate2,
            text_waketime_weekday2, text_waketime_weekend2, text_state2, text_alarmidentifier2, text_connection2, text_wakestate2;
    public ToggleButton bt_snooze, bt_snooze2;
    private TimeThread timer;
    public PollingThread poller;
    int hour, minute, second, year, month, day, weekday, wake_weekday_hour,
            wake_weekday_minute, wake_weekend_hour, wake_weekend_minute,
            hour2, minute2, second2, year2, month2, day2, weekday2, wake_weekday_hour2,
            wake_weekday_minute2, wake_weekend_hour2, wake_weekend_minute2;
    private LinearLayout layout_debug, layout_debug2, layout_alarm2;
    private MainActivity context;
    public boolean mode_debug = false;
    private boolean isConnected = false;
    private boolean isConnected2 = false;
    public int polling_interval; // Update interval
    boolean IS_BUSY_MASTER = false; // Only one thread per alarm is allowed
    boolean IS_BUSY_SLAVE = false; // Only one thread per alarm is allowed
    boolean enable_masterslave = false; // Enable second alarm

    // Monitors
    public String monitor_master = "Master";
    public String monitor_slave = "Slave";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.context = this;

        // Get UI-Elements Alarm 1
        text_alarmidentifier = (TextView) findViewById(R.id.text_alarmidentifier);
        text_currenttime = (TextView) findViewById(R.id.text_currenttime);
        text_currentdate = (TextView) findViewById(R.id.text_currentdate);
        text_waketime_weekday = (TextView) findViewById(R.id.text_waketime_weekday);
        text_waketime_weekend = (TextView) findViewById(R.id.text_waketime_weekend);
        text_state = (TextView) findViewById(R.id.text_state);
        text_connection = (TextView) findViewById(R.id.text_connection);
        text_wakestate = (TextView) findViewById(R.id.text_wakestate);
        bt_snooze = (ToggleButton) findViewById(R.id.bt_snooze);

        // Get UI-Elements Alarm 2
        text_alarmidentifier2 = (TextView) findViewById(R.id.text_alarmidentifier2);
        text_currenttime2 = (TextView) findViewById(R.id.text_currenttime2);
        text_currentdate2 = (TextView) findViewById(R.id.text_currentdate2);
        text_waketime_weekday2 = (TextView) findViewById(R.id.text_waketime_weekday2);
        text_waketime_weekend2 = (TextView) findViewById(R.id.text_waketime_weekend2);
        text_state2 = (TextView) findViewById(R.id.text_state2);
        text_connection2 = (TextView) findViewById(R.id.text_connection2);
        text_wakestate2 = (TextView) findViewById(R.id.text_wakestate2);
        bt_snooze2 = (ToggleButton) findViewById(R.id.bt_snooze2);

        // Layouts
        layout_debug = (LinearLayout) findViewById(R.id.layout_debug);
        layout_debug2 = (LinearLayout) findViewById(R.id.layout_debug2);
        layout_alarm2 = (LinearLayout) findViewById(R.id.layout_alarm2);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOGTAG, "MainActivity: OnResume");
        timer = new TimeThread(this);
        poller = new PollingThread(this);

        // Get preferences
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        text_alarmidentifier.setText(prefs.getString(getResources().getString(R.string.label_alarmidentifier), getResources().getString(R.string.config_default_alarmidentifier)));
        text_alarmidentifier2.setText(prefs.getString(getResources().getString(R.string.label_alarmidentifier2), getResources().getString(R.string.config_default_alarmidentifier2)));
        polling_interval = Integer.parseInt(prefs.getString(context.getResources().getString(R.string.label_updateinterval), "" + context.getResources().getInteger(R.integer.config_default_interval)));
        enable_masterslave = prefs.getBoolean(getResources().getString(R.string.label_enableslave), false);

        if (enable_masterslave)
            layout_alarm2.setVisibility(View.VISIBLE);
        else
            layout_alarm2.setVisibility(View.INVISIBLE);

        // Debug mode
        mode_debug = prefs.getBoolean(getResources().getString(R.string.label_debug), false);
        if (mode_debug) {
            layout_debug.setVisibility(View.VISIBLE);
            layout_debug2.setVisibility(View.VISIBLE);
        } else {
            layout_debug.setVisibility(View.INVISIBLE);
            layout_debug2.setVisibility(View.INVISIBLE);
        }

        // Connect on startup
        if (prefs.getBoolean(getResources().getString(R.string.label_connectOnStartup), true)) {
            click_refresh(null); // Start connection
            Log.d(LOGTAG, "MainActivity: Autoconnect");
        } else Log.d(LOGTAG, "MainActivity: No autoconnect");

        poller.start(); // Start polling-Thread
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(LOGTAG, "mainActivity: onPause");
        timer.running = false;
        poller.running = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this,
                    SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void click_refresh(View v) {
        Log.d(LOGTAG, "Click: Refresh");
        Bundle b = new Bundle();
        b.putChar(ClientThread.FIELD_COMMAND, ClientThread.COMMAND_GETSTATE);
        new Thread(new ClientThread(this, ClientThread.ALARM_MASTER, b)).start();
        if (enable_masterslave)
            new Thread(new ClientThread(this, ClientThread.ALARM_SLAVE, b)).start();
    }

    public void click_reset(View v) {
        Log.d(LOGTAG, "Click: Reset");
        Bundle b = new Bundle();
        b.putChar(ClientThread.FIELD_COMMAND, ClientThread.COMMAND_RESET);
        if (v.getId() == R.id.bt_reset)
            new Thread(new ClientThread(this, ClientThread.ALARM_MASTER, b)).start();
        else
            new Thread(new ClientThread(this, ClientThread.ALARM_SLAVE, b)).start();
    }

    public void click_refreshtime(View v) {
        Log.d(LOGTAG, "Click: Refresh Time");
        Bundle b = new Bundle();
        b.putChar(ClientThread.FIELD_COMMAND, ClientThread.COMMAND_REFRESH_TIME);
        if (v.getId() == R.id.bt_reset)
            new Thread(new ClientThread(this, ClientThread.ALARM_MASTER, b)).start();
        else
            new Thread(new ClientThread(this, ClientThread.ALARM_SLAVE, b)).start();
    }

    public void click_light(View v) {
        if (v.getId() == R.id.bt_snooze && isConnected || v.getId() == R.id.bt_snooze2 && isConnected2) {
            Bundle b = new Bundle(); // CreateBundle
            if (!bt_snooze.isChecked())
                b.putChar(ClientThread.FIELD_COMMAND, ClientThread.COMMAND_WAKE_STOP);
            else
                b.putChar(ClientThread.FIELD_COMMAND, ClientThread.COMMAND_LIGHTS_ON);
            new Thread(new ClientThread(context, ClientThread.ALARM_MASTER, b)).start(); // Send command to master
            if (enable_masterslave)
                new Thread(new ClientThread(context, ClientThread.ALARM_SLAVE, b)).start(); // Send command to slave
        }
    }

    // Start the timer, if its not already started
    public void startTimer() {
        if (!timer.running) {
            timer = new TimeThread(this);
            timer.running = true;
            timer.start();
        }
    }

    public void setStatefield(boolean is_master, String input) {
        if (is_master)
            text_state.setText("State: " + input);
        else
            text_state2.setText("State: " + input);
    }

    public void setConnectionfield(boolean is_master, boolean isConnected, String errorMessage) {
        if (is_master) {
            if (isConnected) {
                bt_snooze.setVisibility(View.VISIBLE);
                text_wakestate.setVisibility(View.VISIBLE);
            } else {
                text_alarmidentifier.setTextColor(Color.RED);
                text_currenttime.setText("");
                text_currentdate.setText(getResources().getString(R.string.label_loading));
                text_waketime_weekday.setText("");
                text_waketime_weekend.setText("");
                text_wakestate.setText("");
                bt_snooze.setVisibility(View.INVISIBLE);
            }

            this.isConnected = isConnected;
            text_connection.setText("Connection: " + errorMessage);
        } else { // Slave
            if (isConnected) {
                bt_snooze2.setVisibility(View.VISIBLE);
                text_wakestate2.setVisibility(View.VISIBLE);
            } else {
                text_alarmidentifier2.setTextColor(Color.RED);
                text_currenttime2.setText("");
                text_currentdate2.setText(getResources().getString(R.string.label_loading));
                text_waketime_weekday2.setText("");
                text_waketime_weekend2.setText("");
                text_wakestate2.setText("");
                bt_snooze2.setVisibility(View.INVISIBLE);
            }

            this.isConnected2 = isConnected;
            text_connection2.setText("Connection: " + errorMessage);
        }
    }

    public void setWakeState(boolean is_master, int wakeState) {
        if (is_master) {
            if (wakeState == ClientThread.STATE_WAITING)
                bt_snooze.setChecked(false);
            else
                bt_snooze.setChecked(true);
        } else {
            if (wakeState == ClientThread.STATE_WAITING)
                bt_snooze2.setChecked(false);
            else
                bt_snooze2.setChecked(true);
        }
    }

    public void setSwitchState(boolean is_master, boolean state) {
        if (is_master) {
            if (state) { // Alarm is activated
                text_wakestate.setText(getResources().getString(R.string.label_alarm_enabled));
                text_wakestate.setTextColor(Color.GREEN);
            } else {
                text_wakestate.setText(getResources().getString(R.string.label_alarm_disabled));
                text_wakestate.setTextColor(Color.RED);
            }
        } else { // Slave
            if (state) { // Alarm is activated
                text_wakestate2.setText(getResources().getString(R.string.label_alarm_enabled));
                text_wakestate2.setTextColor(Color.GREEN);
            } else {
                text_wakestate2.setText(getResources().getString(R.string.label_alarm_disabled));
                text_wakestate2.setTextColor(Color.RED);
            }
        }
    }

    public void displayTimeAndDateAndWake() {
        if (mode_debug)
            Log.d(LOGTAG, "MainActivity: displayTimeAndDateAndWake");
        String temp_hour, temp_minute, temp_second, temp_day, temp_month, temp_weekday, temp_year;

        if (isConnected) { // Alarm 1
            // Time Alarm 1
            temp_hour = "" + hour;
            temp_minute = "" + minute;
            temp_second = "" + second;
            if (hour < 10)
                temp_hour = "0" + hour;
            if (minute < 10)
                temp_minute = "0" + minute;
            if (second < 10)
                temp_second = "0" + second;
            text_currenttime.setText(temp_hour + ":" + temp_minute + ":"
                    + temp_second);
            text_alarmidentifier.setTextColor(Color.GREEN);

            // Date Alarm 1
            temp_day = "" + day;
            temp_month = "" + month;
            temp_weekday = DAYS[weekday];
            temp_year = "" + year;
            if (year < 10)
                temp_year = "0" + year;
            text_currentdate.setText(temp_day + "." + temp_month + ".20"
                    + temp_year + " (" + temp_weekday + ")");

            // Waketime Weekday Alarm 1
            temp_hour = "" + wake_weekday_hour;
            temp_minute = "" + wake_weekday_minute;
            if (wake_weekday_hour < 10)
                temp_hour = "0" + wake_weekday_hour;
            if (wake_weekday_minute < 10)
                temp_minute = "0" + wake_weekday_minute;
            text_waketime_weekday.setText(getResources().getString(R.string.label_weekday) + temp_hour + ":"
                    + temp_minute);

            // Waketime Weekend Alarm 1
            temp_hour = "" + wake_weekend_hour;
            temp_minute = "" + wake_weekend_minute;
            if (wake_weekend_hour < 10)
                temp_hour = "0" + wake_weekend_hour;
            if (wake_weekend_minute < 10)
                temp_minute = "0" + wake_weekend_minute;
            text_waketime_weekend.setText(getResources().getString(R.string.label_weekend) + temp_hour + ":"
                    + temp_minute);
        }

        if (isConnected2) { // Alarm 2
            // Time Alarm 2
            temp_hour = "" + hour2;
            temp_minute = "" + minute2;
            temp_second = "" + second2;
            if (hour2 < 10)
                temp_hour = "0" + hour2;
            if (minute2 < 10)
                temp_minute = "0" + minute2;
            if (second2 < 10)
                temp_second = "0" + second2;
            text_currenttime2.setText(temp_hour + ":" + temp_minute + ":"
                    + temp_second);
            text_alarmidentifier2.setTextColor(Color.GREEN);

            // Date Alarm 2
            temp_day = "" + day2;
            temp_month = "" + month2;
            temp_weekday = DAYS[weekday2];
            temp_year = "" + year2;
            if (year2 < 10)
                temp_year = "0" + year;
            text_currentdate2.setText(temp_day + "." + temp_month + ".20"
                    + temp_year + " (" + temp_weekday + ")");

            // Waketime Weekday Alarm 2
            temp_hour = "" + wake_weekday_hour2;
            temp_minute = "" + wake_weekday_minute2;
            if (wake_weekday_hour2 < 10)
                temp_hour = "0" + wake_weekday_hour2;
            if (wake_weekday_minute2 < 10)
                temp_minute = "0" + wake_weekday_minute2;
            text_waketime_weekday2.setText(getResources().getString(R.string.label_weekday) + temp_hour + ":"
                    + temp_minute);

            // Waketime Weekend Alarm 2
            temp_hour = "" + wake_weekend_hour2;
            temp_minute = "" + wake_weekend_minute2;
            if (wake_weekend_hour2 < 10)
                temp_hour = "0" + wake_weekend_hour2;
            if (wake_weekend_minute2 < 10)
                temp_minute = "0" + wake_weekend_minute2;
            text_waketime_weekend2.setText(getResources().getString(R.string.label_weekend) + temp_hour + ":"
                    + temp_minute);
        }
    }

    public void click_settime(View v) {
        if (v.getId() == R.id.text_currenttime && isConnected || v.getId() == R.id.text_currenttime2 && isConnected2) {
            Calendar c = Calendar.getInstance();
            int now_hour = c.get(Calendar.HOUR_OF_DAY);
            int now_minute = c.get(Calendar.MINUTE);
            TimePickerDialog tpd = new TimePickerDialog(this,
                    new TimePickerDialog.OnTimeSetListener() {
                        @Override
                        public void onTimeSet(TimePicker view, int hourOfDay,
                                              int minuteOfDay) {
                            hour = hourOfDay;
                            minute = minuteOfDay;

                            String temp_hour = "" + hour;
                            String temp_minute = "" + minute;
                            if (hour < 10)
                                temp_hour = "0" + hour;
                            if (minute < 10)
                                temp_minute = "0" + minute;
                            second = 0;

                            Bundle b = new Bundle();
                            b.putChar(ClientThread.FIELD_COMMAND, ClientThread.COMMAND_SETTIME);
                            b.putString(ClientThread.FIELD_HOUR, temp_hour);
                            b.putString(ClientThread.FIELD_MINUTE, temp_minute);
                            b.putString(ClientThread.FIELD_SECOND, "00");
                            new Thread(new ClientThread(context, ClientThread.ALARM_MASTER, b)).start();

                            set_not_acknowledged();

                            if (enable_masterslave)  // Set Time Color
                                new Thread(new ClientThread(context, ClientThread.ALARM_SLAVE, b)).start();
                            }
                    }, now_hour, now_minute, true);
            tpd.show();
        }
    }

    public void click_setdate(View v) {
        if (v.getId() == R.id.text_currentdate && isConnected || v.getId() == R.id.text_currentdate2 && isConnected2) {
            Calendar c = Calendar.getInstance();
            int now_day = c.get(Calendar.DAY_OF_MONTH);
            int now_month = c.get(Calendar.MONTH);
            int now_year = c.get(Calendar.YEAR);

            DatePickerDialog tpd = new DatePickerDialog(this,
                    new DatePickerDialog.OnDateSetListener() {
                        @Override
                        public void onDateSet(DatePicker view, int new_year,
                                              int new_month, int new_day) {
                            year = Integer.parseInt(("" + new_year).substring(2));
                            month = new_month + 1; // Januar=0;
                            day = new_day;

                            String temp_month = "" + month;
                            String temp_day = "" + day;
                            String temp_year = "" + year;
                            if (month < 10)
                                temp_month = "0" + month;
                            if (day < 10)
                                temp_day = "0" + day;
                            if (year < 10)
                                temp_year = "0" + year;

                            Calendar c = Calendar.getInstance();
                            c.set(new_year, month - 1, day, 1, 1, 1);
                            weekday = c.get(Calendar.DAY_OF_WEEK) - 1; // monday=1
                            if (weekday == 0) weekday = 7; // Sunday

                            Log.d(LOGTAG, "MainActivity: Setdate new date: " + temp_day + "."
                                    + temp_month + "." + temp_year + "-" + weekday);

                            Bundle b = new Bundle();
                            b.putChar(ClientThread.FIELD_COMMAND, ClientThread.COMMAND_SETDATE);
                            b.putString(ClientThread.FIELD_DAY, temp_day);
                            b.putString(ClientThread.FIELD_MONTH, temp_month);
                            b.putString(ClientThread.FIELD_YEAR, temp_year);
                            b.putInt(ClientThread.FIELD_WEEKDAY, weekday);
                            new Thread(new ClientThread(context, ClientThread.ALARM_MASTER, b)).start();

                            set_not_acknowledged();

                            if (enable_masterslave)
                                new Thread(new ClientThread(context, ClientThread.ALARM_SLAVE, b)).start();
                        }
                    }, now_year, now_month, now_day);
            tpd.show();
        }
    }

    public void click_waketime_weekday(View v) {
        if (v.getId() == R.id.text_waketime_weekday && isConnected || v.getId() == R.id.text_waketime_weekday2 && isConnected2) {
            TimePickerDialog tpd = new TimePickerDialog(this,
                    new TimePickerDialog.OnTimeSetListener() {
                        @Override
                        public void onTimeSet(TimePicker view, int hourOfDay,
                                              int minuteOfDay) {
                            wake_weekday_hour = hourOfDay;
                            wake_weekday_minute = minuteOfDay;
                            String temp_hour = "" + wake_weekday_hour;
                            String temp_minute = "" + wake_weekday_minute;
                            if (wake_weekday_hour < 10)
                                temp_hour = "0" + wake_weekday_hour;
                            if (wake_weekday_minute < 10)
                                temp_minute = "0" + wake_weekday_minute;

                            Bundle b = new Bundle();
                            b.putChar(ClientThread.FIELD_COMMAND, ClientThread.COMMAND_SET_ALARM_WEEKDAY);
                            b.putString(ClientThread.FIELD_HOUR, temp_hour);
                            b.putString(ClientThread.FIELD_MINUTE, temp_minute);
                            b.putString(ClientThread.FIELD_SECOND, "00");
                            new Thread(new ClientThread(context, ClientThread.ALARM_MASTER, b)).start();

                            set_not_acknowledged();

                            if (enable_masterslave)
                                new Thread(new ClientThread(context, ClientThread.ALARM_SLAVE, b)).start();
                        }
                    }, wake_weekday_hour, wake_weekday_minute, true);
            tpd.show();
        }
    }


    public void click_waketime_weekend(View v) {
        if (v.getId() == R.id.text_waketime_weekend && isConnected || v.getId() == R.id.text_waketime_weekend2 && isConnected2) {
            TimePickerDialog tpd = new TimePickerDialog(this,
                    new TimePickerDialog.OnTimeSetListener() {
                        @Override
                        public void onTimeSet(TimePicker view, int hourOfDay,
                                              int minuteOfDay) {
                            wake_weekend_hour = hourOfDay;
                            wake_weekend_minute = minuteOfDay;
                            String temp_hour = "" + wake_weekend_hour;
                            String temp_minute = "" + wake_weekend_minute;
                            if (wake_weekend_hour < 10)
                                temp_hour = "0" + wake_weekend_hour;
                            if (wake_weekend_minute < 10)
                                temp_minute = "0" + wake_weekend_minute;

                            Bundle b = new Bundle();
                            b.putChar(ClientThread.FIELD_COMMAND, ClientThread.COMMAND_SET_ALARM_WEEKEND);
                            b.putString(ClientThread.FIELD_HOUR, temp_hour);
                            b.putString(ClientThread.FIELD_MINUTE, temp_minute);
                            b.putString(ClientThread.FIELD_SECOND, "00");
                            new Thread(new ClientThread(context, ClientThread.ALARM_MASTER, b)).start();

                            set_not_acknowledged();

                            if (enable_masterslave)
                                new Thread(new ClientThread(context, ClientThread.ALARM_SLAVE, b)).start();
                        }
                    }, wake_weekend_hour, wake_weekend_minute, true);
            tpd.show();
        }
    }

    // Sets the color of the identifier to not-acknowledged
    private void set_not_acknowledged(){
        // Setting color to not-acknowledged
        text_alarmidentifier.setTextColor(Color.YELLOW);
        text_alarmidentifier2.setTextColor(Color.YELLOW);
    }
}