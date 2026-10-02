package in.atulpatare.ranobem.utils;

import android.content.Context;
import android.content.DialogInterface;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import in.atulpatare.core.models.Metadata;
import in.atulpatare.core.sources.SourceManager;
import in.atulpatare.ranobem.R;

/**
 * Sources can be switched off when they stop working, e.g. MangaFire after its site changed. Series
 * saved from them stay in the library and history, these keep the app from requesting them.
 */
public final class SourceAccess {
    private SourceAccess() {
    }

    public static boolean available(int sourceId) {
        return Boolean.TRUE.equals(SourceManager.getSource(sourceId).meta().isEnabled);
    }

    /**
     * Explains that the series can't be loaded, {@code onClose} runs however the dialog goes away.
     */
    public static void showUnavailable(Context context, int sourceId, @Nullable DialogInterface.OnDismissListener onClose) {
        Metadata meta = SourceManager.getSource(sourceId).meta();
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.source_unavailable_title, meta.name))
                .setMessage(context.getString(R.string.source_unavailable_message, meta.name))
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener(onClose)
                .show();
    }
}
