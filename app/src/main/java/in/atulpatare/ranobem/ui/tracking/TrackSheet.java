package in.atulpatare.ranobem.ui.tracking;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.database.AppDatabase;
import in.atulpatare.ranobem.databinding.FragmentTrackBinding;
import in.atulpatare.ranobem.databinding.ItemTrackResultBinding;
import in.atulpatare.ranobem.model.TrackedManga;
import in.atulpatare.ranobem.tracking.ScoreFormat;
import in.atulpatare.ranobem.tracking.TrackDates;
import in.atulpatare.ranobem.tracking.TrackMedia;
import in.atulpatare.ranobem.tracking.TrackStatus;
import in.atulpatare.ranobem.tracking.Tracker;
import in.atulpatare.ranobem.tracking.Trackers;
import in.atulpatare.ranobem.tracking.Tracking;

/**
 * Links the series to an entry on one tracker, or shows the linked entry: status, progress, score,
 * dates, notes and privacy, as far as the tracker keeps them.
 */
public class TrackSheet extends BottomSheetDialogFragment {
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
    private final List<TrackMedia> results = new ArrayList<>();
    private FragmentTrackBinding binding;
    private Manga manga;
    private Tracker tracker;
    private TrackedManga tracked;
    private boolean busy = false;
    private boolean searched = false;
    private boolean pulled = false;

    public static TrackSheet newInstance(Manga manga, Tracker tracker) {
        Bundle args = new Bundle();
        args.putParcelable(Config.KEY_MANGA, manga);
        args.putInt(Config.KEY_TRACKER, tracker.id);
        TrackSheet sheet = new TrackSheet();
        sheet.setArguments(args);
        return sheet;
    }

    // region labels, shared with the details page

    public static String statusLabel(Context context, String status) {
        if (status == null) return context.getString(R.string.tracking_status_planning);
        switch (status) {
            case TrackStatus.READING:
                return context.getString(R.string.tracking_status_current);
            case TrackStatus.COMPLETED:
                return context.getString(R.string.tracking_status_completed);
            case TrackStatus.PAUSED:
                return context.getString(R.string.tracking_status_paused);
            case TrackStatus.DROPPED:
                return context.getString(R.string.tracking_status_dropped);
            case TrackStatus.REREADING:
                return context.getString(R.string.tracking_status_repeating);
            case TrackStatus.PLANNING:
            default:
                return context.getString(R.string.tracking_status_planning);
        }
    }

    public static String progressLabel(Context context, TrackedManga tracked) {
        return tracked.totalChapters > 0
                ? context.getString(R.string.tracking_progress_of, tracked.progress, tracked.totalChapters)
                : context.getString(R.string.tracking_progress, tracked.progress);
    }

    // status, progress and score in one line, e.g. "Reading · Chapter 12 of 80 · 8/10"
    public static String summary(Context context, Tracker tracker, TrackedManga tracked) {
        List<String> parts = new ArrayList<>();
        parts.add(statusLabel(context, tracked.status));
        parts.add(progressLabel(context, tracked));
        String score = tracker.scoreFormat().label(tracked.score);
        if (!score.isEmpty()) parts.add(score);
        return String.join(" · ", parts);
    }

    // manga, manhwa or manhua, which is how readers tell same-named entries apart
    private static String kindLabel(Context context, TrackMedia media) {
        switch (media.kind) {
            case TrackMedia.KIND_ONE_SHOT:
                return context.getString(R.string.tracking_kind_one_shot);
            case TrackMedia.KIND_MANHWA:
                return context.getString(R.string.tracking_kind_manhwa);
            case TrackMedia.KIND_MANHUA:
                return context.getString(R.string.tracking_kind_manhua);
            case TrackMedia.KIND_MANGA:
            default:
                return context.getString(R.string.tracking_kind_manga);
        }
    }

    @Nullable
    private static String publishingLabel(Context context, @Nullable String publishing) {
        if (publishing == null) return null;
        switch (publishing) {
            case TrackMedia.PUBLISHING_RELEASING:
                return context.getString(R.string.tracking_publishing_releasing);
            case TrackMedia.PUBLISHING_FINISHED:
                return context.getString(R.string.tracking_publishing_finished);
            case TrackMedia.PUBLISHING_HIATUS:
                return context.getString(R.string.tracking_publishing_hiatus);
            case TrackMedia.PUBLISHING_CANCELLED:
                return context.getString(R.string.tracking_publishing_cancelled);
            case TrackMedia.PUBLISHING_NOT_YET:
                return context.getString(R.string.tracking_publishing_not_yet);
            default:
                return null;
        }
    }

