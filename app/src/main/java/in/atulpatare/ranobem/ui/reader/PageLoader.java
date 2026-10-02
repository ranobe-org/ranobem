package in.atulpatare.ranobem.ui.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import in.atulpatare.ranobem.R;

/**
 * Loads the pages of one chapter: downloads them closest-to-the-reader first, measures them
 * and plans where very tall images are cut into screen sized slices. Slices are decoded on
 * demand with {@link BitmapRegionDecoder}, so a 16000px strip never has to fit in memory or
 * in a GPU texture at once.
 * <p>
 * Page state is only mutated on the main thread, background work posts its results back.
 */
public class PageLoader {
    public static final int STATE_IDLE = 0;
    public static final int STATE_LOADING = 1;
    public static final int STATE_READY = 2;
    public static final int STATE_ERROR = 3;

    // pages taller than this (height / width), or than MAX_SLICE_HEIGHT pixels, are split
    private static final float SPLIT_RATIO = 3f;
    private static final int MAX_SLICE_HEIGHT = 4096;
    // target height / width of a slice, a little shorter than a phone screen
    private static final float SLICE_RATIO = 1.6f;
    // a cut may move this far (fraction of a slice) to land on a plain background row
    private static final float CUT_SEARCH = 0.2f;
    private static final int MAX_ATTEMPTS = 3;
    private static final int PREFETCH_AHEAD = 4;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final PageCache cache;
    private final List<Page> pages = new ArrayList<>();
    private final Listener listener;
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(3);
    private final ExecutorService decodeExecutor = Executors.newFixedThreadPool(2);

    // pending downloads, picked by distance to the page being read when a worker frees up
    private final Map<Integer, Job> pending = new LinkedHashMap<>();
    private final Object lock = new Object();
    private int focus;

    private final LruCache<String, Bitmap> slices;
    private final Map<String, List<SliceCallback>> decoding = new HashMap<>();
    private final LinkedHashMap<Integer, BitmapRegionDecoder> decoders = new LinkedHashMap<>(4, 0.75f, true);

    private volatile boolean released;

