package in.atulpatare.ranobem.download;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.ui.reader.PageCache;

/**
 * Runs one job from wherever it left off: fetches the chapter list, downloads every page of every
 * chapter into the work folder and finally builds the EPUB. Finished chapters are remembered
 * in the job and finished pages on disk, so a resumed job never downloads anything twice.
 * <p>
 * Network failures are retried with backoff, a lost connection is waited out without using up
 * retries, and pages that still fail are fetched again with fresh urls before giving up on them.
 */
class JobRunner {
    private static final int PAGE_THREADS = 3;
    private static final int RESOLVE_ATTEMPTS = 5;
    private static final int CHAPTER_ROUNDS = 3;
    private static final long MIN_FREE_BYTES = 150L * 1024 * 1024;

    interface Listener {
        // frequent, may be throttled
        void onProgress(DownloadJob job);

        // something worth saving happened
        void onChanged(DownloadJob job);
    }

    /**
     * An error retrying won't fix.
     */
    static class FatalException extends Exception {
        FatalException(String message) {
            super(message);
        }
    }

    private final Context context;
    private final DownloadJob job;
    private final JobControl control;
    private final File workDir;
    private final Listener listener;
    private final NetworkMonitor network;
    private final ImageFetcher fetcher;
    private final ChapterResolver resolver;

    JobRunner(Context context, DownloadJob job, JobControl control, File workDir, Listener listener) {
        this.context = context.getApplicationContext();
        this.job = job;
        this.control = control;
        this.workDir = workDir;
        this.listener = listener;
        this.network = new NetworkMonitor(context);
        this.fetcher = new ImageFetcher(network);
        this.resolver = new ChapterResolver(job.manga);
    }

    void run() throws Exception {
        if (!workDir.isDirectory() && !workDir.mkdirs()) throw new IOException("Couldn't create " + workDir);
        control.setOnStop(fetcher::cancelAll);

        if (job.chapterCount() == 0) fetchChapters();
        recheckFinishedChapters();
        downloadCover();

        job.phase = DownloadJob.Phase.PAGES;
        listener.onChanged(job);
        ExecutorService pool = Executors.newFixedThreadPool(PAGE_THREADS);
        try {
            List<DownloadJob.Item> items;
            synchronized (job.chapters) {
                items = new ArrayList<>(job.chapters);
            }
            for (int i = 0; i < items.size(); i++) {
                DownloadJob.Item item = items.get(i);
                if (item.done) continue;
                ensureSpace(workDir.getUsableSpace(), MIN_FREE_BYTES);
                job.currentChapter = i;
                downloadChapter(i, item, pool);
                listener.onChanged(job);
            }
        } finally {
            pool.shutdownNow();
            job.currentChapter = -1;
            job.currentPagesDone = 0;
            job.currentPagesTotal = 0;
        }

        int missing = job.missingCount();
        if (missing > 0 && !job.allowMissing) {
            job.missingPages = missing;
            job.status = DownloadJob.Status.INCOMPLETE;
            job.message = context.getResources().getQuantityString(R.plurals.download_missing_pages, missing, missing);
            return;
        }
        build();
    }

    private void fetchChapters() throws Exception {
        job.phase = DownloadJob.Phase.CHAPTERS;
        listener.onChanged(job);
        List<Chapter> all = withRetries(resolver::chapters);
        List<DownloadJob.Item> picked = new ArrayList<>();
        for (Chapter c : all) {
            if (job.inRange(c.index)) picked.add(DownloadJob.Item.of(c));
        }
        if (picked.isEmpty()) {
            throw new FatalException(context.getString(job.hasRange() ? R.string.download_no_chapters_in_range : R.string.download_no_chapters));
        }
        synchronized (job.chapters) {
            job.chapters.clear();
            job.chapters.addAll(picked);
        }
        listener.onChanged(job);
    }

    // the work folder is in the cache, Android may have cleared some of it while the job was paused
    private void recheckFinishedChapters() {
        List<DownloadJob.Item> items;
        synchronized (job.chapters) {
            items = new ArrayList<>(job.chapters);
        }
        boolean changed = false;
        for (int i = 0; i < items.size(); i++) {
            DownloadJob.Item item = items.get(i);
            if (!item.done || item.pageCount <= 0) continue;
            File dir = chapterDir(i);
            int present = 0;
            for (int p = 0; p < item.pageCount; p++) {
                if (ImageFetcher.existing(dir, pageName(p)) != null) present++;
            }
            if (present < item.pageCount - item.missing) {
                item.done = false;
                item.missing = 0;
                changed = true;
            }
        }
        if (changed) listener.onChanged(job);
    }

