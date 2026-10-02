package in.atulpatare.ranobem.ui.browse;

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
import java.util.List;

import in.atulpatare.core.models.Manga;
import in.atulpatare.core.models.Metadata;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.databinding.FragmentBrowseBinding;
import in.atulpatare.ranobem.ui.browse.adapter.MangaAdapter;
import in.atulpatare.ranobem.ui.details.DetailsActivity;
import in.atulpatare.ranobem.utils.DisplayUtils;
import in.atulpatare.ranobem.utils.EmptyState;
import in.atulpatare.ranobem.utils.SpacingDecorator;

public class BrowseFragment extends Fragment implements MangaAdapter.OnMangaItemClickListener {

    private static final String ARG_SOURCE_ID = "source_id";
    private final List<Manga> list = new ArrayList<>();
    private int SOURCE_ID;
    private BrowseViewModel viewModel;
    private MangaAdapter adapter;
    private boolean isLoading = false;
    private int page = 1;
    private FragmentBrowseBinding binding;

    public static BrowseFragment newInstance(Metadata meta) {
        BrowseFragment fragment = new BrowseFragment();
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
        binding = FragmentBrowseBinding.inflate(inflater);

        viewModel = new ViewModelProvider(this).get(BrowseViewModel.class);

        adapter = new MangaAdapter(list, this);
        DisplayUtils utils = new DisplayUtils(requireActivity());
        binding.novelList.setLayoutManager(new GridLayoutManager(requireActivity(), utils.noOfCols()));
        binding.novelList.addItemDecoration(new SpacingDecorator(utils.spacing()));
        binding.novelList.setAdapter(adapter);
        binding.novelList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (!recyclerView.canScrollVertically(1) && !isLoading && !list.isEmpty()) {
                    page += 1;
                    load();
                }
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), this::setUpError);
        viewModel.getItems().observe(getViewLifecycleOwner(), this::showItems);
        // the view model keeps loaded pages, so coming back to this tab doesn't fetch them again
        if (!viewModel.hasItems()) {
            page = 1;
            load();
        } else {
            binding.progress.hide();
        }
        return binding.getRoot();

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void load() {
        isLoading = true;
        binding.progress.show();
        EmptyState.hide(binding.emptyState);
        viewModel.load(SOURCE_ID, page);
    }

    private void showItems(List<Manga> mangas) {
        binding.progress.hide();
        isLoading = false;
        int old = list.size();
        boolean fresh = list.isEmpty();
        list.clear();
        list.addAll(mangas);
        if (list.size() > old) {
            adapter.notifyItemRangeInserted(old, list.size() - old);
        } else {
            adapter.notifyDataSetChanged();
        }
        if (fresh) binding.novelList.scheduleLayoutAnimation();
    }

    private void setUpError(String error) {
        if (binding == null || error == null) return;
        viewModel.consumeError();
        binding.progress.hide();
        isLoading = false;
        if (list.isEmpty()) {
            // nothing on screen yet, so offer a retry in its place
            EmptyState.show(binding.emptyState, R.drawable.ic_public, R.string.load_failed_title, R.string.load_failed_message,
                    R.string.retry, v -> load());
        } else {
            if (page > 1) page -= 1;
            Snackbar.make(binding.getRoot(), R.string.load_more_failed, Snackbar.LENGTH_LONG)
                    .setAction(R.string.retry, v -> {
                        page += 1;
                        load();
                    })
                    .show();
        }
    }

    @Override
    public void onMangaItemClick(Manga item) {
        startActivity(new Intent(requireActivity(), DetailsActivity.class).putExtra(Config.KEY_MANGA, item));
    }
}