    // endregion

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            manga = getArguments().getParcelable(Config.KEY_MANGA);
            tracker = Trackers.get(getArguments().getInt(Config.KEY_TRACKER, TrackedManga.TRACKER_ANILIST));
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentTrackBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (manga == null || tracker == null) {
            dismiss();
            return;
        }
        binding.title.setText(tracker.name);
        binding.loginMessage.setText(getString(R.string.tracker_login_needed, tracker.name));
        binding.searchLayout.setHint(getString(R.string.tracking_search_hint, tracker.name));
        binding.results.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.results.setAdapter(new ResultAdapter());
        binding.searchField.setText(manga.name);
        binding.searchField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            search();
            return true;
        });
        binding.login.setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, tracker.loginUri()));
            dismiss();
        });
        binding.status.setOnClickListener(v -> pickStatus());
        binding.scoreOption.showChevron();
        binding.scoreOption.setOnClickListener(v -> pickScore());
        binding.startedOption.setOnClickListener(v -> pickDate(true));
        binding.finishedOption.setOnClickListener(v -> pickDate(false));
        binding.notesOption.setOnClickListener(v -> editNotes());
        binding.privateOption.setOnClickListener(v -> {
            if (tracked != null) Tracking.setPrivate(manga.id, tracker.id, !tracked.isPrivate);
        });
        binding.startedOption.setVisibility(tracker.supportsDates() ? View.VISIBLE : View.GONE);
        binding.finishedOption.setVisibility(tracker.supportsDates() ? View.VISIBLE : View.GONE);
        binding.notesOption.setVisibility(tracker.supportsNotes() ? View.VISIBLE : View.GONE);
        binding.privateOption.setVisibility(tracker.supportsPrivate() ? View.VISIBLE : View.GONE);
        binding.sync.setOnClickListener(v -> refresh());
        binding.unlink.setOnClickListener(v -> unlink());
        binding.open.setOnClickListener(v -> {
            if (tracked != null && tracked.siteUrl != null && !tracked.siteUrl.isEmpty()) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(tracked.siteUrl)));
            }
        });

        AppDatabase.getDatabase().trackedMangaDao().observe(manga.id, tracker.id)
                .observe(getViewLifecycleOwner(), this::show);
    }

    private void show(TrackedManga item) {
        tracked = item;
        boolean loggedIn = tracker.isLoggedIn();
        binding.loginGroup.setVisibility(!loggedIn && item == null ? View.VISIBLE : View.GONE);
        binding.linkedGroup.setVisibility(item != null ? View.VISIBLE : View.GONE);
        binding.searchGroup.setVisibility(loggedIn && item == null ? View.VISIBLE : View.GONE);

        if (item != null && loggedIn && !pulled) pullQuietly();

        if (item != null) {
            binding.linkedTitle.setText(item.title);
            String progress = progressLabel(requireContext(), item);
            if (item.repeatCount > 0) {
                progress += " · " + getResources().getQuantityString(R.plurals.tracking_rereads, item.repeatCount, item.repeatCount);
            }
            if (item.pendingSync) progress += " · " + getString(R.string.tracking_pending);
            binding.linkedProgress.setText(progress);
            binding.status.setText(statusLabel(requireContext(), item.status));
            String score = tracker.scoreFormat().label(item.score);
            binding.scoreOption.setSubtitle(score.isEmpty() ? getString(R.string.tracking_no_score) : score);
            binding.startedOption.setSubtitle(dateLabel(item.startedAt));
            binding.finishedOption.setSubtitle(dateLabel(item.completedAt));
            binding.notesOption.setSubtitle(item.notes != null ? item.notes : getString(R.string.tracking_no_notes));
            binding.privateOption.setChecked(item.isPrivate);
            binding.open.setEnabled(item.siteUrl != null && !item.siteUrl.isEmpty());
            Glide.with(this).load(item.cover).centerCrop().into(binding.linkedCover);
        } else if (loggedIn && !searched) {
            search();
        }
    }

    private String dateLabel(int date) {
        return date == 0 ? getString(R.string.tracking_no_date) : TrackDates.format(date);
    }

    // region linking

    private void search() {
        if (binding == null || binding.searchField.getText() == null) return;
        String query = binding.searchField.getText().toString().trim();
        if (query.isEmpty()) return;
        searched = true;
        setBusy(true);
        binding.searchMessage.setVisibility(View.GONE);
        searchExecutor.execute(() -> {
            List<TrackMedia> found = null;
            Exception error = null;
            try {
                found = tracker.search(query);
            } catch (Exception e) {
                error = e;
            }
            List<TrackMedia> items = found;
            Exception failure = error;
            if (binding == null) return;
            binding.getRoot().post(() -> showResults(items, failure));
        });
    }

    private void showResults(List<TrackMedia> items, Exception error) {
        if (binding == null) return;
        setBusy(false);
        results.clear();
        if (items != null) results.addAll(items);
        RecyclerView.Adapter<?> adapter = binding.results.getAdapter();
        if (adapter != null) adapter.notifyDataSetChanged();

        if (error instanceof Tracker.NotLoggedInException) {
            show(tracked);
        } else if (error != null) {
            binding.searchMessage.setText(getString(R.string.tracking_search_failed, String.valueOf(error.getMessage())));
            binding.searchMessage.setVisibility(View.VISIBLE);
        } else if (results.isEmpty()) {
            binding.searchMessage.setText(R.string.tracking_search_empty);
            binding.searchMessage.setVisibility(View.VISIBLE);
        }
    }

    private void link(TrackMedia media) {
        if (busy) return;
        setBusy(true);
        Tracking.link(tracker, manga.id, media, uiCallback(getString(R.string.tracking_linked, tracker.name)));
    }

    private void refresh() {
        if (busy || tracked == null) return;
        setBusy(true);
        Tracking.refresh(manga.id, tracker.id, uiCallback(getString(R.string.tracking_synced, tracker.name)));
    }

    // once per opening: edits made on the tracker, and its score format, show up without tapping Sync
    private void pullQuietly() {
        pulled = true;
        setBusy(true);
        Tracking.refresh(manga.id, tracker.id, new Tracking.Callback<TrackedManga>() {
            @Override
            public void onComplete(TrackedManga result) {
                post(() -> {
                    setBusy(false);
                    // the score format may have changed without the entry changing
                    show(result);
                });
            }

            @Override
            public void onError(Exception e) {
                post(() -> {
                    setBusy(false);
                    if (e instanceof Tracker.NotLoggedInException) show(tracked);
                });
            }
        });
    }

    private void unlink() {
        if (tracked == null) return;
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.tracking_unlink_title, tracker.name))
                .setPositiveButton(R.string.tracking_unlink, (d, i) -> doUnlink(false))
                .setNegativeButton(R.string.cancel, null);
        if (tracker.supportsRemove() && tracker.isLoggedIn()) {
            dialog.setMessage(getString(R.string.tracking_unlink_message, tracker.name))
                    .setNeutralButton(R.string.tracking_remove, (d, i) -> doUnlink(true));
        } else {
            dialog.setMessage(getString(R.string.tracking_unlink_message_keep, tracker.name));
        }
        dialog.show();
    }

    private void doUnlink(boolean removeRemote) {
        if (busy) return;
        setBusy(true);
        searched = false;
        String done = getString(removeRemote ? R.string.tracking_removed : R.string.tracking_unlinked, tracker.name);
        Tracking.unlink(manga.id, tracker.id, removeRemote, uiCallback(done));
    }

    // endregion

    // region editing the entry

    private void pickStatus() {
        if (tracked == null) return;
        String[] statuses = tracker.statuses();
        String[] labels = new String[statuses.length];
        int checked = -1;
        for (int i = 0; i < statuses.length; i++) {
            labels[i] = statusLabel(requireContext(), statuses[i]);
            if (statuses[i].equals(tracked.status)) checked = i;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tracking_status)
                .setSingleChoiceItems(labels, checked, (dialog, i) -> {
                    Tracking.setStatus(manga.id, tracker.id, statuses[i]);
                    dialog.dismiss();
                })
                .show();
    }

    // a slider in the user's own format: stars, smileys or points
    private void pickScore() {
        if (tracked == null) return;
        ScoreFormat format = tracker.scoreFormat();
        Slider slider = new Slider(requireContext());
        slider.setValueFrom(0);
        slider.setValueTo(format.max);
        slider.setStepSize(format.step);
        slider.setValue(format.fromRaw(tracked.score));
        slider.setLabelFormatter(value -> value <= 0 ? getString(R.string.tracking_no_score) : format.valueLabel(value));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tracking_score)
                .setView(padded(slider))
                .setPositiveButton(R.string.tracking_save, (d, w) -> Tracking.setScore(manga.id, tracker.id, format.toRaw(slider.getValue())))
                .setNeutralButton(R.string.tracking_score_clear, (d, w) -> Tracking.setScore(manga.id, tracker.id, 0))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickDate(boolean started) {
        if (tracked == null) return;
        int current = started ? tracked.startedAt : tracked.completedAt;
        if (current == 0) {
            showDatePicker(started, TrackDates.today());
            return;
        }
        String[] choices = {getString(R.string.tracking_date_change), getString(R.string.tracking_date_remove)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(started ? R.string.tracking_started : R.string.tracking_finished)
                .setItems(choices, (d, i) -> {
                    if (i == 0) {
                        showDatePicker(started, current);
                    } else {
                        setDate(started, 0);
                    }
                })
                .show();
    }

    private void showDatePicker(boolean started, int date) {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(started ? R.string.tracking_started : R.string.tracking_finished)
                .setSelection(TrackDates.toUtcMillis(date))
                .build();
        picker.addOnPositiveButtonClickListener(selection -> setDate(started, TrackDates.fromUtcMillis(selection)));
        FragmentManager manager = getChildFragmentManager();
        picker.show(manager, "track-date");
    }

    private void setDate(boolean started, int date) {
        if (started) {
            Tracking.setStartedAt(manga.id, tracker.id, date);
        } else {
            Tracking.setCompletedAt(manga.id, tracker.id, date);
        }
    }

    private void editNotes() {
        if (tracked == null) return;
        TextInputEditText field = new TextInputEditText(requireContext());
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setMinLines(3);
        field.setText(tracked.notes);
        if (tracked.notes != null) field.setSelection(tracked.notes.length());

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tracking_notes)
                .setView(padded(field))
                .setPositiveButton(R.string.tracking_save, (d, w) ->
                        Tracking.setNotes(manga.id, tracker.id, field.getText() != null ? field.getText().toString() : null))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // dialogs don't pad a custom view
    private View padded(View view) {
        FrameLayout frame = new FrameLayout(requireContext());
        int side = Math.round(20 * getResources().getDisplayMetrics().density);
        frame.setPadding(side, side / 2, side, 0);
        frame.addView(view);
        return frame;
    }

    // endregion

    // results arrive on the network thread
    private <T> Tracking.Callback<T> uiCallback(String successMessage) {
        return new Tracking.Callback<T>() {
            @Override
            public void onComplete(T result) {
                post(() -> {
                    setBusy(false);
                    Snackbar.make(binding.getRoot(), successMessage, Snackbar.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(Exception e) {
                post(() -> {
                    setBusy(false);
                    if (e instanceof Tracker.NotLoggedInException) show(tracked);
                    Snackbar.make(binding.getRoot(), getString(R.string.tracking_failed, tracker.name, String.valueOf(e.getMessage())),
                            Snackbar.LENGTH_LONG).show();
                });
            }
        };
    }

    private void post(Runnable action) {
        View root = binding != null ? binding.getRoot() : null;
        if (root == null) return;
        root.post(() -> {
            if (binding != null) action.run();
        });
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        binding.progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        searchExecutor.shutdownNow();
    }

    private class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemTrackResultBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            TrackMedia media = results.get(position);
            Context context = holder.itemView.getContext();
            holder.binding.title.setText(media.title);

            List<String> parts = new ArrayList<>();
            parts.add(kindLabel(context, media));
            if (media.year > 0) parts.add(String.valueOf(media.year));
            String publishing = publishingLabel(context, media.publishing);
            if (publishing != null) parts.add(publishing);
            if (media.chapters > 0)
                parts.add(context.getString(R.string.tracking_chapters, media.chapters));
            if (media.meanScore > 0)
                parts.add(context.getString(R.string.tracking_mean_score, media.meanScore));
            if (media.entry != null) parts.add(statusLabel(context, media.entry.status));
            holder.binding.subtitle.setText(String.join(" · ", parts));

            Glide.with(holder.binding.cover).load(media.cover).centerCrop().into(holder.binding.cover);
            holder.itemView.setOnClickListener(v -> link(media));
        }

        @Override
        public int getItemCount() {
            return results.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ItemTrackResultBinding binding;

            Holder(ItemTrackResultBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
