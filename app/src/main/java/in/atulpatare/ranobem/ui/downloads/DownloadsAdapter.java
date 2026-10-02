package in.atulpatare.ranobem.ui.downloads;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.databinding.ItemDownloadBinding;
import in.atulpatare.ranobem.download.DownloadJob;
import in.atulpatare.ranobem.download.DownloadText;

public class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.ViewHolder> {
    // progress ticks rebind the text and bar only, the cover stays put
    private static final Object PAYLOAD_PROGRESS = new Object();

    public enum Action {PAUSE, RESUME, RETRY, BUILD_ANYWAY, CANCEL, OPEN, SHARE, REMOVE, OPEN_MANGA}

    public interface Listener {
        void onAction(DownloadJob job, Action action);
    }

    private final List<DownloadJob> jobs = new ArrayList<>();
    private final Listener listener;

    public DownloadsAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void submit(List<DownloadJob> items) {
        boolean sameItems = items.size() == jobs.size();
        for (int i = 0; sameItems && i < items.size(); i++) {
            sameItems = items.get(i).id.equals(jobs.get(i).id);
        }
        jobs.clear();
        jobs.addAll(items);
        if (sameItems) {
            notifyItemRangeChanged(0, jobs.size(), PAYLOAD_PROGRESS);
        } else {
            notifyDataSetChanged();
        }
    }

    @Override
    public long getItemId(int position) {
        return jobs.get(position).id.hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemDownloadBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_PROGRESS)) {
            holder.bindState(jobs.get(position));
        } else {
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DownloadJob job = jobs.get(position);
        holder.binding.title.setText(job.manga.name);
        Glide.with(holder.binding.cover).load(job.manga.cover).into(holder.binding.cover);
        holder.binding.cover.setOnClickListener(v -> listener.onAction(job, Action.OPEN_MANGA));
        holder.bindState(job);
    }

    @Override
    public int getItemCount() {
        return jobs.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final ItemDownloadBinding binding;

        ViewHolder(ItemDownloadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bindState(DownloadJob job) {
            Context context = binding.getRoot().getContext();
            binding.status.setText(DownloadText.status(context, job));

            String message = job.status == DownloadJob.Status.COMPLETED || job.status == DownloadJob.Status.INCOMPLETE
                    ? null : job.message;
            if (job.status == DownloadJob.Status.COMPLETED && job.missingPages > 0) {
                message = context.getResources().getQuantityString(R.plurals.download_left_out_pages, job.missingPages, job.missingPages);
            }
            binding.message.setText(message);
            binding.message.setVisibility(message == null ? View.GONE : View.VISIBLE);

            boolean showProgress = job.status != DownloadJob.Status.COMPLETED;
            binding.progress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
            binding.percent.setVisibility(showProgress ? View.VISIBLE : View.GONE);
            if (showProgress) {
                boolean indeterminate = DownloadText.indeterminate(job);
                if (binding.progress.isIndeterminate() != indeterminate) {
                    // switching modes only works while hidden
                    binding.progress.setVisibility(View.INVISIBLE);
                    binding.progress.setIndeterminate(indeterminate);
                    binding.progress.setVisibility(View.VISIBLE);
                }
                if (!indeterminate) binding.progress.setProgressCompat(job.percent(), true);
                binding.percent.setText(String.format(Locale.getDefault(), "%d%%", job.percent()));
            }

            switch (job.status) {
                case QUEUED:
                case RUNNING:
                    set(binding.actionTertiary, 0, null, job);
                    set(binding.actionSecondary, R.string.download_cancel, Action.CANCEL, job);
                    set(binding.actionPrimary, R.string.download_pause, Action.PAUSE, job);
                    break;
                case PAUSED:
                    set(binding.actionTertiary, 0, null, job);
                    set(binding.actionSecondary, R.string.download_cancel, Action.CANCEL, job);
                    set(binding.actionPrimary, R.string.download_resume, Action.RESUME, job);
                    break;
                case INCOMPLETE:
                    set(binding.actionTertiary, R.string.download_discard, Action.CANCEL, job);
                    set(binding.actionSecondary, R.string.download_build_anyway, Action.BUILD_ANYWAY, job);
                    set(binding.actionPrimary, R.string.retry, Action.RETRY, job);
                    break;
                case FAILED:
                    set(binding.actionTertiary, 0, null, job);
                    set(binding.actionSecondary, R.string.download_discard, Action.CANCEL, job);
                    set(binding.actionPrimary, R.string.retry, Action.RETRY, job);
                    break;
                case COMPLETED:
                    set(binding.actionTertiary, R.string.download_remove, Action.REMOVE, job);
                    set(binding.actionSecondary, R.string.download_share, Action.SHARE, job);
                    set(binding.actionPrimary, R.string.download_open, Action.OPEN, job);
                    break;
            }
        }

        private void set(Button button, @StringRes int text, Action action, DownloadJob job) {
            if (action == null) {
                button.setVisibility(View.GONE);
                return;
            }
            button.setVisibility(View.VISIBLE);
            button.setText(text);
            button.setOnClickListener(v -> listener.onAction(job, action));
        }
    }
}
