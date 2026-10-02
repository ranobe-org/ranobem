package in.atulpatare.ranobem.ui.reader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;

import java.util.ArrayList;
import java.util.List;

import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.databinding.ItemChapterTransitionBinding;
import in.atulpatare.ranobem.databinding.ItemPageBinding;

/**
 * Renders chapter pages for every reading mode, followed by a chapter transition card.
 * <p>
 * Every page is one item until it is measured. Very tall pages then become several slice
 * items (see {@link PageLoader}), which scroll seamlessly in webtoon mode and turn like
 * regular pages in paged modes.
 */
public class PageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_PAGE = 0;
    private static final int TYPE_TRANSITION = 1;
    private static final Object PAYLOAD_PROGRESS = new Object();

    // single pages taller than this (height / width) are long strips meant to be scrolled through
    private static final float STRIP_RATIO = 2.2f;
    // never decode a single page taller than this, protects against huge bitmaps when splitting is off
    private static final int MAX_DECODE_HEIGHT = 8192;

    private final PageLoader loader;
    private final ReaderSettings settings;
    private final Listener listener;

    // display items, a page is either one item (slice == -1) or one item per slice
    private final List<Item> items = new ArrayList<>();
    private int[] firstItem;

    private int viewportWidth, viewportHeight;
    private int foregroundColor;
    private String currentTitle = "";
    private String nextTitle;
    private boolean nextLoading = true;

    public PageAdapter(PageLoader loader, ReaderSettings settings, int foregroundColor, Listener listener) {
        this.loader = loader;
        this.settings = settings;
        this.foregroundColor = foregroundColor;
        this.listener = listener;
        buildItems();
    }

    // region items

    private void buildItems() {
        items.clear();
        firstItem = new int[loader.getPageCount()];
        for (int page = 0; page < loader.getPageCount(); page++) {
            firstItem[page] = items.size();
            items.addAll(itemsFor(page));
        }
    }

    private List<Item> itemsFor(int page) {
        PageLoader.Page p = loader.getPage(page);
        List<Item> result = new ArrayList<>();
        if (isSplit(p)) {
            for (int slice = 0; slice < p.sliceCount(); slice++) result.add(new Item(page, slice, p.sliceCount()));
        } else {
            result.add(new Item(page, -1, 1));
        }
        return result;
    }

    private boolean isSplit(PageLoader.Page page) {
        return settings.isSplitTallImages() && page.state == PageLoader.STATE_READY && page.cuts != null;
    }

    /**
     * Rebuilds every item, e.g. after splitting was turned on or off.
     */
    @SuppressLint("NotifyDataSetChanged")
    public void rebuildItems() {
        buildItems();
        notifyDataSetChanged();
    }

    public void onPageStateChanged(int page) {
        if (page < 0 || page >= firstItem.length) return;
        int start = firstItem[page];
        int oldCount = (page + 1 < firstItem.length ? firstItem[page + 1] : items.size()) - start;
        List<Item> updated = itemsFor(page);
        int newCount = updated.size();

        items.subList(start, start + oldCount).clear();
        items.addAll(start, updated);
        for (int p = page + 1; p < firstItem.length; p++) firstItem[p] += newCount - oldCount;

        notifyItemRangeChanged(start, Math.min(oldCount, newCount));
        if (newCount > oldCount) {
            notifyItemRangeInserted(start + oldCount, newCount - oldCount);
        } else if (oldCount > newCount) {
            notifyItemRangeRemoved(start + newCount, oldCount - newCount);
        }
    }

    public void onPageProgress(int page) {
        if (page < 0 || page >= firstItem.length) return;
        notifyItemChanged(firstItem[page], PAYLOAD_PROGRESS);
    }

    public int getPageCount() {
        return loader.getPageCount();
    }

    public int positionOfPage(int page) {
        if (firstItem.length == 0) return 0;
        return firstItem[Math.max(0, Math.min(firstItem.length - 1, page))];
    }

    /**
     * @return the page shown at an adapter position, the last page for the transition card
     */
    public int pageAt(int position) {
        if (items.isEmpty()) return 0;
        return items.get(Math.max(0, Math.min(items.size() - 1, position))).page;
    }

    public boolean isTransition(int position) {
        return position >= items.size();
    }

    // endregion

    /**
     * @return true if the viewport changed and items were re-laid out
     */
    @SuppressLint("NotifyDataSetChanged")
    public boolean setViewport(int width, int height) {
        if (width <= 0 || height <= 0 || (width == viewportWidth && height == viewportHeight)) {
            return false;
        }
        viewportWidth = width;
        viewportHeight = height;
        notifyDataSetChanged();
        return true;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setForegroundColor(int color) {
        foregroundColor = color;
        notifyDataSetChanged();
    }

    public void setTransition(String currentTitle, @Nullable String nextTitle, boolean nextLoading) {
        this.currentTitle = currentTitle;
        this.nextTitle = nextTitle;
        this.nextLoading = nextLoading;
        notifyItemChanged(items.size());
    }

    @Override
    public int getItemViewType(int position) {
        return position < items.size() ? TYPE_PAGE : TYPE_TRANSITION;
    }

    @Override
    public int getItemCount() {
        // pages + chapter transition card
        return items.isEmpty() ? 0 : items.size() + 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_TRANSITION) {
            return new TransitionHolder(ItemChapterTransitionBinding.inflate(inflater, parent, false));
        }
        return new PageHolder(ItemPageBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (holder instanceof PageHolder && !payloads.isEmpty() && payloads.get(0) == PAYLOAD_PROGRESS) {
            PageLoader.Page page = loader.getPage(items.get(position).page);
            if (page.state == PageLoader.STATE_LOADING) ((PageHolder) holder).showLoading(page.progress);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PageHolder) {
            bindPage((PageHolder) holder, items.get(position));
        } else {
            bindTransition((TransitionHolder) holder);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof PageHolder) ((PageHolder) holder).clear();
    }

    private void bindPage(PageHolder holder, Item item) {
        ItemPageBinding binding = holder.binding;
        Context context = binding.getRoot().getContext();
        PageLoader.Page page = loader.getPage(item.page);
        boolean paged = settings.isPaged();
        boolean slice = item.slice >= 0;

        holder.clear();
        holder.item = item;

        // layout
        RecyclerView.LayoutParams rootParams = (RecyclerView.LayoutParams) binding.getRoot().getLayoutParams();
        ViewGroup.LayoutParams imageParams = binding.image.getLayoutParams();
        rootParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        if (paged) {
            rootParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            rootParams.bottomMargin = 0;
            binding.getRoot().setPadding(0, 0, 0, 0);
            imageParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            int padding = viewportWidth * settings.getWebtoonPadding() / 100;
            boolean lastPiece = !slice || item.slice == item.sliceCount - 1;
            rootParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            rootParams.bottomMargin = settings.isPageGap() && lastPiece ? dp(context, 8) : 0;
            binding.getRoot().setPadding(padding, 0, padding, 0);
            imageParams.height = webtoonHeight(page, item, viewportWidth - 2 * padding);
        }
        binding.getRoot().setLayoutParams(rootParams);
        binding.image.setLayoutParams(imageParams);
        // TouchImageView measures 0x0 without an image, the placeholder keeps the reserved size
        ViewGroup.LayoutParams placeholderParams = binding.placeholder.getLayoutParams();
        placeholderParams.height = imageParams.height;
        binding.placeholder.setLayoutParams(placeholderParams);

        // slices of a webtoon strip scroll with the list, zooming them one by one makes no sense
        binding.image.setZoomEnabled(paged || !slice);
        holder.applyColors(foregroundColor, item.page);

        switch (page.state) {
            case PageLoader.STATE_READY:
                if (slice) loadSlice(holder, item);
                else loadImage(holder, item, page);
                break;
            case PageLoader.STATE_ERROR:
                holder.showError(page.error);
                break;
            default:
                holder.showLoading(page.progress);
                loader.request(item.page);
        }
    }

    private void bindTransition(TransitionHolder holder) {
        ItemChapterTransitionBinding binding = holder.binding;
        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) binding.getRoot().getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = settings.isPaged() ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
        binding.getRoot().setLayoutParams(params);

        binding.currentTitle.setText(currentTitle);
        if (nextLoading) {
            binding.nextLabel.setText(R.string.reader_up_next);
            binding.nextTitle.setText(R.string.reader_checking_next);
            binding.nextButton.setVisibility(View.GONE);
        } else if (nextTitle != null) {
            binding.nextLabel.setText(R.string.reader_up_next);
            binding.nextTitle.setText(nextTitle);
            binding.nextButton.setVisibility(View.VISIBLE);
        } else {
            binding.nextLabel.setText(R.string.reader_no_next);
            binding.nextTitle.setText(R.string.reader_no_next_desc);
            binding.nextButton.setVisibility(View.GONE);
        }
    }

    private int webtoonHeight(PageLoader.Page page, Item item, int contentWidth) {
        if (page.state != PageLoader.STATE_READY || page.width <= 0) {
            // reserve most of a screen until the real size is known, so pages load one by one
            return Math.round(viewportHeight * 0.8f);
        }
        float scale = (float) contentWidth / page.width;
        if (item.slice >= 0) {
            // round the edges, not the heights, so slices meet without a seam
            return Math.round(page.cuts[item.slice + 1] * scale) - Math.round(page.cuts[item.slice] * scale);
        }
        float ratio = page.ratio();
        int fullHeight = Math.round(contentWidth * ratio);
        switch (settings.getWebtoonScale()) {
            case ReaderSettings.WEBTOON_FIT_WIDTH:
                return fullHeight;
            case ReaderSettings.WEBTOON_FIT_SCREEN:
                return Math.min(fullHeight, viewportHeight);
            default:
                return ratio >= STRIP_RATIO ? fullHeight : Math.min(fullHeight, viewportHeight);
        }
    }

    private int contentWidth() {
        if (settings.isPaged()) return viewportWidth;
        return viewportWidth - 2 * (viewportWidth * settings.getWebtoonPadding() / 100);
    }

    private void loadSlice(PageHolder holder, Item item) {
        holder.binding.image.setScaleType(settings.isPaged() ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.FIT_XY);
        Bitmap cached = loader.loadSlice(item.page, item.slice, contentWidth(), (bitmap, error) -> {
            if (holder.item != item) return;
            if (bitmap != null) {
                holder.showBitmap(bitmap);
            } else if (error == R.string.reader_error_memory) {
                holder.showError(error);
            } else {
                // usually the cached file is gone, download it again before reporting an error
                loader.onDisplayFailed(item.page);
            }
        });
        if (cached != null) {
            holder.showBitmap(cached);
        } else {
            holder.showLoading(-1);
        }
    }

    private void loadImage(PageHolder holder, Item item, PageLoader.Page page) {
        ItemPageBinding binding = holder.binding;
        holder.showLoading(-1);
        binding.image.setScaleType(ImageView.ScaleType.FIT_CENTER);

        // paged pages can be zoomed, keep some extra detail for that
        int targetWidth = settings.isPaged() ? Math.round(viewportWidth * 1.5f) : contentWidth();
        if (targetWidth <= 0) targetWidth = Target.SIZE_ORIGINAL;

        Glide.with(binding.image)
                .load(page.file)
                .signature(new ObjectKey(page.file.lastModified()))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .override(targetWidth, MAX_DECODE_HEIGHT)
                .downsample(DownsampleStrategy.CENTER_INSIDE)
                .dontTransform()
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .listener(new RequestListener<>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                        if (holder.item == item) loader.onDisplayFailed(item.page);
                        return true;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                        if (holder.item != item) return false;
                        holder.showContent();
                        if (settings.isPaged()) {
                            // runs after Glide has set the drawable on the view
                            binding.image.post(() -> {
                                if (holder.item == item) applyPagedScale(binding, page.ratio());
                            });
                        }
                        return false;
                    }
                })
                .into(binding.image);
    }

    private void applyPagedScale(ItemPageBinding binding, float ratio) {
        boolean tallerThanScreen = viewportWidth > 0 && ratio > (float) viewportHeight / viewportWidth;
        if (settings.getPageScale() == ReaderSettings.PAGE_FIT_WIDTH && tallerThanScreen) {
            // fill the width and start at the top, the rest of the page can be panned into view
            binding.image.setZoom(1f, 0.5f, 0f, ImageView.ScaleType.CENTER_CROP);
        } else {
            binding.image.setZoom(1f, 0.5f, 0.5f, ImageView.ScaleType.FIT_CENTER);
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public interface Listener {
        void onNextChapterClick();
    }

    private static final class Item {
        final int page;
        // slice index, -1 when the page is shown whole
        final int slice;
        final int sliceCount;

        Item(int page, int slice, int sliceCount) {
            this.page = page;
            this.slice = slice;
            this.sliceCount = sliceCount;
        }
    }

    class PageHolder extends RecyclerView.ViewHolder {
        private final ItemPageBinding binding;
        private final int touchSlop;
        private Item item;
        private float downX, downY;

        @SuppressLint("ClickableViewAccessibility")
        PageHolder(@NonNull ItemPageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.touchSlop = ViewConfiguration.get(binding.getRoot().getContext()).getScaledTouchSlop();
            binding.image.setMaxZoom(4f);
            binding.image.setDoubleTapScale(2.5f);
            binding.retryImage.setOnClickListener(v -> {
                if (item != null) loader.retry(item.page);
            });
            binding.image.setOnTouchListener((v, event) -> {
                handleNestedScroll(event);
                return false;
            });
        }

        void clear() {
            Glide.with(binding.image).clear(binding.image);
            binding.image.setImageDrawable(null);
            binding.image.resetZoom();
            item = null;
        }

        void applyColors(int color, int page) {
            Context context = binding.getRoot().getContext();
            binding.pageNumber.setText(context.getString(R.string.reader_page_label, page + 1));
            binding.pageNumber.setTextColor(ColorUtils.setAlphaComponent(color, 0xB3));
            binding.progressText.setTextColor(color);
            binding.errorTitle.setText(context.getString(R.string.reader_page_failed_numbered, page + 1));
            binding.errorTitle.setTextColor(color);
            binding.errorText.setTextColor(ColorUtils.setAlphaComponent(color, 0xB3));
            binding.errorIcon.setColorFilter(color);
            binding.errorIcon.getBackground().mutate().setTint(ColorUtils.setAlphaComponent(color, 0x1F));
            binding.progress.setIndicatorColor(color);
            binding.progress.setTrackColor(ColorUtils.setAlphaComponent(color, 0x26));
            binding.placeholder.setBackgroundColor(ColorUtils.setAlphaComponent(color, 0x0A));
        }

        void showLoading(int percent) {
            binding.placeholder.setVisibility(View.VISIBLE);
            binding.loading.setVisibility(View.VISIBLE);
            binding.error.setVisibility(View.GONE);
            boolean indeterminate = percent < 0;
            if (binding.progress.isIndeterminate() != indeterminate) {
                // the indicator only switches mode while hidden
                binding.progress.setVisibility(View.INVISIBLE);
                binding.progress.setIndeterminate(indeterminate);
                binding.progress.setVisibility(View.VISIBLE);
            }
            if (indeterminate) {
                binding.progressText.setVisibility(View.GONE);
            } else {
                binding.progress.setProgressCompat(Math.max(2, percent), true);
                binding.progressText.setVisibility(View.VISIBLE);
                binding.progressText.setText(binding.getRoot().getContext().getString(R.string.reader_percent, percent));
            }
        }

        void showError(int error) {
            binding.placeholder.setVisibility(View.VISIBLE);
            binding.loading.setVisibility(View.GONE);
            binding.error.setVisibility(View.VISIBLE);
            binding.errorText.setText(error != 0 ? error : R.string.reader_error_unknown);
        }

        void showContent() {
            binding.placeholder.setVisibility(View.GONE);
            binding.loading.setVisibility(View.GONE);
            binding.error.setVisibility(View.GONE);
        }

        void showBitmap(Bitmap bitmap) {
            showContent();
            binding.image.setImageBitmap(bitmap);
        }

        /**
         * Lets a zoomed page pan inside itself and hands the gesture back to the list
         * once the page edge is reached, so swiping continues to the next page.
         */
        private void handleNestedScroll(MotionEvent event) {
            ViewGroup parent = (ViewGroup) binding.getRoot();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    parent.requestDisallowInterceptTouchEvent(binding.image.isZoomed());
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    parent.requestDisallowInterceptTouchEvent(binding.image.isZoomEnabled());
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() > 1) {
                        parent.requestDisallowInterceptTouchEvent(binding.image.isZoomEnabled());
                        break;
                    }
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (Math.abs(dx) < touchSlop && Math.abs(dy) < touchSlop) break;
                    boolean canScroll = Math.abs(dx) > Math.abs(dy)
                            ? binding.image.canScrollHorizontally(dx > 0 ? -1 : 1)
                            : binding.image.canScrollVertically(dy > 0 ? -1 : 1);
                    parent.requestDisallowInterceptTouchEvent(canScroll);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    parent.requestDisallowInterceptTouchEvent(false);
                    break;
            }
        }
    }

    class TransitionHolder extends RecyclerView.ViewHolder {
        private final ItemChapterTransitionBinding binding;

        TransitionHolder(@NonNull ItemChapterTransitionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.nextButton.setOnClickListener(v -> listener.onNextChapterClick());
        }
    }
}
