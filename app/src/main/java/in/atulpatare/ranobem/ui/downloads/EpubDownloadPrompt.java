package in.atulpatare.ranobem.ui.downloads;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.databinding.DialogEpubDownloadBinding;
import in.atulpatare.ranobem.download.DownloadQueue;
import in.atulpatare.ranobem.utils.NotificationAccess;

/**
 * Starts an EPUB download: free users are pointed to Pro, Pro users pick the chapters and grant
 * what the download needs. Create it in {@code onCreate}, it registers for permission results.
 */
public class EpubDownloadPrompt {
    private final AppCompatActivity activity;
    private final View anchor;
    private final ActivityResultLauncher<String[]> permissions;
    private Manga pendingManga;
    private float pendingFrom;
    private float pendingTo;

    public EpubDownloadPrompt(AppCompatActivity activity, View anchor) {
        this.activity = activity;
        this.anchor = anchor;
        // the download runs either way, without these it just can't show progress or save to Downloads
        this.permissions = activity.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                result -> startPending());
    }

    public void show(Manga manga) {
        if (Config.isFree()) {
            showProUpsell();
            return;
        }
        DialogEpubDownloadBinding binding = DialogEpubDownloadBinding.inflate(activity.getLayoutInflater());
        binding.scope.setOnCheckedChangeListener((group, checked) ->
                binding.range.setVisibility(checked == R.id.scope_range ? View.VISIBLE : View.GONE));

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.download_dialog_title)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.download_start, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        // validate before closing, so a bad range can be fixed in place
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            float from = Float.NaN;
            float to = Float.NaN;
            if (binding.scope.getCheckedRadioButtonId() == R.id.scope_range) {
                from = parse(binding.from.getText());
                to = parse(binding.to.getText());
                if (!Float.isNaN(from) && !Float.isNaN(to) && from > to) {
                    binding.toLayout.setError(activity.getString(R.string.download_invalid_range));
                    return;
                }
            }
            dialog.dismiss();
            start(manga, from, to);
        }));
        dialog.show();
    }

    private void showProUpsell() {
        new MaterialAlertDialogBuilder(activity)
                .setIcon(R.drawable.ic_download)
                .setTitle(R.string.download_pro_title)
                .setMessage(R.string.download_pro_message)
                .setPositiveButton(R.string.download_get_pro, (d, w) ->
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Config.PRO_LINK))))
                .setNegativeButton(R.string.download_not_now, null)
                .show();
    }

    private void start(Manga manga, float from, float to) {
        pendingManga = manga;
        pendingFrom = from;
        pendingTo = to;
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) missing.add(Manifest.permission.POST_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) missing.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        for (int i = missing.size() - 1; i >= 0; i--) {
            if (ContextCompat.checkSelfPermission(activity, missing.get(i)) == PackageManager.PERMISSION_GRANTED) missing.remove(i);
        }
        if (missing.isEmpty()) {
            startPending();
        } else {
            permissions.launch(missing.toArray(new String[0]));
        }
    }

    private void startPending() {
        if (pendingManga == null) return;
        DownloadQueue.get(activity).enqueue(pendingManga, pendingFrom, pendingTo);
        pendingManga = null;
        if (!NotificationAccess.enabled(activity)) {
            // it still runs, but its progress and result only show in the app
            Snackbar.make(anchor, R.string.download_started_no_notifications, Snackbar.LENGTH_LONG)
                    .setAction(R.string.open_settings, v -> NotificationAccess.openSettings(activity))
                    .show();
            return;
        }
        Snackbar.make(anchor, R.string.download_started, Snackbar.LENGTH_LONG)
                .setAction(R.string.download_view, v -> activity.startActivity(new Intent(activity, DownloadsActivity.class)))
                .show();
    }

    private static float parse(CharSequence text) {
        if (text == null || text.toString().trim().isEmpty()) return Float.NaN;
        try {
            return Float.parseFloat(text.toString().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }
}
