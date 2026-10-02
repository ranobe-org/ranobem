package in.atulpatare.ranobem.utils;

import android.content.Context;
import android.util.DisplayMetrics;

import in.atulpatare.ranobem.R;

public class DisplayUtils {
    private final int widthPx;
    private final int minCellPx;
    private final int spacingPx;

    public DisplayUtils(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        widthPx = metrics.widthPixels;
        minCellPx = context.getResources().getDimensionPixelSize(R.dimen.grid_min_cell);
        spacingPx = context.getResources().getDimensionPixelSize(R.dimen.grid_spacing);
    }

    // as many columns as fit at the minimum cover width, never fewer than 2
    public int noOfCols() {
        return Math.max(2, (widthPx - spacingPx) / (minCellPx + spacingPx));
    }

    public int spacing() {
        return spacingPx;
    }
}
