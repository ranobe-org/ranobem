package in.atulpatare.ranobem.ui.reader;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.target.Target;

import java.util.List;

import in.atulpatare.ranobem.databinding.ItemPageBinding;

public class PageAdapter extends RecyclerView.Adapter<PageAdapter.MyViewHolder> {
    private final List<String> pages;

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
        String page = pages.get(position);
        holder.binding.pageNumber.setText(String.format("%s/%s", position + 1, pages.size()));
        Glide.with(holder.binding.image.getContext())
                .load(getGlideWithHeaders(page))
                .override(Target.SIZE_ORIGINAL)
                .into(holder.binding.image);
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

    public static class MyViewHolder extends RecyclerView.ViewHolder {
        private final ItemPageBinding binding;

        public MyViewHolder(@NonNull ItemPageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

        }
    }
}
