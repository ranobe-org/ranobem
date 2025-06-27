package in.atulpatare.ranobem.ui.browse;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.databinding.ActivityBrowseBinding;
import in.atulpatare.ranobem.ui.browse.adapter.MangaAdapter;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.ui.details.DetailsActivity;
import in.atulpatare.ranobem.utils.DisplayUtils;
import in.atulpatare.ranobem.utils.SpacingDecorator;

public class BrowseActivity extends AppCompatActivity implements MangaAdapter.OnMangaItemClickListener {

    private ActivityBrowseBinding binding;

    private BrowseViewModel viewModel;

    private MangaAdapter adapter;

    private final List<Manga> list = new ArrayList<>();

    private boolean isLoading = false;

    private String selectedSortOption = null;
    private String searchQuery = null;

    private int page = 1;

    private static final int SOURCE_ID = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityBrowseBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        viewModel = new ViewModelProvider(this).get(BrowseViewModel.class);

        adapter = new MangaAdapter(list, this);
        DisplayUtils utils = new DisplayUtils(this, R.layout.item_manga);
        binding.novelList.setLayoutManager(new GridLayoutManager(this, utils.noOfCols()));
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
                searchQuery = binding.searchField.getText().toString().trim();
                viewModel.clearItems();
                isLoading = true;
                binding.progress.show();
                page = 1;
                viewModel.getMangas(SOURCE_ID, page, getQueries()).observe(BrowseActivity.this, (mangas) -> {
                    binding.progress.hide();
                    isLoading = false;
                    list.clear();
                    list.addAll(mangas);
                    adapter.notifyDataSetChanged();
                });
            }
        });

        viewModel.getError().observe(this, this::setUpError);
        viewModel.getMangas(SOURCE_ID, page, getQueries()).observe(this, (mangas) -> {
            binding.progress.hide();
            isLoading = false;
            int old = list.size();
            list.clear();
            list.addAll(mangas);
            adapter.notifyItemRangeInserted(old, list.size());
        });

        viewModel.getSortOptions(SOURCE_ID).observe(this, this::setUpSortOptions);
    }

    private HashMap<String, String> getQueries() {
            return  new HashMap<>() {{
                put("sort", selectedSortOption);
                put("search", searchQuery);
            }};
    }

    private void setUpSortOptions(HashMap<String, String> stringStringHashMap) {
        List<String> options = new ArrayList<>(stringStringHashMap.keySet());
        binding.sortOptions.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, options));
        binding.sortOptions.setOnItemClickListener((parent, view, position, id) -> {
            selectedSortOption = stringStringHashMap.get(options.get(position));
            viewModel.clearItems();
            isLoading = true;
            binding.progress.show();
            page = 1;
            viewModel.getMangas(SOURCE_ID, page, getQueries()).observe(BrowseActivity.this, (mangas) -> {
                binding.progress.hide();
                isLoading = false;
                list.clear();
                list.addAll(mangas);
                adapter.notifyDataSetChanged();
            });
        });
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
        startActivity(new Intent(this, DetailsActivity.class).putExtra(Config.KEY_MANGA, item));
    }
}