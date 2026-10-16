package in.atulpatare.ranobem.ui.library;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.AppSettings;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.database.AppDatabase;
import in.atulpatare.ranobem.databinding.FragmentLibraryBinding;
import in.atulpatare.ranobem.library.LibrarySort;
import in.atulpatare.ranobem.tracking.TrackStatus;
import in.atulpatare.ranobem.ui.browse.adapter.MangaAdapter;
import in.atulpatare.ranobem.ui.details.DetailsActivity;
import in.atulpatare.ranobem.ui.tracking.TrackSheet;
import in.atulpatare.ranobem.utils.DisplayUtils;
import in.atulpatare.ranobem.utils.EmptyState;
import in.atulpatare.ranobem.utils.SpacingDecorator;

/**
 * The library: series on shelves by reading status, with unread counts, a few sort orders and an
 * unread filter. Shelves only show once something is on them, so a new library stays simple.
 */
public class LibraryFragment extends Fragment implements MangaAdapter.OnMangaItemClickListener,
        MangaAdapter.OnMangaItemLongClickListener {

    private final List<Manga> allMangas = new ArrayList<>();
    private final List<Manga> shownMangas = new ArrayList<>();
    // the tabs as shown, null standing for All
    private final List<String> shownShelves = new ArrayList<>();
    private FragmentLibraryBinding binding;
    private boolean firstLoad = true;
    private boolean updatingTabs = false;
    private String keyword = "";
    @Nullable
    private String shelf;
    private int sort;
    private boolean unreadOnly;

    private MangaAdapter adapter;

    private static int sortLabel(int sort) {
        switch (sort) {
            case LibrarySort.UPDATED:
                return R.string.library_sort_updated;
            case LibrarySort.UNREAD:
                return R.string.library_sort_unread;
            case LibrarySort.ADDED:
                return R.string.library_sort_added;
            case LibrarySort.TITLE:
                return R.string.library_sort_title;
            case LibrarySort.LAST_READ:
            default:
                return R.string.library_sort_last_read;
        }
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        Context context = requireContext();
        shelf = AppSettings.libraryShelf(context);
        sort = AppSettings.librarySort(context, LibrarySort.LAST_READ);
        unreadOnly = AppSettings.libraryUnreadOnly(context);

        DisplayUtils utils = new DisplayUtils(requireActivity());
        binding.novelList.setLayoutManager(new GridLayoutManager(requireActivity(), utils.noOfCols()));
        binding.novelList.addItemDecoration(new SpacingDecorator(utils.spacing()));
        adapter = new MangaAdapter(shownMangas, this);
        adapter.showSearchName(true);
        adapter.setLibraryMode(this);
        binding.novelList.setAdapter(adapter);
        binding.progress.hide();

        // library items search
        binding.searchField.addTextChangedListener(new SearchBarTextWatcher());

        // setup app bar
        binding.appbar.setTitle(R.string.title_library);
        binding.appbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.search) {
                toggleSearch();
            }
            if (item.getItemId() == R.id.chapter_updates) {
                // the setting lives in the settings tab
                BottomNavigationView nav = requireActivity().findViewById(R.id.nav_view);
                if (nav != null) nav.setSelectedItemId(R.id.navigation_settings);
            }
            return true;
        });

        binding.shelves.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (updatingTabs) return;
                shelf = (String) tab.getTag();
                AppSettings.setLibraryShelf(requireContext(), shelf);
                showFiltered(true);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                binding.novelList.smoothScrollToPosition(0);
            }
        });

        showSortChip();
        binding.sortChip.setOnClickListener(v -> pickSort());
        binding.unreadChip.setChecked(unreadOnly);
        binding.unreadChip.setOnCheckedChangeListener((chip, checked) -> {
            unreadOnly = checked;
            AppSettings.setLibraryUnreadOnly(requireContext(), checked);
            showFiltered(true);
        });

        firstLoad = true;
        AppDatabase.getDatabase().mangaDao().observeAll().observe(getViewLifecycleOwner(), this::setMangaItems);
        return root;
    }

    private void toggleSearch() {
        boolean show = binding.searchView.getVisibility() != View.VISIBLE;
        TransitionManager.beginDelayedTransition(binding.main);
        binding.searchView.setVisibility(show ? View.VISIBLE : View.GONE);
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (show) {
            binding.searchField.requestFocus();
            if (imm != null) imm.showSoftInput(binding.searchField, InputMethodManager.SHOW_IMPLICIT);
        } else {
            // closing the search shows the whole library again
            binding.searchField.setText("");
            if (imm != null) imm.hideSoftInputFromWindow(binding.searchField.getWindowToken(), 0);
        }
    }

    private void setMangaItems(List<Manga> mangas) {
        allMangas.clear();
        allMangas.addAll(mangas);
        showShelves();
        showFiltered(false);
        if (firstLoad && !shownMangas.isEmpty()) {
            firstLoad = false;
            binding.novelList.scheduleLayoutAnimation();
        }
    }

    // region shelves

    // All, then every status that has something on it, plus the open shelf even when it emptied
    private void showShelves() {
        Map<String, Integer> counts = new HashMap<>();
        for (Manga manga : allMangas) {
            if (manga.readStatus != null) counts.merge(manga.readStatus, 1, Integer::sum);
        }
        List<String> shelves = new ArrayList<>();
        shelves.add(null);
        for (String status : TrackStatus.ALL) {
            if (counts.containsKey(status) || status.equals(shelf)) shelves.add(status);
        }
        // nothing to tell apart in an empty library
        binding.shelves.setVisibility(allMangas.isEmpty() ? View.GONE : View.VISIBLE);
        binding.libraryFilters.setVisibility(allMangas.isEmpty() ? View.GONE : View.VISIBLE);

        updatingTabs = true;
        if (!shelves.equals(shownShelves)) {
            shownShelves.clear();
            shownShelves.addAll(shelves);
            binding.shelves.removeAllTabs();
            for (String status : shelves) binding.shelves.addTab(binding.shelves.newTab().setTag(status), false);
        }
        for (int i = 0; i < shelves.size(); i++) {
            String status = shelves.get(i);
            int count = status == null ? allMangas.size() : counts.getOrDefault(status, 0);
            String name = status == null ? getString(R.string.library_tab_all) : TrackSheet.statusLabel(requireContext(), status);
            TabLayout.Tab tab = Objects.requireNonNull(binding.shelves.getTabAt(i));
            tab.setText(getString(R.string.library_tab_label, name, count));
            tab.setContentDescription(getResources().getQuantityString(R.plurals.library_tab_description, count, name, count));
        }
        int selected = Math.max(0, shelves.indexOf(shelf));
        TabLayout.Tab tab = binding.shelves.getTabAt(selected);
        if (tab != null && !tab.isSelected()) tab.select();
        updatingTabs = false;
    }

    // endregion

    // region sort

    private void showSortChip() {
        String label = getString(sortLabel(sort));
        binding.sortChip.setText(getString(R.string.library_sort_chip, label));
        binding.sortChip.setContentDescription(getString(R.string.library_sort_chip_description, label));
    }

    private void pickSort() {
        String[] labels = new String[LibrarySort.ALL.length];
        int checked = 0;
        for (int i = 0; i < LibrarySort.ALL.length; i++) {
            labels[i] = getString(sortLabel(LibrarySort.ALL[i]));
            if (LibrarySort.ALL[i] == sort) checked = i;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.library_sort_by)
                .setSingleChoiceItems(labels, checked, (dialog, i) -> {
                    dialog.dismiss();
                    sort = LibrarySort.ALL[i];
                    AppSettings.setLibrarySort(requireContext(), sort);
                    showSortChip();
                    showFiltered(true);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // endregion

    @SuppressLint("NotifyDataSetChanged")
    private void showFiltered(boolean toTop) {
        shownMangas.clear();
        for (Manga manga : allMangas) {
            if (LibrarySort.matches(manga, shelf, unreadOnly, keyword)) shownMangas.add(manga);
        }
        Collections.sort(shownMangas, LibrarySort.comparator(sort));
        adapter.notifyDataSetChanged();
        if (toTop) binding.novelList.scrollToPosition(0);

        if (allMangas.isEmpty()) {
            EmptyState.show(binding.emptyState, R.drawable.ic_bookmark_border, R.string.library_empty_title,
                    R.string.library_empty_message, R.string.start_browsing, v -> openBrowse());
        } else if (!shownMangas.isEmpty()) {
            EmptyState.hide(binding.emptyState);
        } else if (!keyword.isEmpty()) {
            EmptyState.show(binding.emptyState, R.drawable.ic_search, R.string.library_no_matches, R.string.library_no_matches_message);
        } else if (unreadOnly) {
            EmptyState.show(binding.emptyState, R.drawable.ic_check_circle, R.string.library_caught_up_title, R.string.library_caught_up_message);
        } else {
            EmptyState.show(binding.emptyState, R.drawable.ic_library, R.string.library_shelf_empty_title, R.string.library_shelf_empty_title);
            binding.emptyState.emptyMessage.setText(getString(R.string.library_shelf_empty_message,
                    TrackSheet.statusLabel(requireContext(), shelf)));
        }
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
    public void onMangaItemClick(Manga item) {
        startActivity(new Intent(requireActivity(), DetailsActivity.class).putExtra(Config.KEY_MANGA, item));
    }

    @Override
    public void onMangaItemLongClick(Manga item) {
        LibraryStatusDialog.show(requireContext(), binding.getRoot(), item, item.readStatus, () -> remove(item));
    }

    // with an undo that puts it back on the same shelf, unread count and all
    private void remove(Manga item) {
        AppDatabase.databaseExecutor.execute(() -> AppDatabase.getDatabase().mangaDao().delete(item));
        Snackbar.make(binding.getRoot(), R.string.removed_from_library, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> AppDatabase.databaseExecutor.execute(() ->
                        AppDatabase.getDatabase().mangaDao().insert(item)))
                .show();
    }

    public class SearchBarTextWatcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            keyword = charSequence.toString().trim();
            showFiltered(false);
        }

        @Override
        public void afterTextChanged(Editable editable) {

        }
    }
}
