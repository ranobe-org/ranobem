package in.atulpatare.ranobem.download;

import android.content.Context;
import android.text.format.Formatter;

import in.atulpatare.ranobem.R;

/**
 * The one line status of a job, shared by the notification and the downloads screen.
 */
public final class DownloadText {
    private DownloadText() {
    }

    public static String status(Context context, DownloadJob job) {
        switch (job.status) {
            case QUEUED:
                return context.getString(R.string.download_status_queued);
            case PAUSED:
                return context.getString(R.string.download_status_paused);
            case FAILED:
                return context.getString(R.string.download_status_failed);
            case INCOMPLETE:
                return context.getResources().getQuantityString(R.plurals.download_missing_pages, job.missingPages, job.missingPages);
            case COMPLETED:
                return context.getString(R.string.download_status_completed, Formatter.formatShortFileSize(context, job.outputSize));
            default:
                return running(context, job);
        }
    }

    private static String running(Context context, DownloadJob job) {
        if (job.waitingForNetwork) return context.getString(R.string.download_status_waiting_network);
        switch (job.phase) {
            case CHAPTERS:
                return context.getString(R.string.download_status_chapters);
            case BUILDING:
                return context.getString(R.string.download_status_building, job.percent());
            default:
                int total = job.chapterCount();
                int chapter = Math.min(total, (job.currentChapter >= 0 ? job.currentChapter : job.chaptersDone()) + 1);
                if (job.currentPagesTotal > 0) {
                    return context.getString(R.string.download_status_pages, chapter, total, job.currentPagesDone, job.currentPagesTotal);
                }
                return context.getString(R.string.download_status_chapter, chapter, total);
        }
    }

    /**
     * True while the progress can't be told, the bar should spin.
     */
    public static boolean indeterminate(DownloadJob job) {
        return job.status == DownloadJob.Status.RUNNING && (job.waitingForNetwork || job.phase == DownloadJob.Phase.CHAPTERS);
    }
}
