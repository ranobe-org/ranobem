package in.atulpatare.ranobem.ui.browse.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.ArrayList;
import java.util.List;

import in.atulpatare.core.models.Manga;
import in.atulpatare.core.sources.Source;
import in.atulpatare.core.sources.SourceManager;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.databinding.ItemMangaBinding;
import in.atulpatare.ranobem.ui.tracking.TrackSheet;

public class MangaAdapter extends RecyclerView.Adapter<MangaAdapter.MyViewHolder> {
    private final List<Manga> items;
    private final OnMangaItemClickListener listener;

    private boolean showSourceName = false;
    // library covers: unread badge, status in the description, long press to change it
    private boolean libraryMode = false;
    @Nullable
    private OnMangaItemLongClickListener longClickListener;

    public MangaAdapter(List<Manga> items, OnMangaItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void showSearchName(Boolean show) {
        this.showSourceName = show;
    }

    public void setLibraryMode(OnMangaItemLongClickListener listener) {
        this.libraryMode = true;
        this.longClickListener = listener;
    }

    public List<Manga> getItems() {
        return items;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMangaBinding binding = ItemMangaBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new MyViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        Manga item = items.get(position);
        holder.binding.novelName.setText(item.name);
        Glide.with(holder.binding.novelCover)
                .load(item.cover)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.binding.novelCover);

        if (showSourceName) {
            setSourceName(holder, item.sourceId);
        } else {
            holder.binding.sourceName.setVisibility(View.GONE);
        }

        boolean badge = libraryMode && item.unreadChapters > 0;
        holder.binding.unreadBadge.setVisibility(badge ? View.VISIBLE : View.GONE);
        if (badge) holder.binding.unreadBadge.setText(item.unreadChapters > 999 ? "999+" : String.valueOf(item.unreadChapters));
        holder.binding.novelCoverLayout.setContentDescription(libraryMode ? describe(holder.itemView.getContext(), item) : item.name);
    }

    // one sentence for TalkBack: title, shelf and unread chapters
    private static String describe(Context context, Manga item) {
        List<String> parts = new ArrayList<>();
        parts.add(item.name);
        if (item.readStatus != null) parts.add(TrackSheet.statusLabel(context, item.readStatus));
        if (item.unreadChapters > 0) {
            parts.add(context.getResources().getQuantityString(R.plurals.library_unread, item.unreadChapters, item.unreadChapters));
        }
        return String.join(", ", parts);
    }

    private void setSourceName(@NonNull MyViewHolder holder, int sourceId) {
        Source source = SourceManager.getSource(sourceId);
        holder.binding.sourceName.setText(source.meta().name);
        holder.binding.sourceName.setVisibility(View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public interface OnMangaItemClickListener {
        void onMangaItemClick(Manga item);
    }

    public interface OnMangaItemLongClickListener {
        void onMangaItemLongClick(Manga item);
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {
        private final ItemMangaBinding binding;

        public MyViewHolder(@NonNull ItemMangaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.novelCoverLayout.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) listener.onMangaItemClick(items.get(position));
            });
            // only library covers do anything on a long press, elsewhere it isn't offered at all
            if (libraryMode && longClickListener != null) {
                binding.novelCoverLayout.setOnLongClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position == RecyclerView.NO_POSITION) return false;
                    longClickListener.onMangaItemLongClick(items.get(position));
                    return true;
                });
                // TalkBack says "double tap and hold to change status" instead of a bare long press
                ViewCompat.replaceAccessibilityAction(binding.novelCoverLayout, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                        binding.getRoot().getContext().getString(R.string.library_change_status), null);
            }
        }
    }
}
