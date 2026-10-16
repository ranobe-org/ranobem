package in.atulpatare.ranobem.ui.library;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.library.Library;
import in.atulpatare.ranobem.tracking.TrackStatus;
import in.atulpatare.ranobem.ui.tracking.TrackSheet;

/**
 * Picks the shelf a library series sits on, or takes it out of the library. A plain single choice
 * dialog, so TalkBack and switch access read it as a list of options.
 */
public final class LibraryStatusDialog {

    private LibraryStatusDialog() {
    }

    /**
     * @param anchor   where the confirmation shows
     * @param onRemove removes the series, so the caller can offer an undo
     */
    public static void show(Context context, View anchor, Manga manga, @Nullable String current, Runnable onRemove) {
        String[] statuses = TrackStatus.ALL;
        String[] labels = new String[statuses.length];
        int checked = -1;
        for (int i = 0; i < statuses.length; i++) {
            labels[i] = TrackSheet.statusLabel(context, statuses[i]);
            if (statuses[i].equals(current)) checked = i;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(manga.name)
                .setSingleChoiceItems(labels, checked, (dialog, i) -> {
                    dialog.dismiss();
                    if (statuses[i].equals(current)) return;
                    Library.setStatus(manga.id, statuses[i]);
                    Snackbar.make(anchor, context.getString(R.string.library_moved_to, labels[i]), Snackbar.LENGTH_SHORT).show();
                })
                .setNeutralButton(R.string.library_remove, (dialog, i) -> onRemove.run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
