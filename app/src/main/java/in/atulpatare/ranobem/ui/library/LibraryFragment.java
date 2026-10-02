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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import in.atulpatare.core.models.Manga;
import in.atulpatare.core.util.ListUtils;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.database.AppDatabase;
import in.atulpatare.ranobem.databinding.FragmentLibraryBinding;
import in.atulpatare.ranobem.ui.browse.adapter.MangaAdapter;
import in.atulpatare.ranobem.ui.details.DetailsActivity;
import in.atulpatare.ranobem.utils.DisplayUtils;
import in.atulpatare.ranobem.utils.EmptyState;
import in.atulpatare.ranobem.utils.SpacingDecorator;


public class LibraryFragment extends Fragment implements MangaAdapter.OnMangaItemClickListener {

    private final List<Manga> allMangas = new ArrayList<>();
    private final List<Manga> shownMangas = new ArrayList<>();
    private FragmentLibraryBinding binding;
    private boolean ascending = true;
    private boolean firstLoad = true;
    private String keyword = "";

    private MangaAdapter adapter;
    private LiveData<List<Manga>> library;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        DisplayUtils utils = new DisplayUtils(requireActivity());
        binding.novelList.setLayoutManager(new GridLayoutManager(requireActivity(), utils.noOfCols()));
        binding.novelList.addItemDecoration(new SpacingDecorator(utils.spacing()));
        adapter = new MangaAdapter(shownMangas, this);
        adapter.showSearchName(true);
        binding.novelList.setAdapter(adapter);
        binding.progress.hide();

        // library items search
        binding.searchField.addTextChangedListener(new SearchBarTextWatcher());

        // setup app bar
        binding.appbar.setTitle(R.string.title_library);
        binding.appbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.sort) {
                ascending = !ascending;
                Snackbar.make(binding.getRoot(), ascending ? R.string.sorted_a_z : R.string.sorted_z_a, Snackbar.LENGTH_SHORT).show();
                loadLibrary();
            }
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

        firstLoad = true;
        library = null;
        loadLibrary();
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

    private void loadLibrary() {
        // only one sort order is observed at a time, otherwise both keep overwriting each other
        if (library != null) library.removeObservers(getViewLifecycleOwner());
        library = ascending
                ? AppDatabase.getDatabase().mangaDao().getAll()
                : AppDatabase.getDatabase().mangaDao().getAllSortedDesc();
        library.observe(getViewLifecycleOwner(), this::setMangaItems);
    }

    private void setMangaItems(List<Manga> mangas) {
        allMangas.clear();
        allMangas.addAll(mangas);
        showFiltered();
        if (firstLoad && !shownMangas.isEmpty()) {
            firstLoad = false;
            binding.novelList.scheduleLayoutAnimation();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void showFiltered() {
        shownMangas.clear();
        shownMangas.addAll(keyword.isEmpty() ? allMangas : ListUtils.searchByNameManga(keyword.toLowerCase(), allMangas));
        adapter.notifyDataSetChanged();

        if (allMangas.isEmpty()) {
            EmptyState.show(binding.emptyState, R.drawable.ic_bookmark_border, R.string.library_empty_title,
                    R.string.library_empty_message, R.string.start_browsing, v -> openBrowse());
        } else if (shownMangas.isEmpty()) {
            EmptyState.show(binding.emptyState, R.drawable.ic_search, R.string.library_no_matches, R.string.library_no_matches_message);
        } else {
            EmptyState.hide(binding.emptyState);
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

    public class SearchBarTextWatcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            keyword = charSequence.toString().trim();
            showFiltered();
        }

        @Override
        public void afterTextChanged(Editable editable) {

        }
    }
}
