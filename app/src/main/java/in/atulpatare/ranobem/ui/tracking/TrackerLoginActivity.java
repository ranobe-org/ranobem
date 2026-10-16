package in.atulpatare.ranobem.ui.tracking;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.tracking.Tracker;
import in.atulpatare.ranobem.tracking.Trackers;
import in.atulpatare.ranobem.tracking.Tracking;

/**
 * Catches a tracker's OAuth redirect, ranobem://<tracker>-auth..., hands it to that tracker to
 * store the token, and goes back to the app where the user left it.
 */
public class TrackerLoginActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri data = getIntent().getData();
        Tracker tracker = Trackers.forLoginHost(data != null ? data.getHost() : null);

        if (tracker != null && tracker.handleLogin(data)) {
            Tracking.syncPending();
            Toast.makeText(this, getString(R.string.tracker_logged_in_to, tracker.name), Toast.LENGTH_SHORT).show();
        } else {
            String name = tracker != null ? tracker.name : getString(R.string.settings_tracking);
            Toast.makeText(this, getString(R.string.tracker_login_failed, name), Toast.LENGTH_LONG).show();
        }

        // like tapping the app icon: brings the running app back as it was, or starts it
        Intent app = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (app != null) startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
}
