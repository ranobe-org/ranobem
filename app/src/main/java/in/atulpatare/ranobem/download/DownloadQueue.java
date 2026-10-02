package in.atulpatare.ranobem.download;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;

/**
 * Every EPUB download, newest first. Jobs run one at a time on a background thread while
 * {@link DownloadService} keeps the app alive and shows progress. The list is saved after
 * every meaningful change so jobs survive the app being killed, they come back paused.
 * <p>
 * All public methods are called on the main thread.
 */
public class DownloadQueue {
    private static final long PROGRESS_THROTTLE_MS = 300;
    // job level retries for errors the runner didn't handle itself, before giving up
    private static final long[] AUTO_RETRY_DELAYS_MS = {15_000, 60_000, 180_000};

    @SuppressLint("StaticFieldLeak")
    private static DownloadQueue instance;

    private final Context context;
    private final File stateFile;
    private final File workRoot;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<DownloadJob> jobs = new CopyOnWriteArrayList<>();
    private final MutableLiveData<List<DownloadJob>> live = new MutableLiveData<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Runnable dispatch = this::dispatch;

    private volatile DownloadJob running;
    private volatile JobControl control;
    private boolean draining;
    private boolean dispatchPending;

    private DownloadQueue(Context context) {
        this.context = context.getApplicationContext();
        File stateDir = new File(this.context.getFilesDir(), "downloads");
        //noinspection ResultOfMethodCallIgnored
        stateDir.mkdirs();
        stateFile = new File(stateDir, "jobs.json");
        workRoot = new File(this.context.getCacheDir(), "epub");
        restore();
        live.setValue(new ArrayList<>(jobs));
    }

    @MainThread
    public static DownloadQueue get(Context context) {
        if (instance == null) instance = new DownloadQueue(context);
        return instance;
    }

    public LiveData<List<DownloadJob>> jobs() {
        return live;
    }

    public List<DownloadJob> snapshot() {
        return new ArrayList<>(jobs);
    }

    /**
     * Called on the main thread after any change, for {@link DownloadService}.
     */
    void addListener(Runnable listener) {
        listeners.add(listener);
    }

    void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    boolean hasActive() {
        for (DownloadJob job : jobs) if (job.isActive()) return true;
        return false;
    }

    @Nullable
    public DownloadJob find(String id) {
        for (DownloadJob job : jobs) if (job.id.equals(id)) return job;
        return null;
    }

    /**
     * The newest job for this manga, if any.
     */
    @Nullable
    public DownloadJob findForManga(Manga manga) {
        for (DownloadJob job : jobs) {
            if (job.manga.id.equals(manga.id) && job.manga.sourceId == manga.sourceId) return job;
        }
        return null;
    }

    /**
     * Queues a download of the chapters numbered {@code from} to {@code to}, NaN for no limit.
     */
    @Nullable
    public DownloadJob enqueue(Manga manga, float from, float to) {
        if (Config.isFree()) return null;
        DownloadJob job = new DownloadJob(manga, from, to);
        jobs.add(0, job);
        changed();
        kick();
        return job;
    }

    public void pause(String id) {
        DownloadJob job = find(id);
        if (job == null) return;
        synchronized (this) {
            if (!job.isActive()) return;
            job.status = DownloadJob.Status.PAUSED;
            job.message = null;
            job.waitingForNetwork = false;
            if (job == running && control != null) control.stop(JobControl.Stop.PAUSE, null);
        }
        changed();
    }

    /**
     * Picks a paused, failed or incomplete job up where it left off.
     */
    public void resume(String id) {
        DownloadJob job = find(id);
        if (job == null || job.isActive() || job.status == DownloadJob.Status.COMPLETED) return;
        if (job.status == DownloadJob.Status.INCOMPLETE) {
            // try the missing pages again
            synchronized (job.chapters) {
                for (DownloadJob.Item item : job.chapters) {
                    if (item.missing > 0) {
                        item.done = false;
                        item.missing = 0;
                    }
                }
            }
        }
        job.status = DownloadJob.Status.QUEUED;
        job.message = null;
        changed();
        kick();
    }