    // a missing cover isn't worth failing over, the book falls back to its first page
    private void downloadCover() {
        String cover = job.manga.cover;
        if (cover == null || cover.trim().isEmpty() || ImageFetcher.existing(workDir, "cover") != null) return;
        try {
            fetcher.fetch(cover.trim(), workDir, "cover", control, this::waitingForNetwork);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void downloadChapter(int position, DownloadJob.Item item, ExecutorService pool) throws Exception {
        File dir = chapterDir(position);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Couldn't create " + dir);
        job.currentPagesDone = 0;
        job.currentPagesTotal = Math.max(0, item.pageCount);
        listener.onProgress(job);

        for (int round = 1; round <= CHAPTER_ROUNDS; round++) {
            boolean lastRound = round == CHAPTER_ROUNDS;
            List<String> urls;
            try {
                // fresh urls every round, signed ones may have expired since the last
                urls = withRetries(() -> resolver.pages(item));
            } catch (JobControl.StoppedException e) {
                throw e;
            } catch (Exception e) {
                job.message = describe(context, e);
                if (!lastRound) continue;
                item.missing = Math.max(1, item.pageCount);
                item.done = true;
                return;
            }
            item.pageCount = urls.size();
            job.currentPagesTotal = urls.size();

            List<Integer> todo = new ArrayList<>();
            for (int p = 0; p < urls.size(); p++) {
                if (ImageFetcher.existing(dir, pageName(p)) == null) todo.add(p);
            }
            job.currentPagesDone = urls.size() - todo.size();
            listener.onProgress(job);

            AtomicReference<Exception> error = new AtomicReference<>();
            List<Future<?>> futures = new ArrayList<>();
            for (int p : todo) {
                String url = urls.get(p);
                futures.add(pool.submit(() -> {
                    try {
                        fetcher.fetch(url, dir, pageName(p), control, this::waitingForNetwork);
                        job.waitingForNetwork = false;
                        synchronized (job) {
                            job.currentPagesDone++;
                        }
                        listener.onProgress(job);
                    } catch (IOException e) {
                        error.set(e);
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) await(future);

            int failed = 0;
            for (int p = 0; p < urls.size(); p++) {
                if (ImageFetcher.existing(dir, pageName(p)) == null) failed++;
            }
            if (failed == 0) {
                item.missing = 0;
                item.done = true;
                job.message = null;
                return;
            }
            if (error.get() != null) job.message = describe(context, error.get());
            if (lastRound) {
                item.missing = failed;
                item.done = true;
                return;
            }
            control.sleep(5_000L * round);
        }
    }

    private void build() throws Exception {
        job.phase = DownloadJob.Phase.BUILDING;
        job.buildDone = 0;
        job.buildTotal = 0;
        job.message = null;
        listener.onChanged(job);

        EpubBuilder.Book book = new EpubBuilder.Book();
        book.id = "urn:uuid:" + job.id;
        book.title = title();
        book.author = job.manga.author;
        book.description = job.manga.summary;
        book.language = resolver.language();
        book.source = job.manga.url;
        book.cover = ImageFetcher.existing(workDir, "cover");

        long bytes = book.cover == null ? 0 : book.cover.length();
        List<DownloadJob.Item> items;
        synchronized (job.chapters) {
            items = new ArrayList<>(job.chapters);
        }
        for (int i = 0; i < items.size(); i++) {
            DownloadJob.Item item = items.get(i);
            EpubBuilder.Section section = new EpubBuilder.Section(chapterTitle(item));
            File dir = chapterDir(i);
            for (int p = 0; p < item.pageCount; p++) {
                File page = ImageFetcher.existing(dir, pageName(p));
                if (page == null) continue;
                section.pages.add(page);
                bytes += page.length();
            }
            book.sections.add(section);
        }
        // the book is about as big as its images, leave some room on top
        ensureSpace(EpubOutput.freeSpace(context), bytes + bytes / 20 + 20L * 1024 * 1024);

        String fileName = EpubOutput.fileName(book.title);
        EpubOutput output = EpubOutput.create(context, fileName);
        CountingOutputStream counter;
        Uri uri;
        try {
            counter = new CountingOutputStream(output.open());
            try (OutputStream out = new BufferedOutputStream(counter, 64 * 1024)) {
                EpubBuilder.write(book, out, (done, total) -> {
                    control.check();
                    job.buildDone = done;
                    job.buildTotal = total;
                    listener.onProgress(job);
                });
            }
            uri = output.commit();
        } catch (Throwable t) {
            output.abort();
            throw t;
        }

        job.outputUri = uri.toString();
        job.outputName = fileName;
        job.outputSize = counter.count;
        job.missingPages = job.missingCount();
        job.message = null;
        job.status = DownloadJob.Status.COMPLETED;
    }

    private <T> T withRetries(Callable<T> task) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= RESOLVE_ATTEMPTS; attempt++) {
            control.check();
            network.awaitConnected(control, this::waitingForNetwork);
            try {
                T result = task.call();
                job.waitingForNetwork = false;
                return result;
            } catch (JobControl.StoppedException e) {
                throw e;
            } catch (Exception e) {
                control.check();
                last = e;
            }
            if (attempt < RESOLVE_ATTEMPTS) control.sleep(Math.min(30_000L, 2000L << (attempt - 1)));
        }
        throw last;
    }

    private void await(Future<?> future) throws Exception {
        try {
            future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof JobControl.StoppedException) throw (JobControl.StoppedException) cause;
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JobControl.StoppedException();
        }
    }

    private void waitingForNetwork() {
        job.waitingForNetwork = true;
        listener.onProgress(job);
    }

    // pausing beats failing, the user can free up space and resume
    private void ensureSpace(long free, long needed) {
        if (free >= needed) return;
        control.stop(JobControl.Stop.PAUSE, context.getString(R.string.download_storage_full));
        control.check();
    }

    private File chapterDir(int position) {
        return new File(workDir, String.format(Locale.ROOT, "c%04d", position + 1));
    }

    private static String pageName(int page) {
        return String.format(Locale.ROOT, "p%04d", page + 1);
    }

    private String title() {
        String name = job.manga.name == null || job.manga.name.trim().isEmpty() ? "Manga" : job.manga.name.trim();
        if (!job.hasRange()) return name;
        List<DownloadJob.Item> items;
        synchronized (job.chapters) {
            items = new ArrayList<>(job.chapters);
        }
        if (items.isEmpty()) return name;
        String first = formatIndex(items.get(0).index);
        String last = formatIndex(items.get(items.size() - 1).index);
        return context.getString(R.string.download_book_title_range, name, first.equals(last) ? first : first + "-" + last);
    }

    private String chapterTitle(DownloadJob.Item item) {
        if (item.name != null && !item.name.trim().isEmpty()) return item.name.trim();
        return context.getString(R.string.reader_chapter_title, formatIndex(item.index));
    }

    static String formatIndex(float index) {
        if (index == Math.rint(index)) return String.valueOf((long) index);
        return String.valueOf(index);
    }

    /**
     * A short, user facing explanation of a failure.
     */
    static String describe(Context context, Throwable e) {
        if (e instanceof FatalException) return e.getMessage();
        if (e instanceof UnknownHostException || e instanceof ConnectException) {
            return context.getString(R.string.reader_error_offline);
        }
        if (e instanceof SocketTimeoutException) return context.getString(R.string.reader_error_timeout);
        if (e instanceof PageCache.HttpException) {
            int code = ((PageCache.HttpException) e).code;
            if (code == 404 || code == 410) return context.getString(R.string.reader_error_not_found);
            if (code == 401 || code == 403) return context.getString(R.string.reader_error_refused);
            if (code >= 500 || code == 429) return context.getString(R.string.reader_error_server);
        }
        if (e instanceof OutOfMemoryError) return context.getString(R.string.reader_error_memory);
        String message = e.getLocalizedMessage();
        return message == null || message.trim().isEmpty() ? context.getString(R.string.reader_error_unknown) : message;
    }

    private static class CountingOutputStream extends FilterOutputStream {
        long count;

        CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }
    }
}
