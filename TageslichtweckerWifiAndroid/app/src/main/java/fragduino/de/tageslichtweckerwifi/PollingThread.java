package fragduino.de.tageslichtweckerwifi;

import android.util.Log;

/**
 * Created by Marcel on 10.03.2015.
 */
public class PollingThread extends Thread {
    MainActivity context;
    boolean running = true;
    int countdown = 0;

    public PollingThread(MainActivity context) {
        this.context = context;
    }

    @Override
    public void run() {
        super.run();
        countdown = context.polling_interval;
        Log.d(MainActivity.LOGTAG, "PollingThread: Running at " + countdown);

        while (running) {
            try {
                if (context.polling_interval == 0)
                    break;
                sleep(1000); // Sleep a second
            } catch (Exception e) {
            }

            if (countdown-- == 0) {
                countdown = context.polling_interval;

                if (running)
                    context.click_refresh(null);
                else
                    break;
            }
        }
        Log.d(MainActivity.LOGTAG, "PollingThread: Finished");
    }

    public void resetPolling(){
        countdown = context.polling_interval;
    }
}