    /**
     * Builds the book from what was downloaded, leaving out pages that couldn't be fetched.
     */
    public void buildAnyway(String id) {
        DownloadJob job = find(id);
        if (job == null || job.status != DownloadJob.Status.INCOMPLETE) return;
        job.allowMissing = true;
        job.status = DownloadJob.Status.QUEUED;
        job.message = null;
        changed();
        kick();
    }

    public void resumeAll() {
        for (DownloadJob job : jobs) {
            if (job.status == DownloadJob.Status.PAUSED) resume(job.id);
        }
    }

    /**
     * Stops every job, e.g. when the system ends the foreground service.
     */
    void pauseAll(String reason) {
        for (DownloadJob job : jobs) {
            if (!job.isActive()) continue;
            pause(job.id);
            job.message = reason;
        }
        changed();
    }

    /**
     * Stops the job and throws away what it downloaded. A finished book stays in Downloads.
     */
    public void cancel(String id) {
        DownloadJob job = find(id);
        if (job == null) return;
        synchronized (this) {
            jobs.remove(job);
            if (job == running && control != null) {
                // the worker cleans up once the runner has stopped
                control.stop(JobControl.Stop.CANCEL, null);
            } else {
                io.execute(() -> deleteRecursively(workDir(job)));
            }
        }
        new DownloadNotifications(context).clear(job);
        changed();
    }

    private File workDir(DownloadJob job) {
        return new File(workRoot, job.id);
    }

    @MainThread
    private void kick() {
        if (hasActive()) DownloadService.start(context);
        if (draining) return;
        draining = true;
        worker.execute(this::drain);
    }

    // worker thread: runs queued jobs, oldest first, until none are left
    private void drain() {
        while (true) {
            DownloadJob next = null;
            JobControl jobControl = new JobControl();
            // picked and marked running under the lock so a pause or cancel can't slip in between
            synchronized (this) {
                for (int i = jobs.size() - 1; i >= 0; i--) {
                    DownloadJob job = jobs.get(i);
                    if (job.status == DownloadJob.Status.QUEUED) {
                        next = job;
                        break;
                    }
                }
                if (next != null) {
                    next.status = DownloadJob.Status.RUNNING;
                    next.waitingForNetwork = false;
                    running = next;
                    control = jobControl;
                }
            }
            if (next == null) {
                // re-check on the main thread, which is where jobs get queued
                main.post(() -> {
                    draining = false;
                    for (DownloadJob job : jobs) {
                        if (job.status == DownloadJob.Status.QUEUED) {
                            kick();
                            return;
                        }
                    }
                });
                return;
            }
            // a stray interrupt from an earlier job must not cut this one short
            //noinspection ResultOfMethodCallIgnored
            Thread.interrupted();
            runJob(next, jobControl);
        }
    }

    private void runJob(DownloadJob job, JobControl jobControl) {
        postChanged();

        JobRunner.Listener listener = new JobRunner.Listener() {
            @Override
            public void onProgress(DownloadJob j) {
                postProgress();
            }

            @Override
            public void onChanged(DownloadJob j) {
                postChanged();
            }
        };

        for (int attempt = 0; ; attempt++) {
            try {
                new JobRunner(context, job, jobControl, workDir(job), listener).run();
                // the runner sets COMPLETED or INCOMPLETE
                if (job.status == DownloadJob.Status.COMPLETED) deleteRecursively(workDir(job));
                break;
            } catch (JobControl.StoppedException e) {
                if (jobControl.stopped() == null) {
                    // interrupted without anyone asking, treat it like any other error
                    if (retryLater(job, jobControl, attempt, e)) continue;
                }
                break;
            } catch (JobRunner.FatalException e) {
                fail(job, e);
                break;
            } catch (Throwable t) {
                t.printStackTrace();
                if (retryLater(job, jobControl, attempt, t)) continue;
                break;
            }
        }

        synchronized (this) {
            if (jobControl.stopped() == JobControl.Stop.PAUSE && job.status == DownloadJob.Status.RUNNING) {
                // the runner paused itself, e.g. storage ran out
                job.status = DownloadJob.Status.PAUSED;
                job.message = jobControl.reason();
            }
            if (jobControl.stopped() == null && job.status == DownloadJob.Status.RUNNING) {
                // never leave a job looking busy when nothing works on it
                job.status = DownloadJob.Status.FAILED;
                job.message = context.getString(R.string.reader_error_unknown);
            }
            job.waitingForNetwork = false;
            running = null;
            control = null;
        }
        if (jobControl.stopped() == JobControl.Stop.CANCEL) deleteRecursively(workDir(job));
        postChanged();
    }

