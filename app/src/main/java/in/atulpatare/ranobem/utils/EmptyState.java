package in.atulpatare.ranobem.utils;

import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import in.atulpatare.ranobem.databinding.ViewEmptyStateBinding;

/**
 * Fills in and shows/hides the shared empty state (view_empty_state.xml).
 */
public class EmptyState {
    private EmptyState() {
    }

    public static void show(ViewEmptyStateBinding view, @DrawableRes int icon, @StringRes int title, @StringRes int message) {
        show(view, icon, title, message, 0, null);
    }

    public static void show(ViewEmptyStateBinding view, @DrawableRes int icon, @StringRes int title, @StringRes int message,
                            @StringRes int action, View.OnClickListener onAction) {
        view.emptyIcon.setImageResource(icon);
        view.emptyTitle.setText(title);
        view.emptyMessage.setText(message);
        view.emptyAction.setVisibility(onAction != null ? View.VISIBLE : View.GONE);
        if (onAction != null) {
            view.emptyAction.setText(action);
            view.emptyAction.setOnClickListener(onAction);
        }
        if (view.getRoot().getVisibility() != View.VISIBLE) {
            view.getRoot().setAlpha(0f);
            view.getRoot().setVisibility(View.VISIBLE);
            view.getRoot().animate().alpha(1f).setDuration(200).start();
        }
    }

    public static void hide(ViewEmptyStateBinding view) {
        view.getRoot().animate().cancel();
        view.getRoot().setVisibility(View.GONE);
    }
}
