package fragduino.de.tageslichtweckerwifi;

import android.util.Log;

/**
 * Created by Marcel on 10.03.2015.
 */
public class TimeThread extends Thread {
    MainActivity context;
    boolean running = false;

    public TimeThread(MainActivity context) {
        this.context = context;
    }

    @Override
    public void run() {
        super.run();
        Log.d(MainActivity.LOGTAG, "TimeThread: Running ");

        while (running) {
            context.second++;
            context.second2++;

            // Alarm 1
            if (context.second == 60) {
                context.second = 0;
                context.minute++;
            }
            if (context.minute == 60) {
                context.minute = 0;
                context.hour++;
            }
            if (context.hour == 24) {
                context.hour = 0;
            }

            // Alarm 2
            if (context.second2 == 60) {
                context.second2 = 0;
                context.minute2++;
            }
            if (context.minute2 == 60) {
                context.minute2 = 0;
                context.hour2++;
            }
            if (context.hour2 == 24) {
                context.hour2 = 0;
            }

            context.runOnUiThread(new Runnable() {
                public void run() {
                    context.displayTimeAndDateAndWake();
                }
            });
            try {
                sleep(1000);
            } catch (Exception e) {
            }
        }
        Log.d(MainActivity.LOGTAG, "TimeThread: Finished");
    }
}
