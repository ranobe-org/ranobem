package in.atulpatare.ranobem.ui.details;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.List;

import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.databinding.ItemMangaCarouselBinding;
import in.atulpatare.ranobem.ui.browse.adapter.MangaAdapter;

/**
 * Horizontal row of covers on the details page.
 */
public class MangaCarouselAdapter extends RecyclerView.Adapter<MangaCarouselAdapter.ViewHolder> {
    private final List<Manga> items;
    private final MangaAdapter.OnMangaItemClickListener listener;

    public MangaCarouselAdapter(List<Manga> items, MangaAdapter.OnMangaItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemMangaCarouselBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Manga item = items.get(position);
        holder.binding.name.setText(item.name);
        boolean hasRelation = item.relation != null && !item.relation.isEmpty();
        holder.binding.relation.setVisibility(hasRelation ? View.VISIBLE : View.GONE);
        holder.binding.relation.setText(item.relation);
        Glide.with(holder.binding.cover).load(item.cover)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.binding.cover);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemMangaCarouselBinding binding;

        public ViewHolder(@NonNull ItemMangaCarouselBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.coverCard.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) listener.onMangaItemClick(items.get(position));
            });
        }
    }
}
