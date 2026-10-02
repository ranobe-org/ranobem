package in.atulpatare.ranobem.ui.reader;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Downloads page images to a size limited disk cache, reporting progress.
 * <p>
 * Files are written to a temporary file first and only renamed into place once complete,
 * so a cached file is never partial.
 */
public class PageCache {
    private static final long MAX_BYTES = 300L * 1024 * 1024;
    private static final int TRIM_EVERY = 20;
    public static final String REFERER = "https://mangafire.to";
    public static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36";

    private static volatile PageCache instance;

    private final File dir;
    private final OkHttpClient client;
    private final AtomicInteger downloads = new AtomicInteger();

    private PageCache(Context context) {
        dir = new File(context.getCacheDir(), "reader_pages");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        new Thread(this::trim, "reader-cache-trim").start();
    }

    public static PageCache get(Context context) {
        if (instance == null) {
            synchronized (PageCache.class) {
                if (instance == null) instance = new PageCache(context.getApplicationContext());
            }
        }
        return instance;
    }

    /**
     * Returns the cached file for {@code url}, downloading it first when needed.
     * Must be called from a background thread.
     */
    public File fetch(String url, boolean refresh, ProgressListener listener) throws IOException {
        File file = new File(dir, key(url));
        if (!refresh && file.length() > 0) {
            //noinspection ResultOfMethodCallIgnored
            file.setLastModified(System.currentTimeMillis());
            return file;
        }

        Request request = new Request.Builder()
                .url(url)
                .header("Referer", REFERER)
                .header("User-Agent", USER_AGENT)
                .build();

        File temp = new File(dir, file.getName() + "." + Thread.currentThread().getId() + ".tmp");
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new HttpException(response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Empty response");

            long total = body.contentLength();
            long written = 0;
            int lastPercent = -1;
            byte[] buffer = new byte[16 * 1024];
            try (InputStream in = body.byteStream(); OutputStream out = new FileOutputStream(temp)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                    out.write(buffer, 0, read);
                    written += read;
                    if (total > 0) {
                        int percent = (int) (written * 100 / total);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            listener.onProgress(percent);
                        }
                    }
                }
            }
            if (total > 0 && written != total) throw new IOException("Incomplete download");
            if (written == 0) throw new IOException("Empty image");

            //noinspection ResultOfMethodCallIgnored
            file.delete();
            if (!temp.renameTo(file)) throw new IOException("Couldn't save page");
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }

        if (downloads.incrementAndGet() % TRIM_EVERY == 0) trim();
        return file;
    }

    public void remove(String url) {
        //noinspection ResultOfMethodCallIgnored
        new File(dir, key(url)).delete();
    }

    /**
     * Deletes the least recently used pages until the cache fits its size limit.
     */
    private synchronized void trim() {
        File[] files = dir.listFiles();
        if (files == null) return;
        long size = 0;
        for (File f : files) size += f.length();
        if (size <= MAX_BYTES) return;

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File f : files) {
            if (size <= MAX_BYTES * 0.8) break;
            long length = f.length();
            if (f.delete()) size -= length;
        }
    }

    private static String key(String url) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) builder.append(String.format("%02x", b));
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(url.hashCode());
        }
    }

    public interface ProgressListener {
        void onProgress(int percent);
    }

    public static class HttpException extends IOException {
        public final int code;

        public HttpException(int code) {
            super("HTTP " + code);
            this.code = code;
        }

        @NonNull
        @Override
        public String toString() {
            return getMessage();
        }
    }
}