    public PageLoader(Context context, List<String> urls, Listener listener) {
        this.cache = PageCache.get(context);
        this.listener = listener;
        for (String url : urls) pages.add(new Page(url));

        int cacheSize = (int) Math.min(Runtime.getRuntime().maxMemory() / 6, 96L * 1024 * 1024);
        slices = new LruCache<>(cacheSize) {
            @Override
            protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
                return value.getAllocationByteCount();
            }
        };
    }

    public int getPageCount() {
        return pages.size();
    }

    public Page getPage(int index) {
        return pages.get(index);
    }

    // region downloads

    /**
     * Starts loading a page if it is not loaded or loading already.
     */
    public void request(int index) {
        if (released || index < 0 || index >= pages.size()) return;
        Page page = pages.get(index);
        if (page.state == STATE_IDLE) {
            // idle pages are already shown as loading, no need to notify
            enqueue(index, false, false);
        }
    }

    /**
     * Marks the page being read so downloads around it are done first, and prefetches the next pages.
     */
    public void setFocus(int index) {
        synchronized (lock) {
            focus = index;
        }
        request(index);
        for (int i = 1; i <= PREFETCH_AHEAD; i++) request(index + i);
        request(index - 1);
    }

    /**
     * Downloads the page again, ignoring the cached copy.
     */
    public void retry(int index) {
        if (released || index < 0 || index >= pages.size()) return;
        Page page = pages.get(index);
        if (page.state == STATE_LOADING) return;
        page.generation++;
        page.displayFailures = 0;
        enqueue(index, true, true);
    }

    /**
     * Called when a ready page could not be displayed, e.g. the cached file was evicted.
     */
    public void onDisplayFailed(int index) {
        if (released || index < 0 || index >= pages.size()) return;
        Page page = pages.get(index);
        if (page.state != STATE_READY) return;
        page.displayFailures++;
        if (page.displayFailures > 1) {
            page.state = STATE_ERROR;
            page.error = R.string.reader_error_display;
            notifyLater(index);
        } else {
            page.generation++;
            enqueue(index, true, true);
        }
    }

    private void enqueue(int index, boolean refresh, boolean notify) {
        Page page = pages.get(index);
        page.state = STATE_LOADING;
        page.progress = -1;
        page.error = 0;
        if (notify) notifyLater(index);

        synchronized (lock) {
            pending.put(index, new Job(index, page.url, page.generation, refresh));
        }
        downloadExecutor.execute(this::runNext);
    }

    /**
     * Requests can come from inside RecyclerView layout passes, which must not be notified synchronously.
     */
    private void notifyLater(int index) {
        main.post(() -> {
            if (!released) listener.onPageStateChanged(index);
        });
    }

    private void runNext() {
        Job job;
        synchronized (lock) {
            job = pickNext();
            if (job == null) return;
            pending.remove(job.index);
        }
        download(job);
    }

    private Job pickNext() {
        Job best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Job job : pending.values()) {
            // reading forward is far more likely than going back
            int score = job.index >= focus ? job.index - focus : (focus - job.index) * 3;
            if (score < bestScore) {
                bestScore = score;
                best = job;
            }
        }
        return best;
    }

    private void download(Job job) {
        boolean refresh = job.refresh;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS && !released; attempt++) {
            try {
                File file = cache.fetch(job.url, refresh, percent -> postProgress(job, percent));
                Result result = measure(file);
                if (result == null) {
                    // damaged or unsupported image, try a fresh copy before giving up
                    cache.remove(job.url);
                    refresh = true;
                    if (attempt == MAX_ATTEMPTS) postError(job, R.string.reader_error_decode);
                    continue;
                }
                postReady(job, result);
                return;
            } catch (InterruptedIOException e) {
                if (released) return;
                if (attempt == MAX_ATTEMPTS) postError(job, R.string.reader_error_timeout);
            } catch (IOException e) {
                if (released) return;
                int error = describe(e);
                // client errors won't fix themselves, don't hammer the server
                boolean permanent = e instanceof PageCache.HttpException
                        && ((PageCache.HttpException) e).code >= 400
                        && ((PageCache.HttpException) e).code < 500
                        && ((PageCache.HttpException) e).code != 429;
                if (permanent || attempt == MAX_ATTEMPTS) {
                    postError(job, error);
                    return;
                }
            } catch (OutOfMemoryError e) {
                postError(job, R.string.reader_error_memory);
                return;
            } catch (Exception e) {
                if (!released) postError(job, R.string.reader_error_unknown);
                return;
            }
            backoff(attempt);
        }
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(700L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int describe(IOException e) {
        if (e instanceof UnknownHostException || e instanceof ConnectException) {
            return R.string.reader_error_offline;
        } else if (e instanceof SocketTimeoutException) {
            return R.string.reader_error_timeout;
        } else if (e instanceof PageCache.HttpException) {
            int code = ((PageCache.HttpException) e).code;
            if (code == 404 || code == 410) return R.string.reader_error_not_found;
            if (code >= 500) return R.string.reader_error_server;
            return R.string.reader_error_refused;
        }
        return R.string.reader_error_network;
    }

    /**
     * Reads the image size and plans the slices, or returns null if the file is not a usable image.
     */
    @Nullable
    private Result measure(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;

        Result result = new Result(file, options.outWidth, options.outHeight);
        result.cuts = planCuts(file, options.outWidth, options.outHeight);
        return result;
    }

    @Nullable
    private int[] planCuts(File file, int width, int height) {
        if ((float) height / width <= SPLIT_RATIO && height <= MAX_SLICE_HEIGHT) return null;

        int count = Math.max(2, Math.round(height / (width * SLICE_RATIO)));
        // slices may grow by CUT_SEARCH while snapping, keep them under the texture limit
        count = Math.max(count, (int) Math.ceil(height / (MAX_SLICE_HEIGHT * (1 - CUT_SEARCH))));
        float step = (float) height / count;
        int range = Math.round(step * CUT_SEARCH);

        int[] cuts = new int[count + 1];
        cuts[count] = height;
        BitmapRegionDecoder decoder = decoder(-1, file);
        try {
            for (int i = 1; i < count; i++) {
                int target = Math.round(i * step);
                int low = Math.max(cuts[i - 1] + range, target - range);
                int high = Math.min(height - range, target + range);
                cuts[i] = decoder != null && high > low ? quietRow(decoder, width, low, high, target) : target;
            }
        } finally {
            if (decoder != null) decoder.recycle();
        }
        return cuts;
    }

    /**
     * Finds the row between {@code low} and {@code high} that is closest to a flat color,
     * so slices are cut through gutters instead of through artwork and speech bubbles.
     */
    private static int quietRow(BitmapRegionDecoder decoder, int width, int low, int high, int target) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, Integer.highestOneBit(Math.max(1, width / 240)));
        Bitmap band;
        try {
            band = decoder.decodeRegion(new Rect(0, low, width, high), options);
        } catch (Exception | OutOfMemoryError e) {
            return target;
        }
        if (band == null) return target;

        int sample = options.inSampleSize;
        int w = band.getWidth();
        int[] row = new int[w];
        int best = target;
        double bestScore = Double.MAX_VALUE;
        for (int y = 0; y < band.getHeight(); y++) {
            band.getPixels(row, 0, w, 0, y, w, 1);
            double sum = 0, sumSq = 0;
            for (int color : row) {
                int luma = ((color >> 16 & 0xff) * 3 + (color >> 8 & 0xff) * 6 + (color & 0xff)) / 10;
                sum += luma;
                sumSq += luma * luma;
            }
            double variance = sumSq / w - (sum / w) * (sum / w);
            int source = low + y * sample;
            // prefer flat rows, then rows close to the even split point
            double score = variance + Math.abs(source - target) * 0.05;
            if (score < bestScore) {
                bestScore = score;
                best = source;
            }
        }
        band.recycle();
        return best;
    }

    private void postProgress(Job job, int percent) {
        main.post(() -> {
            Page page = pages.get(job.index);
            if (released || page.generation != job.generation || page.state != STATE_LOADING) return;
            page.progress = percent;
            listener.onPageProgress(job.index, percent);
        });
    }

    private void postReady(Job job, Result result) {
        main.post(() -> {
            Page page = pages.get(job.index);
            if (released || page.generation != job.generation) return;
            page.file = result.file;
            page.width = result.width;
            page.height = result.height;
            page.cuts = result.cuts;
            page.state = STATE_READY;
            page.error = 0;
            invalidateSlices(job.index);
            listener.onPageStateChanged(job.index);
        });
    }

    private void postError(Job job, int error) {
        main.post(() -> {
            Page page = pages.get(job.index);
            if (released || page.generation != job.generation) return;
            page.state = STATE_ERROR;
            page.error = error;
            listener.onPageStateChanged(job.index);
        });
    }

    // endregion

    // region slices

    /**
     * Returns the slice bitmap if it is cached, otherwise decodes it in the background and
     * delivers it to {@code callback} on the main thread.
     */
    @Nullable
    public Bitmap loadSlice(int index, int slice, int targetWidth, SliceCallback callback) {
        Page page = pages.get(index);
        if (released || page.state != STATE_READY || page.cuts == null) return null;

        int sample = sampleSize(page.width, targetWidth);
        String key = index + ":" + slice + ":" + sample + ":" + page.generation;
        Bitmap cached = slices.get(key);
        if (cached != null) return cached;

        List<SliceCallback> callbacks = decoding.get(key);
        if (callbacks != null) {
            callbacks.add(callback);
            return null;
        }
        callbacks = new ArrayList<>();
        callbacks.add(callback);
        decoding.put(key, callbacks);

        File file = page.file;
        Rect region = new Rect(0, page.cuts[slice], page.width, page.cuts[slice + 1]);
        int generation = page.generation;
        decodeExecutor.execute(() -> {
            Bitmap bitmap = null;
            boolean outOfMemory = false;
            try {
                bitmap = decodeRegion(index, file, region, sample);
            } catch (OutOfMemoryError e) {
                outOfMemory = true;
            }
            Bitmap result = bitmap;
            boolean oom = outOfMemory;
            main.post(() -> {
                List<SliceCallback> waiting = decoding.remove(key);
                if (released || waiting == null) return;
                if (result != null && pages.get(index).generation == generation) slices.put(key, result);
                int error = result != null ? 0 : oom ? R.string.reader_error_memory : R.string.reader_error_decode;
                for (SliceCallback c : waiting) c.onSlice(result, error);
            });
        });
        return null;
    }

    @Nullable
    private Bitmap decodeRegion(int index, File file, Rect region, int sample) {
        if (released) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        for (int attempt = 0; attempt < 2; attempt++) {
            BitmapRegionDecoder decoder = decoder(index, file);
            if (decoder == null) return decodeFallback(file, region, sample);
            try {
                return decoder.decodeRegion(region, options);
            } catch (IllegalStateException | IllegalArgumentException e) {
                // decoder was recycled by eviction meanwhile, open it again
                dropDecoder(index);
            } catch (OutOfMemoryError e) {
                main.post(slices::evictAll);
                options.inSampleSize *= 2;
            }
        }
        return null;
    }

    /**
     * Some formats (e.g. GIF) can't be region decoded: decode the whole image downsampled and crop.
     */
    @Nullable
    private static Bitmap decodeFallback(File file, Rect region, int sample) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        while (region.height() / options.inSampleSize > MAX_SLICE_HEIGHT * 2) options.inSampleSize *= 2;
        Bitmap full = BitmapFactory.decodeFile(file.getPath(), options);
        if (full == null) return null;
        int s = options.inSampleSize;
        int top = Math.min(full.getHeight() - 1, region.top / s);
        int height = Math.max(1, Math.min(full.getHeight() - top, region.height() / s));
        Bitmap cropped = Bitmap.createBitmap(full, 0, top, full.getWidth(), height);
        if (cropped != full) full.recycle();
        return cropped;
    }

    /**
     * Returns a cached region decoder for the page, {@code index == -1} opens an uncached one.
     */
    @Nullable
    private BitmapRegionDecoder decoder(int index, File file) {
        if (index >= 0) {
            synchronized (decoders) {
                BitmapRegionDecoder cached = decoders.get(index);
                if (cached != null && !cached.isRecycled()) return cached;
            }
        }
        BitmapRegionDecoder decoder;
        try {
            //noinspection deprecation
            decoder = BitmapRegionDecoder.newInstance(file.getPath(), false);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (index < 0 || decoder == null) return decoder;

        synchronized (decoders) {
            if (released) {
                decoder.recycle();
                return null;
            }
            decoders.put(index, decoder);
            // keep a few pages open, a large strip's decoder holds a sizeable index
            Iterator<Map.Entry<Integer, BitmapRegionDecoder>> iterator = decoders.entrySet().iterator();
            while (decoders.size() > 3 && iterator.hasNext()) {
                Map.Entry<Integer, BitmapRegionDecoder> eldest = iterator.next();
                if (eldest.getKey() == index) continue;
                eldest.getValue().recycle();
                iterator.remove();
            }
        }
        return decoder;
    }

    private void dropDecoder(int index) {
        synchronized (decoders) {
            BitmapRegionDecoder decoder = decoders.remove(index);
            if (decoder != null) decoder.recycle();
        }
    }

    private void invalidateSlices(int index) {
        dropDecoder(index);
        String prefix = index + ":";
        for (String key : slices.snapshot().keySet()) {
            if (key.startsWith(prefix)) slices.remove(key);
        }
    }

    private static int sampleSize(int sourceWidth, int targetWidth) {
        int sample = 1;
        while (targetWidth > 0 && sourceWidth / (sample * 2) >= targetWidth) sample *= 2;
        return sample;
    }

    // endregion

    /**
     * Stops all work and frees memory. The loader can't be used afterwards.
     */
    public void release() {
        released = true;
        downloadExecutor.shutdownNow();
        decodeExecutor.shutdownNow();
        synchronized (lock) {
            pending.clear();
        }
        synchronized (decoders) {
            for (BitmapRegionDecoder decoder : decoders.values()) decoder.recycle();
            decoders.clear();
        }
        decoding.clear();
        slices.evictAll();
    }

    public static class Page {
        public final String url;
        public int state = STATE_IDLE;
        // download progress 0..100, -1 while unknown
        public int progress = -1;
        public File file;
        public int width, height;
        // slice boundaries in source pixels [0, ..., height], null when the page is not split
        public int[] cuts;
        // string resource describing the failure
        public int error;
        int generation;
        int displayFailures;

        Page(String url) {
            this.url = url;
        }

        public float ratio() {
            return width > 0 ? (float) height / width : 0;
        }

        public int sliceCount() {
            return cuts == null ? 1 : cuts.length - 1;
        }
    }

    private static class Job {
        final int index;
        final String url;
        final int generation;
        final boolean refresh;

        Job(int index, String url, int generation, boolean refresh) {
            this.index = index;
            this.url = url;
            this.generation = generation;
            this.refresh = refresh;
        }
    }

    private static class Result {
        final File file;
        final int width, height;
        int[] cuts;

        Result(File file, int width, int height) {
            this.file = file;
            this.width = width;
            this.height = height;
        }
    }

    public interface Listener {
        void onPageStateChanged(int index);

        void onPageProgress(int index, int percent);
    }

    public interface SliceCallback {
        /**
         * @param bitmap the slice, or null when it failed
         * @param error  string resource describing the failure, 0 on success
         */
        void onSlice(@Nullable Bitmap bitmap, int error);
    }
}
