package in.atulpatare.ranobem.ui.library;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

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
import in.atulpatare.ranobem.utils.SpacingDecorator;


public class LibraryFragment extends Fragment implements MangaAdapter.OnMangaItemClickListener {

    private final List<Manga> originalMangas = new ArrayList<>();
    private final String SORTING_ORDER_ASC = "ASC";
    private final String SORTING_ORDER_DESC = "DESC";
    private FragmentLibraryBinding binding;
    private String sortingOrder = SORTING_ORDER_ASC;

    private MangaAdapter adapter;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        DisplayUtils utils = new DisplayUtils(requireActivity(), R.layout.item_manga);
        binding.novelList.setLayoutManager(new GridLayoutManager(requireActivity(), utils.noOfCols()));
        binding.novelList.addItemDecoration(new SpacingDecorator(utils.spacing()));
        adapter = new MangaAdapter(originalMangas, this);
        binding.novelList.setAdapter(adapter);
        binding.progress.hide();

        // library items search
        binding.searchField.addTextChangedListener(new SearchBarTextWatcher());

        // setup app bar
        binding.appbar.setTitle("Library");
        binding.appbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.sort) {
                sortingOrder = sortingOrder.equals(SORTING_ORDER_DESC) ? SORTING_ORDER_ASC : SORTING_ORDER_DESC;
                String message = sortingOrder.equals(SORTING_ORDER_ASC) ? "Sorting with name in ascending order" : "Sorting with name in descending order";
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                loadLibrary();
            }
            if (item.getItemId() == R.id.search) {
                setSearchView();
            }
            return true;
        });

        loadLibrary();
        return root;
    }

    private void setSearchView() {
        int mode = binding.searchView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE;
        binding.searchView.setVisibility(mode);
    }

    private void loadLibrary() {
        if (sortingOrder.equals(SORTING_ORDER_ASC)) {
            AppDatabase.getDatabase().mangaDao().getAll().observe(getViewLifecycleOwner(), this::setMangaItems);
        } else {
            AppDatabase.getDatabase().mangaDao().getAllSortedDesc().observe(getViewLifecycleOwner(), this::setMangaItems);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setMangaItems(List<Manga> mangas) {
        originalMangas.clear();
        if (mangas.isEmpty()) {
            binding.emptyLib.setVisibility(View.VISIBLE);
        } else {
            originalMangas.addAll(mangas);
            adapter.notifyDataSetChanged();
        }
    }

    private void searchResults(String keyword) {
        if (!keyword.isEmpty()) {
            List<Manga> filtered = ListUtils.searchByNameManga(keyword.toLowerCase(), originalMangas);
            MangaAdapter local = new MangaAdapter(filtered, this);
            binding.novelList.setAdapter(local);
        } else {
            binding.novelList.setAdapter(adapter);
        }
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
            searchResults(charSequence.toString());
        }

        @Override
        public void afterTextChanged(Editable editable) {

        }
    }
}