package in.atulpatare.ranobem.ui.search;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import in.atulpatare.core.models.Manga;
import in.atulpatare.core.models.Metadata;
import in.atulpatare.core.sources.Source;
import in.atulpatare.core.sources.SourceManager;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.databinding.FragmentSearchBinding;
import in.atulpatare.ranobem.ui.browse.adapter.MangaAdapter;
import in.atulpatare.ranobem.ui.details.DetailsActivity;
import in.atulpatare.ranobem.ui.search.modal.FilterModal;
import in.atulpatare.ranobem.utils.DisplayUtils;
import in.atulpatare.ranobem.utils.EmptyState;
import in.atulpatare.ranobem.utils.SpacingDecorator;

public class SearchFragment extends Fragment implements MangaAdapter.OnMangaItemClickListener, FilterModal.UpdateFilterListener {
    private static final String ARG_SOURCE_ID = "source_id";
    private static final String ARG_AUTHOR = "author";
    private static final String ARG_GENRE = "genre";
    private final List<Manga> list = new ArrayList<>();
    private int SOURCE_ID = 2;
    private Source source;
    private SearchViewModel viewModel;
    private MangaAdapter adapter;
    private boolean isLoading = false;
    private String searchQuery = null;
    private String filterQuery = null;
    private String authorQuery = null;
    // set when opened pre-filtered, the first search then runs without the user asking
    private boolean searchOnOpen = false;
    private int page = 1;

    private FragmentSearchBinding binding;

    public static SearchFragment newInstance(Metadata meta) {
        SearchFragment fragment = new SearchFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(ARG_SOURCE_ID, meta.sourceId);
        fragment.setArguments(bundle);
        return fragment;
    }

    // opens with the results for an author and/or a genre already showing
    public static SearchFragment newInstance(Metadata meta, String author, String genre) {
        SearchFragment fragment = newInstance(meta);
        Bundle bundle = fragment.requireArguments();
        bundle.putString(ARG_AUTHOR, author);
        bundle.putString(ARG_GENRE, genre);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            SOURCE_ID = getArguments().getInt(ARG_SOURCE_ID);
            if (savedInstanceState == null) {
                authorQuery = getArguments().getString(ARG_AUTHOR);
                filterQuery = getArguments().getString(ARG_GENRE);
                searchOnOpen = authorQuery != null || filterQuery != null;
            }
        } else {
            SOURCE_ID = 2;
        }
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        source = SourceManager.getSource(SOURCE_ID);

        adapter = new MangaAdapter(list, this);
        DisplayUtils utils = new DisplayUtils(requireActivity());
        binding.novelList.setLayoutManager(new GridLayoutManager(requireActivity(), utils.noOfCols()));
        binding.novelList.addItemDecoration(new SpacingDecorator(utils.spacing()));
        binding.novelList.setAdapter(adapter);
        binding.novelList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (!recyclerView.canScrollVertically(1) && !isLoading) {
                    binding.progress.show();
                    isLoading = true;
                    page += 1;
                    viewModel.getMangas(SOURCE_ID, page, getQueries());
                }
            }
        });

        // search events
        binding.searchView.setEndIconOnClickListener(v -> handleSearch());
        binding.searchField.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                // the listener fires for both key down and key up, search only once
                if (event.getAction() == KeyEvent.ACTION_UP) handleSearch();
                return true;
            }
            return false;
        });
        binding.filter.setOnClickListener(v -> {
            List<String> selectedList = filterQuery != null
                    ? Arrays.asList(filterQuery.split(","))
                    : new ArrayList<>();
            FilterModal modal = new FilterModal(source.meta().genres, SearchFragment.this, selectedList);
            modal.show(getParentFragmentManager(), FilterModal.TAG);
        });

        // listening to errors
        viewModel.getError().observe(getViewLifecycleOwner(), this::setUpError);

        renderActiveFilters();
        if (searchOnOpen) {
            searchOnOpen = false;
            handleSearch();
        } else if (list.isEmpty()) {
            showHint();
        }

        return root;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void handleSearch() {
        if (binding.searchField.getText() != null) {
            searchQuery = binding.searchField.getText().toString().trim();
        }
        hideKeyboard();
        EmptyState.hide(binding.emptyState);
        list.clear();
        adapter.notifyDataSetChanged();
        isLoading = true;
        binding.progress.show();
        page = 1;
        viewModel.clearItems();
        viewModel.getMangas(SOURCE_ID, page, getQueries()).observe(getViewLifecycleOwner(), (mangas) -> {
            binding.progress.hide();
            isLoading = false;
            boolean fresh = list.isEmpty();
            list.clear();
            list.addAll(mangas);
            adapter.notifyDataSetChanged();
            // fade new results in, but not when a further page is appended
            if (fresh) binding.novelList.scheduleLayoutAnimation();
            if (list.isEmpty()) {
                EmptyState.show(binding.emptyState, R.drawable.ic_search, R.string.search_nothing_found, R.string.search_nothing_found_hint);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private HashMap<String, String> getQueries() {
        return new HashMap<>() {{
            put("search", searchQuery);
            put("filters", filterQuery);
            put("author", authorQuery);
        }};
    }

    private void showHint() {
        EmptyState.show(binding.emptyState, R.drawable.ic_search, R.string.search_hint_title, R.string.search_hint_message);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(binding.searchField.getWindowToken(), 0);
        binding.searchField.clearFocus();
    }

    private void setUpError(String error) {
        binding.progress.hide();
        // error on the first call
        if (list.isEmpty()) {
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onMangaItemClick(Manga item) {
        startActivity(new Intent(requireActivity(), DetailsActivity.class).putExtra(Config.KEY_MANGA, item));
    }

    @Override
    public void onUpdate(List<String> selectedFilters) {
        filterQuery = selectedFilters.isEmpty() ? null : String.join(",", selectedFilters);
        renderActiveFilters();
        handleSearch();
    }

    // one closable chip per filter in effect, closing it searches again without that filter
    private void renderActiveFilters() {
        ChipGroup group = binding.activeFilters;
        group.removeAllViews();

        if (authorQuery != null) {
            Chip chip = filterChip(decode(authorQuery));
            chip.setChipIconResource(R.drawable.ic_person);
            chip.setOnCloseIconClickListener(v -> {
                authorQuery = null;
                renderActiveFilters();
                handleSearch();
            });
            group.addView(chip);
        }

        if (filterQuery != null) {
            HashMap<String, String> genres = source.meta().genres;
            for (String key : filterQuery.split(",")) {
                String name = genres != null && genres.containsKey(key) ? genres.get(key) : decode(key);
                Chip chip = filterChip(name);
                chip.setOnCloseIconClickListener(v -> {
                    List<String> remaining = new ArrayList<>(Arrays.asList(filterQuery.split(",")));
                    remaining.remove(key);
                    filterQuery = remaining.isEmpty() ? null : String.join(",", remaining);
                    renderActiveFilters();
                    handleSearch();
                });
                group.addView(chip);
            }
        }

        binding.activeFiltersScroll.setVisibility(group.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private Chip filterChip(String text) {
        Chip chip = new Chip(requireContext());
        chip.setText(text);
        chip.setCloseIconVisible(true);
        chip.setCheckable(false);
        return chip;
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return value;
        }
    }
}