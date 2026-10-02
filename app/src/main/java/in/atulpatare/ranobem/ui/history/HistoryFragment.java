package in.atulpatare.ranobem.ui.history;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.database.AppDatabase;
import in.atulpatare.ranobem.databinding.FragmentHistoryBinding;
import in.atulpatare.ranobem.model.History;
import in.atulpatare.ranobem.ui.history.adapter.HistoryAdapter;
import in.atulpatare.ranobem.ui.reader.ReaderActivity;
import in.atulpatare.ranobem.utils.SourceAccess;
import in.atulpatare.ranobem.utils.EmptyState;

public class HistoryFragment extends Fragment implements HistoryAdapter.OnHistoryItemClickListener {

    private FragmentHistoryBinding binding;
    private HistoryAdapter adapter;
    private boolean firstLoad = true;

    public HistoryFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        adapter = new HistoryAdapter(this);
        binding.mangaList.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        binding.mangaList.setAdapter(adapter);

        binding.appbar.setTitle(R.string.reading_history);
        binding.clearAll.setOnClickListener(v -> confirmClearAll());

        firstLoad = true;
        AppDatabase.getDatabase().historyDao().getAll().observe(getViewLifecycleOwner(), this::setHistories);
        return binding.getRoot();
    }

    private void setHistories(List<History> histories) {
        binding.progress.hide();
        adapter.submit(histories);
        if (firstLoad && !histories.isEmpty()) {
            firstLoad = false;
            binding.mangaList.scheduleLayoutAnimation();
        }

        // nothing to clear when there's no history
        binding.clearAll.setVisibility(histories.isEmpty() ? View.GONE : View.VISIBLE);
        if (histories.isEmpty()) {
            EmptyState.show(binding.emptyState, R.drawable.ic_history, R.string.history_empty_title,
                    R.string.history_empty_message, R.string.start_browsing, v -> openBrowse());
        } else {
            EmptyState.hide(binding.emptyState);
        }
    }

    private void confirmClearAll() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clear_history_title)
                .setMessage(R.string.clear_history_message)
                .setPositiveButton(R.string.clear_history_confirm, (dialog, which) ->
                        AppDatabase.databaseExecutor.execute(() -> AppDatabase.getDatabase().historyDao().deleteAll()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openBrowse() {
        BottomNavigationView nav = requireActivity().findViewById(R.id.nav_view);
        if (nav != null) nav.setSelectedItemId(R.id.navigation_browse);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onHistoryItemClick(History history) {
        if (!SourceAccess.available(history.sourceId)) {
            SourceAccess.showUnavailable(requireContext(), history.sourceId, null);
            return;
        }
        Manga manga = history.getManga();
        Chapter chapter = history.getChapter();
        Bundle bundle = new Bundle();
        bundle.putParcelable(Config.KEY_CHAPTER, chapter);
        bundle.putParcelable(Config.KEY_MANGA, manga);
        bundle.putString(Config.KEY_PAGE, Config.PAGE_HISTORY);
        requireActivity().startActivity(new Intent(requireActivity(), ReaderActivity.class).putExtras(bundle));
    }

    @Override
    public void onHistoryItemDeleteClick(History history) {
        if (history == null) return;
        AppDatabase.databaseExecutor.execute(() -> AppDatabase.getDatabase().historyDao().deleteById(history.id));
        Snackbar.make(binding.getRoot(), R.string.history_entry_removed, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> AppDatabase.databaseExecutor.execute(() ->
                        AppDatabase.getDatabase().historyDao().insert(history)))
                .show();
    }
}
