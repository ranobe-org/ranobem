package in.atulpatare.ranobem.ui.history.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;
import java.util.Locale;

import in.atulpatare.ranobem.databinding.ItemHistoryBinding;
import in.atulpatare.ranobem.model.History;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.MyViewHolder> {
    private final List<History> items;
    private final OnHistoryItemClickListener listener;

    public HistoryAdapter(List<History> items, OnHistoryItemClickListener listener) {
        this.items = items;
        this.listener = listener;
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
        holder.binding.chapterName.setText(String.format(Locale.getDefault(), "Chapter %s %s", formatIndex(item.chapterIndex), item.chapterName));
        holder.binding.createdAt.setText(formatCreatedAt(item.createdAt));
        Glide.with(holder.binding.mangaCover.getContext())
                .load(item.cover)
                .into(holder.binding.mangaCover);
    }

    private String formatIndex(float value) {
        if (value == (int) value) {
            // If no decimal part, return as integer string
            return String.valueOf((int) value);
        } else {
            // Else, return as normal float string
            return String.valueOf(value);
        }
    }

    private String formatCreatedAt(long createdAt) {
        // Convert to milliseconds if in seconds
        if (createdAt < 1000000000000L) {
            createdAt *= 1000;
        }

        long now = System.currentTimeMillis();
        long diff = now - createdAt;

        if (diff < 0) return "In the future";

        final long SECOND = 1000;
        final long MINUTE = 60 * SECOND;
        final long HOUR = 60 * MINUTE;
        final long DAY = 24 * HOUR;

        if (diff < MINUTE) {
            return "just now";
        } else if (diff < 2 * MINUTE) {
            return "a minute ago";
        } else if (diff < 60 * MINUTE) {
            return (diff / MINUTE) + " minutes ago";
        } else if (diff < 2 * HOUR) {
            return "an hour ago";
        } else if (diff < 24 * HOUR) {
            return (diff / HOUR) + " hours ago";
        } else if (diff < 2 * DAY) {
            return "yesterday";
        } else if (diff < 7 * DAY) {
            return (diff / DAY) + " days ago";
        } else if (diff < 30 * DAY) {
            return (diff / 7 / DAY) + " weeks ago";
        } else if (diff < 365 * DAY) {
            return (diff / 30 / DAY) + " months ago";
        } else {
            return (diff / 365 / DAY) + " years ago";
        }
    }


    @Override
    public int getItemCount() {
        return items.size();
    }

    public interface OnHistoryItemClickListener {
        void onHistoryItemClick(History history);
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {
        private final ItemHistoryBinding binding;

        public MyViewHolder(@NonNull ItemHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.historyLayout.setOnClickListener(v ->
                    listener.onHistoryItemClick(items.get(getAdapterPosition())));
        }
    }
}