    // waits and asks for another go, or marks the job failed once out of retries
    private boolean retryLater(DownloadJob job, JobControl jobControl, int attempt, Throwable t) {
        if (jobControl.stopped() != null) return false;
        if (attempt >= AUTO_RETRY_DELAYS_MS.length) {
            fail(job, t);
            return false;
        }
        long delay = AUTO_RETRY_DELAYS_MS[attempt];
        job.message = context.getString(R.string.download_retrying_in, delay / 1000, JobRunner.describe(context, t));
        postChanged();
        try {
            jobControl.sleep(delay);
        } catch (JobControl.StoppedException e) {
            return false;
        }
        job.message = null;
        return true;
    }

    private synchronized void fail(DownloadJob job, Throwable t) {
        if (job.status != DownloadJob.Status.RUNNING) return;
        job.status = DownloadJob.Status.FAILED;
        job.message = JobRunner.describe(context, t);
    }

    private void postChanged() {
        main.post(this::changed);
    }

    private void postProgress() {
        main.post(() -> {
            if (dispatchPending) return;
            dispatchPending = true;
            main.postDelayed(dispatch, PROGRESS_THROTTLE_MS);
        });
    }

    @MainThread
    private void changed() {
        save();
        main.removeCallbacks(dispatch);
        dispatch();
    }

    @MainThread
    private void dispatch() {
        dispatchPending = false;
        live.setValue(new ArrayList<>(jobs));
        for (Runnable listener : listeners) listener.run();
    }

    private void save() {
        String json;
        try {
            JSONArray array = new JSONArray();
            for (DownloadJob job : jobs) array.put(job.toJson());
            json = new JSONObject().put("version", 1).put("jobs", array).toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        io.execute(() -> write(json));
    }

    // write then rename, a crash mid write never loses the previous state
    private void write(String json) {
        File temp = new File(stateFile.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(temp)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        if (!temp.renameTo(stateFile)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private void restore() {
        if (!stateFile.isFile()) return;
        try (InputStream in = new FileInputStream(stateFile)) {
            byte[] data = new byte[(int) stateFile.length()];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) break;
                read += n;
            }
            JSONArray array = new JSONObject(new String(data, 0, read, StandardCharsets.UTF_8)).getJSONArray("jobs");
            for (int i = 0; i < array.length(); i++) {
                try {
                    DownloadJob job = DownloadJob.fromJson(array.getJSONObject(i));
                    if (job.isActive()) {
                        // the app was killed mid download, wait for the user to carry on
                        job.status = DownloadJob.Status.PAUSED;
                        job.message = context.getString(R.string.download_interrupted);
                    }
                    jobs.add(job);
                } catch (JSONException | IllegalArgumentException e) {
                    // skip a job we can't read rather than lose them all
                    e.printStackTrace();
                }
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        // leftovers of jobs that no longer exist
        io.execute(() -> {
            File[] dirs = workRoot.listFiles();
            if (dirs == null) return;
            for (File dir : dirs) {
                if (find(dir.getName()) == null) deleteRecursively(dir);
            }
        });
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
