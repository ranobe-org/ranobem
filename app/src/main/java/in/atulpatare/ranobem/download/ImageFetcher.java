package in.atulpatare.ranobem.download;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import in.atulpatare.ranobem.ui.reader.PageCache;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Downloads images into a job's work folder, retrying with backoff and waiting out lost
 * connections. Pages are written to a temporary file and renamed when complete, so a file with
 * the final name is always a whole image. Images that aren't JPEG, PNG or GIF are re-encoded
 * so any EPUB reader can show them.
 */
class ImageFetcher {
    private static final int MAX_ATTEMPTS = 6;
    private static final long MAX_BACKOFF_MS = 30_000;
    private static final String[] STORED_FORMATS = {ImageInfo.JPEG, ImageInfo.PNG, ImageInfo.GIF};

    private static OkHttpClient client;

    private final NetworkMonitor network;
    private final Random random = new Random();
    private final Set<Call> calls = Collections.synchronizedSet(new HashSet<>());

    ImageFetcher(NetworkMonitor network) {
        this.network = network;
    }

    private static synchronized OkHttpClient client() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(45, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();
        }
        return client;
    }

    /**
     * The finished file for {@code name} from an earlier run, or null.
     */
    static File existing(File dir, String name) {
        for (String format : STORED_FORMATS) {
            File file = new File(dir, name + "." + format);
            if (file.length() > 0) return file;
        }
        return null;
    }

    /**
     * Aborts the downloads in flight, used when the job is paused or cancelled.
     */
    void cancelAll() {
        synchronized (calls) {
            for (Call call : calls) call.cancel();
        }
    }

    /**
     * Downloads {@code url} to {@code dir/name.<format>}.
     *
     * @throws IOException once every attempt failed
     */
    File fetch(String url, File dir, String name, JobControl control, Runnable onWaitingForNetwork) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            control.check();
            network.awaitConnected(control, onWaitingForNetwork);
            long wait = backoff(attempt);
            try {
                return download(url, dir, name, control);
            } catch (PageCache.HttpException e) {
                control.check();
                last = e;
                // gone for good, though one more try covers a flaky CDN edge
                if ((e.code == 404 || e.code == 410) && attempt >= 2) break;
                // probably an expired signed url, the caller fetches fresh ones and tries again
                if ((e.code == 401 || e.code == 403) && attempt >= 2) break;
                if (e.code == 429 || e.code == 503) wait = Math.max(wait, retryAfter(e));
            } catch (IOException e) {
                control.check();
                last = e;
            }
            if (attempt < MAX_ATTEMPTS) control.sleep(wait);
        }
        throw last != null ? last : new IOException("Download failed");
    }

    private File download(String url, File dir, String name, JobControl control) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Referer", PageCache.REFERER)
                .header("User-Agent", PageCache.USER_AGENT)
                .build();
        File temp = new File(dir, name + ".part");
        Call call = client().newCall(request);
        calls.add(call);
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) throw new RetryAfterException(response.code(), response.header("Retry-After"));
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Empty response");

            long total = body.contentLength();
            long written = 0;
            byte[] buffer = new byte[32 * 1024];
            try (InputStream in = body.byteStream(); OutputStream out = new FileOutputStream(temp)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    control.check();
                    out.write(buffer, 0, read);
                    written += read;
                }
            }
            if (total > 0 && written != total) throw new IOException("Incomplete download");
            if (written == 0) throw new IOException("Empty image");
            return store(temp, dir, name);
        } finally {
            calls.remove(call);
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    // keeps JPEG, PNG and GIF as they are, anything else (usually WebP) is re-encoded
    private File store(File temp, File dir, String name) throws IOException {
        ImageInfo info = ImageInfo.read(temp);
        if (info != null && ImageInfo.isEpubCore(info.format)) {
            if (info.width <= 0 || info.height <= 0) throw new IOException("Damaged image");
            return moveInto(temp, new File(dir, name + "." + info.format));
        }

        Bitmap bitmap = decode(temp);
        if (bitmap == null) throw new IOException("Unsupported or damaged image");
        File converted = new File(dir, name + ".convert");
        try {
            // keep transparency when there is some, JPEG would turn it black
            boolean alpha = bitmap.hasAlpha();
            try (OutputStream out = new FileOutputStream(converted)) {
                boolean ok = bitmap.compress(alpha ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 90, out);
                if (!ok) throw new IOException("Couldn't convert image");
            }
            return moveInto(converted, new File(dir, name + "." + (alpha ? ImageInfo.PNG : ImageInfo.JPEG)));
        } finally {
            bitmap.recycle();
            //noinspection ResultOfMethodCallIgnored
            converted.delete();
        }
    }

    // long strips can be huge, fall back to a smaller copy rather than fail the page
    private static Bitmap decode(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        for (int sample = 1; sample <= 4; sample *= 2) {
            options.inSampleSize = sample;
            try {
                return BitmapFactory.decodeFile(file.getPath(), options);
            } catch (OutOfMemoryError e) {
                // try again at a smaller size
            }
        }
        return null;
    }

    private static File moveInto(File from, File to) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        to.delete();
        if (from.renameTo(to)) return to;
        // rename can fail across mount points, copy instead
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            to.delete();
            throw e;
        }
        return to;
    }

    // 1s, 2s, 4s ... capped, with jitter so parallel pages don't retry in lockstep
    private long backoff(int attempt) {
        long base = Math.min(MAX_BACKOFF_MS, 1000L << Math.min(attempt - 1, 5));
        return base / 2 + (long) (random.nextDouble() * base / 2);
    }

    private static long retryAfter(PageCache.HttpException e) {
        if (!(e instanceof RetryAfterException)) return 0;
        String header = ((RetryAfterException) e).retryAfter;
        if (header == null) return 0;
        try {
            return Math.min(120_000, Long.parseLong(header.trim()) * 1000);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static class RetryAfterException extends PageCache.HttpException {
        final String retryAfter;

        RetryAfterException(int code, String retryAfter) {
            super(code);
            this.retryAfter = retryAfter;
        }
    }
}
