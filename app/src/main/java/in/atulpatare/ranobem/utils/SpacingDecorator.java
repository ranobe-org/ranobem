package in.atulpatare.ranobem.utils;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Equal gaps between grid cells and around the grid's edges.
 */
public class SpacingDecorator extends RecyclerView.ItemDecoration {
    private final int spacing;

    public SpacingDecorator(int spacing) {
        this.spacing = spacing;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION) return;
        int columns = parent.getLayoutManager() instanceof GridLayoutManager
                ? ((GridLayoutManager) parent.getLayoutManager()).getSpanCount() : 1;
        int column = position % columns;

        // splits the gaps so every cell ends up the same width
        outRect.left = spacing - column * spacing / columns;
        outRect.right = (column + 1) * spacing / columns;
        outRect.top = position < columns ? spacing : 0;
        outRect.bottom = spacing;
    }
}
