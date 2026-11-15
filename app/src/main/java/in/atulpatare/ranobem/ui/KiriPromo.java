package in.atulpatare.ranobem.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import in.atulpatare.ranobem.R;

/**
 * Introduces Kiri, the new tracker app. Shown once at launch, and again from the Settings card.
 */
public final class KiriPromo {

    private static final String PREFS = "kiri_promo";
    private static final String KEY_SEEN = "seen";
    private static final String PACKAGE = "in.atulpatare.kiri";
    private static final String SITE = "https://kiri.atulpatare.in";

    private KiriPromo() {
    }

    public static void showOnce(FragmentActivity activity) {
        var prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SEEN, false)) return;
        prefs.edit().putBoolean(KEY_SEEN, true).apply();
        show(activity);
    }

    public static void show(FragmentActivity activity) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.sheet_kiri_promo, null, false);
        view.findViewById(R.id.kiri_play).setOnClickListener(v -> {
            openPlayStore(activity);
            dialog.dismiss();
        });
        view.findViewById(R.id.kiri_site).setOnClickListener(v -> {
            open(activity, SITE);
            dialog.dismiss();
        });
        view.findViewById(R.id.kiri_later).setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(view);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.show();
    }

    private static void openPlayStore(Context context) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + PACKAGE)));
        } catch (ActivityNotFoundException e) {
            open(context, "https://play.google.com/store/apps/details?id=" + PACKAGE);
        }
    }

    private static void open(Context context, String url) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException ignored) {
        }
    }
}
