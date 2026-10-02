package in.atulpatare.ranobem.ui.downloads;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.databinding.ActivityDownloadsBinding;
import in.atulpatare.ranobem.download.DownloadJob;
import in.atulpatare.ranobem.download.DownloadQueue;
import in.atulpatare.ranobem.download.EpubOutput;
import in.atulpatare.ranobem.ui.details.DetailsActivity;

/**
 * Every EPUB download with its progress, and the controls to pause, resume, cancel or open it.
 */
public class DownloadsActivity extends AppCompatActivity implements DownloadsAdapter.Listener {
    private ActivityDownloadsBinding binding;
    private DownloadQueue queue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityDownloadsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        DownloadsAdapter adapter = new DownloadsAdapter(this);
        binding.list.setLayoutManager(new LinearLayoutManager(this));
        binding.list.setItemAnimator(null);
        binding.list.setAdapter(adapter);

        queue = DownloadQueue.get(this);
        queue.jobs().observe(this, jobs -> {
            adapter.submit(jobs);
            showEmpty(jobs);
        });
    }

    private void showEmpty(List<DownloadJob> jobs) {
        binding.empty.setVisibility(jobs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onAction(DownloadJob job, DownloadsAdapter.Action action) {
        switch (action) {
            case PAUSE:
                queue.pause(job.id);
                break;
            case RESUME:
            case RETRY:
                queue.resume(job.id);
                break;
            case BUILD_ANYWAY:
                queue.buildAnyway(job.id);
                break;
            case CANCEL:
                confirmCancel(job);
                break;
            case REMOVE:
                queue.cancel(job.id);
                break;
            case OPEN:
                open(job);
                break;
            case SHARE:
                share(job);
                break;
            case OPEN_MANGA:
                startActivity(new Intent(this, DetailsActivity.class).putExtra(Config.KEY_MANGA, job.manga));
                break;
        }
    }

    private void confirmCancel(DownloadJob job) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.download_cancel_title)
                .setMessage(R.string.download_cancel_message)
                .setPositiveButton(R.string.download_cancel, (d, w) -> queue.cancel(job.id))
                .setNegativeButton(R.string.download_keep, null)
                .show();
    }

    private void open(DownloadJob job) {
        try {
            startActivity(EpubOutput.viewIntent(Uri.parse(job.outputUri)));
        } catch (ActivityNotFoundException e) {
            Snackbar.make(binding.getRoot(), R.string.download_no_reader, Snackbar.LENGTH_LONG).show();
        }
    }

    private void share(DownloadJob job) {
        startActivity(Intent.createChooser(EpubOutput.shareIntent(Uri.parse(job.outputUri), job.manga.name), job.outputName));
    }
}
