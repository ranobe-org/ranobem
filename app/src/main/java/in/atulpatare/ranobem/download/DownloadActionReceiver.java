package in.atulpatare.ranobem.download;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Pause, resume and cancel buttons on the download notifications.
 */
public class DownloadActionReceiver extends BroadcastReceiver {
    static final String ACTION_PAUSE = "in.atulpatare.ranobem.download.PAUSE";
    static final String ACTION_RESUME = "in.atulpatare.ranobem.download.RESUME";
    static final String ACTION_CANCEL = "in.atulpatare.ranobem.download.CANCEL";
    static final String EXTRA_JOB_ID = "job_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra(EXTRA_JOB_ID);
        String action = intent.getAction();
        if (id == null || action == null) return;
        DownloadQueue queue = DownloadQueue.get(context);
        switch (action) {
            case ACTION_PAUSE:
                queue.pause(id);
                break;
            case ACTION_RESUME:
                queue.resume(id);
                break;
            case ACTION_CANCEL:
                queue.cancel(id);
                break;
        }
    }
}
