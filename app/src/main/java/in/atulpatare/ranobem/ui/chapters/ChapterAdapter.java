package in.atulpatare.ranobem.ui.chapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.databinding.ItemChapterBinding;
import in.atulpatare.ranobem.model.History;
import in.atulpatare.ranobem.utils.NumberUtils;

public class ChapterAdapter extends RecyclerView.Adapter<ChapterAdapter.MyViewHolder> {
    private final List<Chapter> items;
    private final OnChapterItemClickListener listener;
    private List<History> histories;

    public ChapterAdapter(List<Chapter> items, OnChapterItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setHistory(List<History> histories) {
        this.histories = histories;
        this.notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChapterBinding binding = ItemChapterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new MyViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        Chapter item = items.get(position);
        String name = holder.itemView.getContext().getString(R.string.chapter_number, NumberUtils.normalize(item.index));
        if (item.name != null && !item.name.trim().isEmpty()) name = name + " · " + item.name.trim();
        holder.binding.chapterName.setText(name);

        // read chapters are dimmed; rows are recycled, so unread ones must be reset
        holder.binding.chapterName.setAlpha(getHistoryByChapterId(item.id) != null ? 0.45f : 1f);
    }

    private History getHistoryByChapterId(int id) {
        if (histories == null) return null;
        for (History h : histories) {
            if (h.chapterId == id) {
                return h;
            }
        }
        return null;
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

            binding.chapterItemLayout.setOnClickListener(v ->
                    listener.onChapterItemClick(items.get(getAdapterPosition())));
        }
    }
}
