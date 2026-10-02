package in.atulpatare.ranobem.ui.history.adapter;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.ArrayList;
import java.util.List;

import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.databinding.ItemHistoryBinding;
import in.atulpatare.ranobem.model.History;
import in.atulpatare.ranobem.utils.NumberUtils;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.MyViewHolder> {
    private final List<History> items = new ArrayList<>();
    private final OnHistoryItemClickListener listener;

    public HistoryAdapter(OnHistoryItemClickListener listener) {
        this.listener = listener;
    }

    // updates only the rows that changed, so the list keeps its scroll position and removals animate
    public void submit(List<History> next) {
        List<History> old = new ArrayList<>(items);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return old.size();
            }

            @Override
            public int getNewListSize() {
                return next.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return old.get(oldPosition).id.equals(next.get(newPosition).id);
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return old.get(oldPosition).createdAt == next.get(newPosition).createdAt;
            }
        });
        items.clear();
        items.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHistoryBinding binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new MyViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        History item = items.get(position);
        holder.binding.mangaName.setText(item.mangaName);
        String chapter = holder.itemView.getContext().getString(R.string.chapter_number, NumberUtils.normalize(item.chapterIndex));
        if (item.chapterName != null && !item.chapterName.trim().isEmpty()) {
            chapter = chapter + " · " + item.chapterName.trim();
        }
        holder.binding.chapterName.setText(chapter);
        holder.binding.createdAt.setText(DateUtils.getRelativeTimeSpanString(
                toMillis(item.createdAt), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        Glide.with(holder.binding.mangaCover)
                .load(item.cover)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.binding.mangaCover);
    }

    // older entries were stored in seconds
    private long toMillis(long createdAt) {
        return createdAt < 1000000000000L ? createdAt * 1000 : createdAt;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public interface OnHistoryItemClickListener {
        void onHistoryItemClick(History history);

        void onHistoryItemDeleteClick(History history);
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {
        private final ItemHistoryBinding binding;

        public MyViewHolder(@NonNull ItemHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.historyLayout.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) listener.onHistoryItemClick(items.get(position));
            });

            binding.deleteHistory.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) listener.onHistoryItemDeleteClick(items.get(position));
            });
        }
    }
}
