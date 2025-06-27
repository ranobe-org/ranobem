package in.atulpatare.ranobem.ui.chapters;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.ranobem.databinding.ItemChapterBinding;
import in.atulpatare.ranobem.utils.NumberUtils;

public class ChapterAdapter extends RecyclerView.Adapter<ChapterAdapter.MyViewHolder> {
    private final List<Chapter> items;
    private final OnChapterItemClickListener listener;

    public ChapterAdapter(List<Chapter> items, OnChapterItemClickListener listener) {
        this.items = items;
        this.listener = listener;
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
        holder.binding.chapterName.setText(String.format("Chapter %s  %s", NumberUtils.normalize(item.index), item.name));
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
