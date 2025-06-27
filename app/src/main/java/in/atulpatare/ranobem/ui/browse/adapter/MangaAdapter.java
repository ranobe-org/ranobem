package in.atulpatare.ranobem.ui.browse.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.databinding.ItemMangaBinding;

public class MangaAdapter extends RecyclerView.Adapter<MangaAdapter.MyViewHolder> {
    private final List<Manga> items;
    private final OnMangaItemClickListener listener;

    public MangaAdapter(List<Manga> items, OnMangaItemClickListener listener) {
        this.items = items;
        this.listener = listener;
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
        Glide.with(holder.binding.novelCover.getContext())
                .load(item.cover)
                .into(holder.binding.novelCover);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public interface OnMangaItemClickListener {
        void onMangaItemClick(Manga item);
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {
        private final ItemMangaBinding binding;

        public MyViewHolder(@NonNull ItemMangaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.novelCoverLayout.setOnClickListener(v ->
                    listener.onMangaItemClick(items.get(getAdapterPosition())));
        }
    }
}
