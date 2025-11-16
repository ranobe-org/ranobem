package in.atulpatare.ranobem.ui.library;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import java.util.List;

import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.database.AppDatabase;
import in.atulpatare.ranobem.databinding.FragmentLibraryBinding;
import in.atulpatare.ranobem.ui.browse.adapter.MangaAdapter;
import in.atulpatare.ranobem.ui.details.DetailsActivity;
import in.atulpatare.ranobem.utils.DisplayUtils;
import in.atulpatare.ranobem.utils.SpacingDecorator;


public class LibraryFragment extends Fragment implements MangaAdapter.OnMangaItemClickListener {

    private final String SORTING_ORDER_ASC = "ASC";
    private final String SORTING_ORDER_DESC = "DESC";
    private FragmentLibraryBinding binding;
    private String sortingOrder = SORTING_ORDER_ASC;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        DisplayUtils utils = new DisplayUtils(requireActivity(), R.layout.item_manga);
        binding.novelList.setLayoutManager(new GridLayoutManager(requireActivity(), utils.noOfCols()));
        binding.novelList.addItemDecoration(new SpacingDecorator(utils.spacing()));
        binding.progress.hide();

        binding.appbar.setTitle("Library");
        binding.appbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.sort) {
                sortingOrder = sortingOrder.equals(SORTING_ORDER_DESC) ? SORTING_ORDER_ASC : SORTING_ORDER_DESC;
                loadLibrary();
            }
            return true;
        });

        loadLibrary();
        return root;
    }

    private void loadLibrary() {
        if (sortingOrder.equals(SORTING_ORDER_ASC)) {
            AppDatabase.getDatabase().mangaDao().getAll().observe(getViewLifecycleOwner(), this::setMangaItems);
        } else {
            AppDatabase.getDatabase().mangaDao().getAllSortedDesc().observe(getViewLifecycleOwner(), this::setMangaItems);
        }
    }

    private void setMangaItems(List<Manga> mangas) {
        if (mangas.isEmpty()) {
            binding.emptyLib.setVisibility(View.VISIBLE);
        } else {
            binding.novelList.setAdapter(new MangaAdapter(mangas, this));
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
}