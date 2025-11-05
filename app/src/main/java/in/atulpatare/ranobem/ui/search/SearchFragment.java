package in.atulpatare.ranobem.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import in.atulpatare.core.models.Manga;
import in.atulpatare.core.models.Metadata;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.databinding.FragmentSearchBinding;
import in.atulpatare.ranobem.ui.browse.BrowseViewModel;
import in.atulpatare.ranobem.ui.browse.adapter.MangaAdapter;
import in.atulpatare.ranobem.ui.details.DetailsActivity;
import in.atulpatare.ranobem.utils.DisplayUtils;
import in.atulpatare.ranobem.utils.SpacingDecorator;

public class SearchFragment extends Fragment implements MangaAdapter.OnMangaItemClickListener {
    private static final String ARG_SOURCE_ID = "source_id";
    private static  int SOURCE_ID = 2;
    private final List<Manga> list = new ArrayList<>();
    private BrowseViewModel viewModel;
    private MangaAdapter adapter;
    private boolean isLoading = false;
    private String selectedSortOption = null;
    private String searchQuery = null;
    private int page = 1;

    private FragmentSearchBinding binding;

    public static SearchFragment newInstance(Metadata meta) {
        SearchFragment fragment = new SearchFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(ARG_SOURCE_ID, meta.sourceId);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            SOURCE_ID = getArguments().getInt(ARG_SOURCE_ID);
        } else {
            SOURCE_ID = 1;
        }
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        viewModel = new ViewModelProvider(this).get(BrowseViewModel.class);

        adapter = new MangaAdapter(list, this);
        DisplayUtils utils = new DisplayUtils(requireActivity(), R.layout.item_manga);
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

        binding.searchView.setEndIconOnClickListener(v -> {
            if (binding.searchField.getText() != null) {
                binding.progress.show();
                searchQuery = binding.searchField.getText().toString().trim();
                viewModel.clearItems();
                isLoading = true;
                binding.progress.show();
                page = 1;
                viewModel.getMangas(SOURCE_ID, page, getQueries()).observe(getViewLifecycleOwner(), (mangas) -> {
                    binding.progress.hide();
                    isLoading = false;
                    list.clear();
                    list.addAll(mangas);
                    adapter.notifyDataSetChanged();
                });
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), this::setUpError);

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private HashMap<String, String> getQueries() {
        return new HashMap<>() {{
            put("sort", selectedSortOption);
            put("search", searchQuery);
        }};
    }

    private void setUpError(String error) {
        binding.progress.hide();
        // error on the first call
        if (list.isEmpty()) {
            Snackbar.make(binding.getRoot(), error, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onMangaItemClick(Manga item) {
        startActivity(new Intent(requireActivity(), DetailsActivity.class).putExtra(Config.KEY_MANGA, item));
    }
}