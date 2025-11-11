package in.atulpatare.ranobem.ui.reader;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.util.List;

import in.atulpatare.ranobem.databinding.ItemPageBinding;

public class PageAdapter extends RecyclerView.Adapter<PageAdapter.MyViewHolder> {
    private  final List<String> pages;

    public PageAdapter(List<String> pages) {
        this.pages = pages;
    }

    @NonNull
    @Override
    public PageAdapter.MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPageBinding binding = ItemPageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new MyViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PageAdapter.MyViewHolder holder, int position) {
        holder.binding.pageNumber.setText(String.format("%s/%s", position + 1, pages.size()));
        loadImage(holder.binding, position);
    }

    private Priority getPriorityForPage(int currentPosition) {
        int totalCount = pages.size();
        if (totalCount == 0) return Priority.NORMAL;
        float progress = (float) currentPosition / (float) totalCount;
        if (progress < 0.2f) {          // first 20%
            return Priority.HIGH;
        } else if (progress < 0.6f) {   // middle section
            return Priority.NORMAL;
        } else if (progress < 0.9f) {   // nearing end
            return Priority.HIGH;
        } else {                        // last 10%
            return Priority.IMMEDIATE;
        }
    }


    @Override
    public int getItemCount() {
        return pages.size();
    }

    public GlideUrl getGlideWithHeaders(String url) {
        return new GlideUrl(url, new LazyHeaders.Builder()
                .addHeader("referer", "https://mangafire.to")
                .build());
    }

    public void loadImage (ItemPageBinding binding, int position) {
        String page = pages.get(position);
        binding.retryImage.setVisibility(View.GONE);
        binding.progress.setVisibility(View.VISIBLE);
        Glide.with(binding.image.getContext())
                .load(getGlideWithHeaders(page))
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .skipMemoryCache(false)
                .override(Target.SIZE_ORIGINAL)
                .priority(getPriorityForPage(position))
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .listener(new RequestListener<>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        binding.retryImage.setVisibility(View.VISIBLE);
                        binding.progress.setVisibility(View.GONE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        binding.progress.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(binding.image);

    }

    public  class MyViewHolder extends RecyclerView.ViewHolder {
        private final ItemPageBinding binding;

        public MyViewHolder(@NonNull ItemPageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.retryImage.setOnClickListener(v -> loadImage(this.binding, getAdapterPosition()));
        }
    }
}
