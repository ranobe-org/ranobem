package in.atulpatare.ranobem.ui.chapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.databinding.ItemChapterBinding;
import in.atulpatare.ranobem.model.History;
import in.atulpatare.ranobem.utils.NumberUtils;

public class ChapterAdapter extends RecyclerView.Adapter<ChapterAdapter.MyViewHolder> {
    // unread chapters released within this window get a "New" tag
    private static final long NEW_WINDOW_MS = 7 * DateUtils.DAY_IN_MILLIS;

    private final List<Chapter> items = new ArrayList<>();
    private final OnChapterItemClickListener listener;
    private final Set<Integer> read = new HashSet<>();
    private int lastReadId = Integer.MIN_VALUE;

    public ChapterAdapter(OnChapterItemClickListener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void submit(List<Chapter> chapters) {
        items.clear();
        items.addAll(chapters);
        notifyDataSetChanged();
    }

    /**
     * Histories newest first, the first one is where the reader left off.
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setHistory(List<History> histories) {
        read.clear();
        lastReadId = Integer.MIN_VALUE;
        for (History h : histories) read.add(h.chapterId);
        if (!histories.isEmpty()) lastReadId = histories.get(0).chapterId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChapterBinding binding = ItemChapterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new MyViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        Chapter item = items.get(position);
        boolean isRead = read.contains(item.id);
        boolean isLastRead = item.id == lastReadId;
        long now = System.currentTimeMillis();
        boolean isNew = !isRead && item.updatedAt > 0 && now - item.updatedAt < NEW_WINDOW_MS;

        holder.binding.chapterName.setText(context.getString(R.string.chapter_number, NumberUtils.normalize(item.index)));

        String meta = meta(item, now);
        holder.binding.chapterMeta.setText(meta);
        holder.binding.chapterMeta.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);

        if (isLastRead) {
            showTag(holder, R.string.chapter_last_read, com.google.android.material.R.attr.colorSecondaryContainer,
                    com.google.android.material.R.attr.colorOnSecondaryContainer);
        } else if (isNew) {
            showTag(holder, R.string.chapter_new, androidx.appcompat.R.attr.colorPrimary,
                    com.google.android.material.R.attr.colorOnPrimary);
        } else {
            holder.binding.chapterTag.setVisibility(View.GONE);
        }
        holder.binding.chapterRead.setVisibility(isRead && !isLastRead ? View.VISIBLE : View.GONE);

        // read chapters are dimmed; rows are recycled, so unread ones must be reset
        holder.binding.text.setAlpha(isRead && !isLastRead ? 0.5f : 1f);
    }

    private void showTag(MyViewHolder holder, int text, int backgroundAttr, int textAttr) {
        holder.binding.chapterTag.setText(text);
        holder.binding.chapterTag.setBackgroundTintList(ColorStateList.valueOf(
                MaterialColors.getColor(holder.binding.chapterTag, backgroundAttr)));
        holder.binding.chapterTag.setTextColor(MaterialColors.getColor(holder.binding.chapterTag, textAttr));
        holder.binding.chapterTag.setVisibility(View.VISIBLE);
    }

    // "Title · 3 days ago", either part may be missing
    private static String meta(Chapter item, long now) {
        List<String> parts = new ArrayList<>();
        if (item.name != null && !item.name.trim().isEmpty()) parts.add(item.name.trim());
        if (item.updatedAt > 0) {
            parts.add(DateUtils.getRelativeTimeSpanString(item.updatedAt, now, DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE).toString());
        }
        return String.join(" · ", parts);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public interface OnChapterItemClickListener {
        void onChapterItemClick(Chapter item);

        boolean onMenuItemClick(MenuItem menuItem);
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {
        private final ItemChapterBinding binding;

        public MyViewHolder(@NonNull ItemChapterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.chapterItemLayout.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) listener.onChapterItemClick(items.get(position));
            });
        }
    }
}
